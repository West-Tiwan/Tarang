package com.westtiwan.tarang.eq

import com.westtiwan.tarang.data.JsonStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutoEqEntry(val name: String, val path: String, val source: String)

/** Describes the current audio output so the EQ can follow headphones and DACs. */
data class OutputDevice(
    val key: String,
    val name: String,
    val kind: Kind,
) {
    enum class Kind { SPEAKER, WIRED, BLUETOOTH, USB, HDMI, OTHER }
    val isHeadphone get() = kind == Kind.WIRED || kind == Kind.BLUETOOTH || kind == Kind.USB
}

class EqManager(
    private val store: JsonStore<EqState>,
    private val http: HttpClient,
    private val now: () -> Long,
) {
    val state: StateFlow<EqState> get() = store.state
    private val _message = MutableStateFlow<String?>(null)
    /** One shot user visible notices, for example when an AutoEq profile was applied. */
    val message: StateFlow<String?> = _message
    private var autoEqIndex: List<AutoEqEntry>? = null
    private val indexLock = Mutex()
    private var currentDevice: OutputDevice? = null

    val allPresets: List<EqPreset> get() = EqPresets.builtins + store.value.presets

    fun consumeMessage() { _message.value = null }

    private fun touch(transform: (EqState) -> EqState) = store.update { transform(it).copy(updatedAt = now()) }

    fun setEnabled(on: Boolean) = touch { it.copy(enabled = on) }

    fun select(preset: EqPreset) = touch { s ->
        val profiles = currentDevice?.let { d -> s.deviceProfiles + (d.key to preset.id) } ?: s.deviceProfiles
        s.copy(current = preset, enabled = true, deviceProfiles = if (s.autoSwitch) profiles else s.deviceProfiles)
    }

    /** Live edit of the current curve (dragging a band). */
    fun edit(preset: EqPreset) = store.update { it.copy(current = preset) }

    fun saveCurrentAs(name: String): EqPreset {
        val p = store.value.current.copy(id = "user-" + now().toString(36), name = name, source = "user")
        touch { it.copy(presets = it.presets + p, current = p) }
        return p
    }

    fun overwrite(preset: EqPreset) = touch { s -> s.copy(presets = s.presets.map { if (it.id == preset.id) preset else it }, current = preset) }

    fun delete(id: String) = touch { s ->
        s.copy(presets = s.presets.filterNot { it.id == id }, deviceProfiles = s.deviceProfiles.filterValues { it != id })
    }

    fun import(preset: EqPreset) = touch { s -> s.copy(presets = s.presets.filterNot { it.id == preset.id } + preset) }

    fun setAutoSwitch(on: Boolean) = touch { it.copy(autoSwitch = on) }
    fun setAutoEqLookup(on: Boolean) = touch { it.copy(autoEqLookup = on) }
    fun forgetDevice(key: String) = touch { it.copy(deviceProfiles = it.deviceProfiles - key) }

    val device: OutputDevice? get() = currentDevice

    /** Called by the player whenever the route changes. */
    suspend fun onOutputChanged(device: OutputDevice) {
        currentDevice = device
        val s = store.value
        if (!s.autoSwitch) return
        val presetId = s.deviceProfiles[device.key]
        if (presetId != null) {
            allPresets.firstOrNull { it.id == presetId }?.let { p ->
                if (p.id != s.current.id) {
                    store.update { it.copy(current = p, enabled = true) }
                    _message.value = "EQ: ${p.name} for ${device.name}"
                }
            }
            return
        }
        if (s.autoEqLookup && device.isHeadphone && device.kind != OutputDevice.Kind.USB) {
            val match = runCatching { findAutoEq(device.name) }.getOrNull() ?: return
            val preset = runCatching { fetchAutoEq(match) }.getOrNull() ?: return
            touch { st ->
                st.copy(
                    presets = st.presets.filterNot { it.id == preset.id } + preset,
                    deviceProfiles = st.deviceProfiles + (device.key to preset.id),
                    current = preset,
                    enabled = true,
                )
            }
            _message.value = "Recognised ${device.name}: applied AutoEq ${match.name}"
        }
    }

    suspend fun autoEqIndex(): List<AutoEqEntry> = indexLock.withLock {
        autoEqIndex?.let { return it }
        val res = http.get("$AUTOEQ_RAW/INDEX.md")
        if (!res.status.isSuccess()) return emptyList()
        val re = Regex("""^- \[(.*)]\(\./(.*)\) by (.*)$""")
        val list = res.bodyAsText().lineSequence().mapNotNull { line ->
            re.find(line.trim())?.let { AutoEqEntry(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        }.toList()
        autoEqIndex = list
        list
    }

    suspend fun searchAutoEq(query: String): List<AutoEqEntry> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val tokens = q.split(" ")
        return autoEqIndex().filter { e -> val n = normalize(e.name); tokens.all { n.contains(it) } }.take(50)
    }

    /** Fuzzy match a Bluetooth or wired device name against the AutoEq database. */
    suspend fun findAutoEq(deviceName: String): AutoEqEntry? {
        val target = normalize(deviceName)
        if (target.length < 3) return null
        val idx = autoEqIndex()
        val targetTokens = target.split(" ").toSet()
        val scored = idx.map { e ->
            val n = normalize(e.name.substringBefore(" (").ifBlank { e.name })
            val tokens = n.split(" ").toSet()
            val common = tokens.intersect(targetTokens).size
            val score = when {
                n == target -> 100.0
                target.contains(n) && n.length > 4 -> 80.0 + n.length / 10.0
                else -> common * 20.0 - (tokens.size - common) * 5.0
            }
            e to score
        }.filter { it.second >= 40 }
        // Prefer oratory1990 and crinacle measurements when names tie.
        return scored.maxWithOrNull(compareBy<Pair<AutoEqEntry, Double>> { it.second }.thenBy {
            when (it.first.source.substringBefore(" ")) { "oratory1990" -> 2; "crinacle" -> 1; else -> 0 }
        })?.first
    }

    suspend fun fetchAutoEq(entry: AutoEqEntry): EqPreset? {
        val fileName = entry.path.substringAfterLast('/') + "%20ParametricEQ.txt"
        val res = http.get("$AUTOEQ_RAW/${entry.path}/$fileName")
        if (!res.status.isSuccess()) return null
        return AutoEq.parse("autoeq-" + normalize(entry.name).replace(' ', '-'), entry.name, res.bodyAsText())
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex(" +"), " ")

    companion object {
        const val AUTOEQ_RAW = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    }
}
