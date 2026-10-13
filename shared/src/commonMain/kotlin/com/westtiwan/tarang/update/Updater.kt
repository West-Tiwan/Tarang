package com.westtiwan.tarang.update

import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.joinPath
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ReleaseAsset(val id: Long, val name: String, val size: Long = 0, val url: String = "", val browser_download_url: String = "")

@Serializable
data class Release(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    val version: String get() = tag_name.removePrefix("v")
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val asset: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val path: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Installs a downloaded update. Android uses PackageInstaller, macOS swaps the app bundle. */
interface UpdateInstaller {
    /** File extension of the release asset for this platform, for example ".apk". */
    val assetSuffix: String
    /** True when installing quits and relaunches the app (desktop). */
    val restartsApp: Boolean get() = false
    fun install(path: String)
}

/** Checks GitHub releases, downloads the matching asset and hands it to the platform installer. */
class Updater(
    private val http: HttpClient,
    val currentVersion: String,
    private val dir: String,
    private val installer: UpdateInstaller?,
    private val repo: () -> String,
    private val token: () -> String,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    val supported: Boolean get() = installer != null
    val restartsApp: Boolean get() = installer?.restartsApp == true

    init { Files.mkdirs(dir) }

    private suspend fun api(path: String): String? {
        val res = http.get("https://api.github.com/repos/${repo()}$path") {
            header("Accept", "application/vnd.github+json")
            token().takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }
        if (res.status.value == 404) error(if (token().isBlank()) "Release not found. The repo is private, so add a GitHub token in Settings." else "Release not found")
        if (!res.status.isSuccess()) error("GitHub returned ${res.status.value}")
        return res.bodyAsText()
    }

    suspend fun check(): UpdateState {
        val inst = installer ?: return UpdateState.Idle
        _state.value = UpdateState.Checking
        val result = runCatching {
            val releases = json.decodeFromString(ListSerializer(Release.serializer()), api("/releases?per_page=10") ?: "[]")
            val latest = releases.filter { !it.draft && !it.prerelease }.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            val asset = latest?.assets?.firstOrNull { it.name.endsWith(inst.assetSuffix, ignoreCase = true) }
            if (latest == null || asset == null || compareVersions(latest.version, currentVersion) <= 0) UpdateState.UpToDate
            else UpdateState.Available(latest, asset)
        }.getOrElse { UpdateState.Failed(it.message ?: "Update check failed") }
        _state.value = result
        return result
    }

    suspend fun notesFor(version: String): Release? = runCatching {
        json.decodeFromString(Release.serializer(), api("/releases/tags/v$version") ?: return null)
    }.getOrNull()

    suspend fun download(release: Release, asset: ReleaseAsset) {
        _state.value = UpdateState.Downloading(release, 0f)
        val target = joinPath(dir, asset.name)
        val tmp = "$target.part"
        try {
            // The API asset URL works for private repos when a token is set, then redirects to storage.
            http.prepareGet(asset.url.ifBlank { asset.browser_download_url }) {
                header("Accept", "application/octet-stream")
                token().takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }.execute { res ->
                if (!res.status.isSuccess()) error("Download failed (${res.status.value})")
                val total = res.contentLength() ?: asset.size
                val ch = res.bodyAsChannel()
                val sink = Files.openWriter(tmp)
                val buf = ByteArray(64 * 1024)
                var done = 0L
                try {
                    while (true) {
                        val n = ch.readAvailable(buf, 0, buf.size)
                        if (n == -1) break
                        if (n > 0) {
                            sink.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                } finally { sink.close() }
            }
            Files.rename(tmp, target)
            _state.value = UpdateState.Ready(release, target)
        } catch (e: Throwable) {
            Files.delete(tmp)
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = UpdateState.Failed(e.message ?: "Download failed")
        }
    }

    fun install(path: String) {
        runCatching { installer?.install(path) }.onFailure { _state.value = UpdateState.Failed(it.message ?: "Install failed") }
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    companion object {
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
