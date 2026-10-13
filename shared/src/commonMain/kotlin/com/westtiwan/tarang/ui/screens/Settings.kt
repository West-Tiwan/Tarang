package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.ReplayGainMode
import com.westtiwan.tarang.data.ThemeMode
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.platform.nowMillis
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.LocalPlatformUi
import com.westtiwan.tarang.ui.MemCache
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.formatBytes

private val bitrates = listOf(0, 320, 256, 192, 128, 96)
private fun bitrateLabel(b: Int) = if (b == 0) "Original" else "$b kbps"
private val formats = listOf("raw" to "Original file", "opus" to "Opus", "mp3" to "MP3", "aac" to "AAC")
private val languages = listOf("en" to "English", "hi" to "Hindi", "pa" to "Punjabi", "ur" to "Urdu", "es" to "Spanish", "fr" to "French", "de" to "German", "ja" to "Japanese", "ko" to "Korean", "ta" to "Tamil", "te" to "Telugu", "bn" to "Bengali", "mr" to "Marathi")

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val s by app.settings.collectAsState()
    val server by app.activeServer.collectAsState()
    val progress by app.library.progress.collectAsState()
    val index by app.library.index.collectAsState()
    val platformUi = LocalPlatformUi.current
    var picker by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        Group("Server")
        NavRow("Servers", server?.let { "${it.name} (${it.username})" }) { nav.push(Screen.Servers) }
        NavRow("Library index", if (progress.running) "Syncing: ${progress.fetched} songs" else "${index.songs.size} songs" + (progress.error?.let { " • $it" } ?: "")) {
            app.launch { app.api?.let { app.library.sync(it, nowMillis()) } }
        }
        NavRow("Scan server library", "Ask Navidrome to rescan your music folder") {
            app.launch { runCatching { app.api?.startScan() }.onSuccess { app.toast("Scan started") }.onFailure { app.toast(it.message) } }
        }
        Pad { SettingSwitch("Offline mode", "Only play downloaded music", s.offlineMode) { v -> app.updateSettings { it.copy(offlineMode = v) }; MemCache.clear() } }

        Group("Streaming")
        NavRow("Wi-Fi quality", "${formats.first { it.first == s.wifiFormat }.second} • ${bitrateLabel(s.wifiMaxBitrate)}") { picker = Picker.WIFI }
        NavRow("Mobile data quality", "${formats.first { it.first == s.mobileFormat }.second} • ${bitrateLabel(s.mobileMaxBitrate)}") { picker = Picker.MOBILE }
        NavRow("Download quality", "${formats.first { it.first == s.downloadFormat }.second} • ${bitrateLabel(s.downloadMaxBitrate)}") { picker = Picker.DOWNLOAD }
        if (!platformUi.isDesktop) {
            Pad {
                Text("Streaming cache: ${s.cacheSizeMb / 1024.0} GB", style = MaterialTheme.typography.bodyLarge)
                Slider(s.cacheSizeMb.toFloat(), { v -> app.updateSettings { it.copy(cacheSizeMb = (v / 512).toInt() * 512) } }, valueRange = 512f..32768f)
            }
        }
        NavRow("Downloads", "${app.downloads.state.value.items.size} songs • ${formatBytes(app.downloads.totalBytes())}") { nav.push(Screen.Downloads) }

        Group("Audio")
        NavRow("Equalizer", if (app.eqStore.value.enabled) app.eqStore.value.current.name else "Off") { nav.push(Screen.Equalizer) }
        platformUi.audioSettings?.invoke()
        NavRow("Replay gain", s.replayGain.name.lowercase().replaceFirstChar { it.uppercase() } + if (s.replayGain != ReplayGainMode.OFF) " • preamp ${s.replayGainPreampDb} dB" else "") { picker = Picker.REPLAY_GAIN }
        Pad { SettingSwitch("Gapless playback", null, s.gapless) { v -> app.updateSettings { it.copy(gapless = v) } } }

        Group("Lyrics")
        Pad { SettingSwitch("Show pronunciation", "Foreign lyrics written out in English letters, like romaji for Japanese or Hinglish for Hindi", s.showRomanized) { v -> app.updateSettings { it.copy(showRomanized = v) } } }
        Pad { SettingSwitch("Translate foreign songs automatically", "Shows the translation under each line when a song is not in your language", s.autoTranslateForeign) { v -> app.updateSettings { it.copy(autoTranslateForeign = v) } } }
        Pad { SettingSwitch("Always show translation", null, s.showTranslation) { v -> app.updateSettings { it.copy(showTranslation = v) } } }
        Pad { SettingSwitch("Search LRCLIB when the server has none", null, "lrclib" in s.lyricsSources) { v -> app.updateSettings { it.copy(lyricsSources = if (v) listOf("server", "lrclib") else listOf("server")) } } }
        NavRow("Translate to", languages.firstOrNull { it.first == s.translateTarget }?.second ?: s.translateTarget) { picker = Picker.LANGUAGE }
        Pad {
            Text(
                if (platformUi.isDesktop) "Translation uses a LibreTranslate server. You can self-host one next to Navidrome."
                else "Translation runs on device with Google ML Kit (the language model downloads once). Set a LibreTranslate server to use that instead.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(s.libreTranslateUrl, { v -> app.updateSettings { it.copy(libreTranslateUrl = v.trim()) } }, label = { Text("LibreTranslate URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.libreTranslateKey, { v -> app.updateSettings { it.copy(libreTranslateKey = v.trim()) } }, label = { Text("API key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation())
        }

        Group("Scrobbling")
        Pad { SettingSwitch("Scrobble plays", "Navidrome forwards to Last.fm and ListenBrainz when linked in its web UI", s.scrobble) { v -> app.updateSettings { it.copy(scrobble = v) } } }
        Pad {
            OutlinedTextField(s.listenBrainzToken, { v -> app.updateSettings { it.copy(listenBrainzToken = v.trim()) } }, label = { Text("ListenBrainz token (optional, direct)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            if (app.scrobbler.pendingCount > 0) Text("${app.scrobbler.pendingCount} plays waiting to be sent", style = MaterialTheme.typography.bodySmall)
        }

        Group("Sync")
        Pad { SettingSwitch("Sync play queue", "Pick up where you left off on any device", s.syncPlayQueue) { v -> app.updateSettings { it.copy(syncPlayQueue = v) } } }
        Pad { SettingSwitch("Sync EQ and smart playlists", "Stored in a private playlist named \"Tarang settings\" on your server", s.syncEqToServer) { v -> app.updateSettings { it.copy(syncEqToServer = v) } } }
        NavRow("Sync now", null) { app.launch { app.pullSyncedSettings(); app.pushSyncedSettings(); app.toast("Synced") } }

        Group("Appearance")
        NavRow("Theme", s.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { picker = Picker.THEME }
        if (!platformUi.isDesktop) Pad { SettingSwitch("Wallpaper colours", "Material You colours from your wallpaper, Android 12 and later", s.dynamicColor) { v -> app.updateSettings { it.copy(dynamicColor = v) } } }
        Pad { SettingSwitch("Album art colours in the player", "The player picks up the colours of the current cover", s.albumArtColors) { v -> app.updateSettings { it.copy(albumArtColors = v) } } }
        if (!platformUi.isDesktop) Pad { SettingSwitch("Album art colours everywhere", "Use the cover colours for the whole app instead of the wallpaper", s.albumArtEverywhere) { v -> app.updateSettings { it.copy(albumArtEverywhere = v) } } }
        Pad { SettingSwitch("Pure black", "For OLED screens", s.pureBlack) { v -> app.updateSettings { it.copy(pureBlack = v) } } }

        Group("Storage")
        NavRow("Clear lyrics and image cache", formatBytes(Files.size(Platform.cacheDir()))) {
            Files.delete(Platform.cacheDir() + "/lyrics")
            MemCache.clear()
            app.toast("Cache cleared")
        }

        Text("Tarang 1.0.0 • ${Platform.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }

    when (picker) {
        Picker.WIFI, Picker.MOBILE, Picker.DOWNLOAD -> {
            val which = picker!!
            QualityDialog(
                when (which) { Picker.WIFI -> s.wifiFormat; Picker.MOBILE -> s.mobileFormat; else -> s.downloadFormat },
                when (which) { Picker.WIFI -> s.wifiMaxBitrate; Picker.MOBILE -> s.mobileMaxBitrate; else -> s.downloadMaxBitrate },
                onDismiss = { picker = null },
            ) { f, b ->
                app.updateSettings {
                    when (which) {
                        Picker.WIFI -> it.copy(wifiFormat = f, wifiMaxBitrate = b)
                        Picker.MOBILE -> it.copy(mobileFormat = f, mobileMaxBitrate = b)
                        else -> it.copy(downloadFormat = f, downloadMaxBitrate = b)
                    }
                }
            }
        }
        Picker.REPLAY_GAIN -> AlertDialog(
            onDismissRequest = { picker = null },
            confirmButton = { TextButton({ picker = null }) { Text("Done") } },
            title = { Text("Replay gain") },
            text = {
                Column {
                    ReplayGainMode.entries.forEach { m ->
                        RadioLine(m.name.lowercase().replaceFirstChar { it.uppercase() }, s.replayGain == m) { app.updateSettings { it.copy(replayGain = m) }; app.player.refreshAudio() }
                    }
                    Text("Preamp: ${s.replayGainPreampDb} dB")
                    Slider(s.replayGainPreampDb.toFloat(), { v -> app.updateSettings { it.copy(replayGainPreampDb = (v * 2).toInt() / 2.0) } }, valueRange = -15f..15f)
                }
            },
        )
        Picker.LANGUAGE -> ListDialog("Translate to", languages.map { it.second }, languages.indexOfFirst { it.first == s.translateTarget }, { picker = null }) { i ->
            app.updateSettings { it.copy(translateTarget = languages[i].first) }
        }
        Picker.THEME -> ListDialog("Theme", ThemeMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, s.theme.ordinal, { picker = null }) { i ->
            app.updateSettings { it.copy(theme = ThemeMode.entries[i]) }
        }
        null -> {}
    }
}

private enum class Picker { WIFI, MOBILE, DOWNLOAD, REPLAY_GAIN, LANGUAGE, THEME }

@Composable
private fun Group(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
}

@Composable
private fun Pad(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) { content() }
}

@Composable
fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ListDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { options.forEachIndexed { i, o -> RadioLine(o, i == selected) { onSelect(i); onDismiss() } } } },
    )
}

@Composable
private fun QualityDialog(format: String, bitrate: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var f by remember { mutableStateOf(format) }
    var b by remember { mutableStateOf(bitrate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onSave(f, if (f == "raw") 0 else b); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Quality") },
        text = {
            Column {
                Text("Format", style = MaterialTheme.typography.titleSmall)
                formats.forEach { (k, label) -> RadioLine(label, f == k) { f = k } }
                if (f != "raw") {
                    Text("Max bitrate", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    bitrates.drop(1).forEach { r -> RadioLine(bitrateLabel(r), b == r) { b = r } }
                } else {
                    Text("Streams the untouched file, including FLAC and hi-res.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
