package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.westtiwan.tarang.art.ArtCandidate
import com.westtiwan.tarang.data.Artist
import com.westtiwan.tarang.data.ArtistInfo
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalPlatformUi
import com.westtiwan.tarang.ui.LoadingDots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ArtistImageDialog(artist: Artist, info: ArtistInfo?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val platform = LocalPlatformUi.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(artist.name) }
    var results by remember { mutableStateOf<List<ArtCandidate>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val server = remember(info) {
        listOfNotNull(info?.largeImageUrl, info?.mediumImageUrl).filter { it.startsWith("http") }.distinct()
            .map { ArtCandidate(it, artist.name, "Navidrome (Last.fm or Spotify)") }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(400)
        busy = true
        results = runCatching { app.artistArt.search(query) }.getOrDefault(emptyList())
        busy = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Artist image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (platform.supportsImagePicking) FilledTonalButton({
                        scope.launch {
                            val bytes = platform.pickImage() ?: return@launch
                            app.artistArt.setUpload(artist.id, bytes)
                            app.toast("Updated ${artist.name}")
                            onDismiss()
                        }
                    }) { Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(6.dp)); Text("Upload") }
                    OutlinedButton({ app.artistArt.reset(artist.id); onDismiss() }) {
                        Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text("Server image")
                    }
                }
                OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                    label = { Text("Search Deezer and TheAudioDB") }, modifier = Modifier.fillMaxWidth())
                if (busy) LoadingDots()
                val all = server + results
                if (!busy && all.isEmpty()) Text("Nothing found. Try the artist's name in another script.", style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(GridCells.Adaptive(110.dp), modifier = Modifier.height(360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(all, key = { it.url }) { c ->
                        Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            app.artistArt.setUrl(artist.id, c.url)
                            app.toast("Updated ${artist.name}")
                            onDismiss()
                        }.padding(4.dp)) {
                            AsyncImage(c.url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                            Text(c.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}
