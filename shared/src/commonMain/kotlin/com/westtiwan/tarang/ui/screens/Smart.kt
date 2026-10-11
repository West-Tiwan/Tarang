package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.platform.nowMillis
import com.westtiwan.tarang.smart.SmartField
import com.westtiwan.tarang.smart.SmartOp
import com.westtiwan.tarang.smart.SmartPlaylist
import com.westtiwan.tarang.smart.SmartRule
import com.westtiwan.tarang.smart.SmartSort
import com.westtiwan.tarang.ui.CenteredMessage
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.SongRow
import com.westtiwan.tarang.ui.formatDuration

@Composable
fun SmartScreen(id: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val smart by app.smartStore.state.collectAsState()
    val index by app.library.index.collectAsState()
    val pl = smart.playlists.firstOrNull { it.id == id }
    if (pl == null) { BackBar("Smart playlist"); CenteredMessage("Playlist not found"); return }
    val songs = remember(pl, index) { app.evaluateSmart(pl) }
    val st by app.player.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackBar(pl.name) {
            IconButton({
                app.launch {
                    app.syncSmartToServer(pl).onSuccess { app.toast("Saved to server playlist") }.onFailure { app.toast(it.message) }
                }
            }) { Icon(Icons.Rounded.CloudUpload, "Save to server") }
            IconButton({ nav.push(Screen.SmartEdit(pl)) }) { Icon(Icons.Rounded.Edit, "Edit") }
        }
        Text(
            "${songs.size} songs • ${formatDuration(songs.sumOf { it.durationMs })}" + if (index.songs.isEmpty()) " • library index not synced yet" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ app.player.play(songs) }, enabled = songs.isNotEmpty()) { Text("Play") }
            FilledTonalButton({ app.player.play(songs.shuffled()) }, enabled = songs.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
            Spacer(Modifier.weight(1f))
            DownloadButton(songs)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(songs.size, key = { songs[it].id }) { i -> SongRow(songs[i], onClick = { app.player.play(songs, i) }, active = st.current?.id == songs[i].id) }
        }
    }
}

@Composable
fun SmartEditScreen(initial: SmartPlaylist?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var pl by remember { mutableStateOf(initial ?: SmartPlaylist(id = "smart-" + nowMillis().toString(36), name = "", rules = listOf(SmartRule(SmartField.GENRE, SmartOp.CONTAINS, "")))) }
    val preview = remember(pl) { app.evaluateSmart(pl) }
    Column(Modifier.fillMaxSize()) {
        BackBar(if (initial == null) "New smart playlist" else "Edit smart playlist") {
            if (initial != null) IconButton({ app.deleteSmart(pl.id); nav.back(); nav.back() }) { Icon(Icons.Rounded.Delete, "Delete") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(pl.name, { pl = pl.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(pl.matchAll, { pl = pl.copy(matchAll = true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Match all rules") }
                    SegmentedButton(!pl.matchAll, { pl = pl.copy(matchAll = false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Match any") }
                }
            }
            itemsIndexed(pl.rules) { i, rule ->
                RuleEditor(rule, onChange = { r -> pl = pl.copy(rules = pl.rules.toMutableList().also { it[i] = r }) }, onRemove = { pl = pl.copy(rules = pl.rules.filterIndexed { j, _ -> j != i }) })
            }
            item {
                OutlinedButton({ pl = pl.copy(rules = pl.rules + SmartRule(SmartField.ARTIST, SmartOp.CONTAINS, "")) }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add rule")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by", Modifier.width(80.dp))
                    Dropdown(pl.sort.label, SmartSort.entries.map { it.label }) { pl = pl.copy(sort = SmartSort.entries[it]) }
                    Spacer(Modifier.width(12.dp))
                    Text("Descending")
                    Switch(pl.descending, { pl = pl.copy(descending = it) }, Modifier.padding(start = 8.dp))
                }
            }
            item {
                OutlinedTextField(
                    pl.limit?.toString() ?: "", { pl = pl.copy(limit = it.toIntOrNull()) },
                    label = { Text("Limit (empty for no limit)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep a server playlist in sync")
                        Text("Writes the result to a normal Navidrome playlist so every app sees it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(pl.syncToServer, { pl = pl.copy(syncToServer = it) })
                }
            }
            item { Text("Preview: ${preview.size} songs", style = MaterialTheme.typography.titleSmall) }
            items(preview.take(8), key = { "p" + it.id }) { s ->
                Text("${s.title} • ${s.artist ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(
            {
                app.saveSmart(pl)
                if (pl.syncToServer) app.launch { app.syncSmartToServer(pl) }
                nav.back()
            },
            enabled = pl.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun RuleEditor(rule: SmartRule, onChange: (SmartRule) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dropdown(rule.field.label, SmartField.entries.map { it.label }) { i ->
                    val f = SmartField.entries[i]
                    val ops = SmartOp.forKind(f.kind)
                    onChange(rule.copy(field = f, op = if (rule.op in ops) rule.op else ops.first()))
                }
                Spacer(Modifier.width(8.dp))
                val ops = SmartOp.forKind(rule.field.kind)
                Dropdown(rule.op.label, ops.map { it.label }) { onChange(rule.copy(op = ops[it])) }
                Spacer(Modifier.weight(1f))
                IconButton(onRemove) { Icon(Icons.Rounded.Close, "Remove rule") }
            }
            val needsValue = rule.field.kind != SmartField.Kind.BOOL && rule.op != SmartOp.NEVER
            if (needsValue) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val numeric = rule.field.kind != SmartField.Kind.TEXT
                    OutlinedTextField(rule.value, { onChange(rule.copy(value = it)) }, singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text(if (rule.field.kind == SmartField.Kind.DATE) "Days" else "Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
                    if (rule.op == SmartOp.BETWEEN) OutlinedTextField(rule.value2, { onChange(rule.copy(value2 = it)) }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("And") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        }
    }
}

@Composable
fun Dropdown(selected: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }) { Text(selected) }
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o) }, { onSelect(i); open = false }) }
        }
    }
}
