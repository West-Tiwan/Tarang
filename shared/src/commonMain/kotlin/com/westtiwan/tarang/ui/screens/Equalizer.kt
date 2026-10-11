package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.eq.AutoEq
import com.westtiwan.tarang.eq.AutoEqEntry
import com.westtiwan.tarang.eq.EqBand
import com.westtiwan.tarang.eq.EqEngine
import com.westtiwan.tarang.eq.EqPreset
import com.westtiwan.tarang.eq.EqPresets
import com.westtiwan.tarang.eq.FilterType
import com.westtiwan.tarang.platform.nowMillis
import com.westtiwan.tarang.ui.LocalApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private fun fmtHz(f: Double) = if (f >= 1000) "${(f / 100).roundToInt() / 10.0}k" else "${f.roundToInt()}"
private fun fmtDb(d: Double) = (if (d > 0) "+" else "") + ((d * 10).roundToInt() / 10.0).toString()

@Composable
fun EqualizerScreen() {
    val app = LocalApp.current
    val eq by app.eqStore.state.collectAsState()
    val output by app.player.currentOutput.collectAsState()
    val player by app.player.state.collectAsState()
    var parametric by remember(eq.current.id) { mutableStateOf(!EqPresets.isGraphic(eq.current)) }
    var saveAs by remember { mutableStateOf(false) }
    var autoEq by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf(false) }
    val preset = eq.current
    val isUser = eq.presets.any { it.id == preset.id }

    Column(Modifier.fillMaxSize()) {
        BackBar("Equalizer") {
            Switch(eq.enabled, { app.eq.setEnabled(it) }, Modifier.padding(end = 12.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (player.audio.bitPerfect && eq.enabled) item {
                Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "Bit-perfect USB output is active, so the EQ is bypassed to keep the signal untouched. Turn off bit-perfect in Settings to use the EQ with this DAC.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (preset.source) { "autoeq" -> "AutoEq profile"; "builtin" -> "Built-in"; else -> "Custom" } + " • Preamp ${fmtDb(preset.preampDb)} dB",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ResponseCurve(preset, eq.enabled)
                    }
                }
            }
            item {
                output?.let { out ->
                    val assigned = eq.deviceProfiles[out.key]?.let { pid -> app.eq.allPresets.firstOrNull { it.id == pid }?.name }
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Headphones, null) },
                        headlineContent = { Text(out.name) },
                        supportingContent = { Text(if (assigned != null) "Uses $assigned automatically" else "No profile assigned") },
                        trailingContent = {
                            if (assigned != null) TextButton({ app.eq.forgetDevice(out.key) }) { Text("Forget") }
                            else TextButton({ app.eq.select(preset) }) { Text("Use here") }
                        },
                    )
                }
            }
            item {
                Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(app.eq.allPresets, key = { it.id }) { p ->
                        FilterChip(p.id == preset.id, { app.eq.select(p) }, label = { Text(p.name) })
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ saveAs = true }) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(4.dp)); Text("Save as") }
                    if (isUser) TextButton({ app.eq.overwrite(preset); app.toast("Saved ${preset.name}") }) { Text("Save") }
                    if (isUser) TextButton({ app.eq.delete(preset.id); app.eq.select(EqPresets.flat) }) { Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete") }
                    TextButton({ autoEq = true }) { Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(4.dp)); Text("AutoEq") }
                    TextButton({ paste = true }) { Icon(Icons.Rounded.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("Import") }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                    SegmentedButton(!parametric, {
                        parametric = false
                        if (!EqPresets.isGraphic(preset)) app.eq.edit(toGraphic(preset))
                    }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Graphic") }
                    SegmentedButton(parametric, { parametric = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Parametric") }
                }
            }
            item {
                BandSlider("Preamp", preset.preampDb, -24f..12f) { app.eq.edit(preset.copy(preampDb = it, id = editId(preset), name = editName(preset), source = "user")) }
            }
            if (!parametric) {
                itemsIndexed(preset.bands) { i, b ->
                    BandSlider(fmtHz(b.freq) + " Hz", b.gainDb, -12f..12f) { g ->
                        app.eq.edit(preset.copy(id = editId(preset), name = editName(preset), source = "user", bands = preset.bands.toMutableList().also { it[i] = b.copy(gainDb = g) }))
                    }
                }
            } else {
                itemsIndexed(preset.bands) { i, b ->
                    ParametricBand(b, onChange = { nb ->
                        app.eq.edit(preset.copy(id = editId(preset), name = editName(preset), source = "user", bands = preset.bands.toMutableList().also { it[i] = nb }))
                    }, onRemove = {
                        app.eq.edit(preset.copy(id = editId(preset), name = editName(preset), source = "user", bands = preset.bands.filterIndexed { j, _ -> j != i }))
                    })
                }
                item {
                    OutlinedButton({
                        app.eq.edit(preset.copy(id = editId(preset), name = editName(preset), source = "user", bands = preset.bands + EqBand(FilterType.PEAK, 1000.0, 0.0, 1.0)))
                    }, Modifier.padding(16.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add band") }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    SettingSwitch("Follow output device", "Remember a preset for each headphone, speaker and DAC", eq.autoSwitch) { app.eq.setAutoSwitch(it) }
                    SettingSwitch("Recognise headphones", "Look up new Bluetooth and wired headphones in the AutoEq database and apply their correction", eq.autoEqLookup) { app.eq.setAutoEqLookup(it) }
                    Text(
                        "Your presets and device assignments sync to your Navidrome account, so other devices running Tarang get them too.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
    if (saveAs) {
        var name by remember { mutableStateOf(if (preset.source == "user" && preset.name.endsWith("*")) preset.name.dropLast(1).trim() else "") }
        AlertDialog(
            onDismissRequest = { saveAs = false },
            confirmButton = { TextButton({ app.eq.saveCurrentAs(name.ifBlank { "My preset" }); saveAs = false }) { Text("Save") } },
            dismissButton = { TextButton({ saveAs = false }) { Text("Cancel") } },
            title = { Text("Save preset") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
        )
    }
    if (autoEq) AutoEqDialog(output?.name) { autoEq = false }
    if (paste) PasteDialog { paste = false }
}

private fun editId(p: EqPreset) = if (p.source == "user") p.id else "edit-" + p.id
private fun editName(p: EqPreset) = if (p.source == "user") p.name else p.name + " *"

private fun toGraphic(p: EqPreset): EqPreset {
    val curve = EqPresets.graphicFreqs.map { f ->
        val full = EqEngine.responseCurve(p.copy(preampDb = 0.0), points = 200)
        full.minByOrNull { kotlin.math.abs(log10(it.first) - log10(f)) }!!.second
    }
    return EqPresets.graphic("user-" + nowMillis().toString(36), p.name + " (graphic)", curve.map { (it * 2).roundToInt() / 2.0 }, p.preampDb).copy(source = "user")
}

@Composable
private fun ResponseCurve(preset: EqPreset, enabled: Boolean) {
    val curve = remember(preset) { EqEngine.responseCurve(preset, 160) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val maxDb = 15.0
        fun x(f: Double) = ((log10(f) - log10(20.0)) / (log10(20000.0) - log10(20.0))).toFloat() * size.width
        fun y(db: Double) = (size.height / 2 - (db / maxDb) * size.height / 2).toFloat()
        listOf(-12.0, -6.0, 0.0, 6.0, 12.0).forEach { d -> drawLine(grid, Offset(0f, y(d)), Offset(size.width, y(d)), strokeWidth = if (d == 0.0) 2f else 1f) }
        listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0).forEach { f -> drawLine(grid, Offset(x(f), 0f), Offset(x(f), size.height), strokeWidth = 1f) }
        val path = Path()
        curve.forEachIndexed { i, (f, db) ->
            val px = x(f); val py = y(db.coerceIn(-maxDb, maxDb))
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, if (enabled) line else dim, style = Stroke(width = 5f))
    }
}

@Composable
private fun BandSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Slider(value.toFloat(), { onChange(((it * 2).roundToInt() / 2.0)) }, valueRange = range, modifier = Modifier.weight(1f))
        Text(fmtDb(value), Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ParametricBand(b: EqBand, onChange: (EqBand) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(b.enabled, { onChange(b.copy(enabled = it)) })
                Dropdown(b.type.label, FilterType.entries.map { it.label }) { onChange(b.copy(type = FilterType.entries[it])) }
                Spacer(Modifier.weight(1f))
                IconButton(onRemove) { Icon(Icons.Rounded.Close, "Remove band") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Freq", Modifier.width(48.dp))
                // Log scale slider from 20 Hz to 20 kHz.
                val pos = ((log10(b.freq) - log10(20.0)) / 3.0).toFloat()
                Slider(pos, { p -> onChange(b.copy(freq = (20.0 * 10.0.pow(p * 3.0)).roundToInt().toDouble())) }, modifier = Modifier.weight(1f))
                Text(fmtHz(b.freq), Modifier.width(48.dp))
            }
            if (b.type != FilterType.LOW_PASS && b.type != FilterType.HIGH_PASS) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gain", Modifier.width(48.dp))
                Slider(b.gainDb.toFloat(), { onChange(b.copy(gainDb = (it * 10).roundToInt() / 10.0)) }, valueRange = -15f..15f, modifier = Modifier.weight(1f))
                Text(fmtDb(b.gainDb), Modifier.width(48.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Q", Modifier.width(48.dp))
                Slider(b.q.toFloat(), { onChange(b.copy(q = (it * 100).roundToInt() / 100.0)) }, valueRange = 0.1f..10f, modifier = Modifier.weight(1f))
                Text("${b.q}", Modifier.width(48.dp))
            }
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun AutoEqDialog(deviceName: String?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(deviceName ?: "") }
    var results by remember { mutableStateOf<List<AutoEqEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        delay(300)
        busy = true
        results = runCatching { app.eq.searchAutoEq(query) }.getOrDefault(emptyList())
        busy = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("AutoEq headphone database") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Headphone model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (busy) CircularProgressIndicator(Modifier.padding(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { e ->
                        ListItem(
                            headlineContent = { Text(e.name) },
                            supportingContent = { Text(e.source) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val p = runCatching { app.eq.fetchAutoEq(e) }.getOrNull()
                                    if (p == null) app.toast("Could not load profile") else {
                                        app.eq.import(p)
                                        app.eq.select(p)
                                        app.toast("Applied ${p.name}")
                                        onDismiss()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var name by remember { mutableStateOf("Imported") }
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, name) { AutoEq.parse("user-" + nowMillis().toString(36), name, text)?.copy(source = "user") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { app.eq.import(it); app.eq.select(it) }; onDismiss() }) { Text("Import") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Import parametric EQ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste a ParametricEQ.txt from AutoEq, Equalizer APO, Wavelet or Peace.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Preamp: -6 dB / Filter 1: ON PK Fc ...") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                Text(parsed?.let { "${it.bands.size} filters, preamp ${it.preampDb} dB" } ?: "Nothing recognised yet", style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}
