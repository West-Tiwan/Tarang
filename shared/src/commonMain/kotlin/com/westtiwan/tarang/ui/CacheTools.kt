package com.westtiwan.tarang.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.Song

/** Drops every cached copy of an image (memory, disk) whose URL refers to [coverArt]. */
fun clearImageCache(context: PlatformContext, app: AppContainer, coverArt: String?, extraUrls: List<String> = emptyList()) {
    val loader = SingletonImageLoader.get(context)
    val needles = listOfNotNull(coverArt?.let { "id=$it" }) + extraUrls
    if (needles.isEmpty()) return
    loader.memoryCache?.let { mem -> mem.keys.filter { k -> needles.any { k.key.contains(it) } }.forEach { mem.remove(it) } }
    loader.diskCache?.let { disk ->
        val sizes = listOf(null, 120, 128, 150, 300, 400, 600, 800, 1000)
        coverArt?.let { id -> sizes.forEach { size -> app.api?.coverArtUrl(id, size)?.let { disk.remove(it) } } }
        extraUrls.forEach { disk.remove(it) }
    }
    app.coverEpoch.value++
}

@Composable
fun ClearCacheDialog(song: Song, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalPlatformContext.current
    var lyrics by remember { mutableStateOf(true) }
    var translation by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear cached data") },
        text = {
            Column {
                Text(song.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CheckLine("Lyrics", "Fetched again from Navidrome or LRCLIB", lyrics) { lyrics = it }
                CheckLine("Translations", "Translated again with the current engine", translation) { translation = it }
                CheckLine("Cover art", "Downloaded again from the server", cover) { cover = it }
                CheckLine("Cached audio", "Streamed fresh next time it plays", audio) { audio = it }
                Text("Pronunciation is worked out on the fly, so it never goes stale.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton({
                if (lyrics) app.lyrics.clearCache(song)
                if (translation) app.translations.clear(song.id)
                if (cover) clearImageCache(context, app, song.coverArt)
                if (audio) app.player.clearAudioCache(song.id)
                if (lyrics || translation) app.lyricsEpoch.value++
                app.toast("Cleared")
                onDismiss()
            }) { Text("Clear") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
