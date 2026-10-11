package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.AlbumListType
import com.westtiwan.tarang.ui.AlbumCard
import com.westtiwan.tarang.ui.AlbumRow
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.MemCache
import com.westtiwan.tarang.ui.OfflineLibrary
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.SectionHeader
import com.westtiwan.tarang.ui.rememberLoad

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val online by app.online.collectAsState()
    val settings by app.settings.collectAsState()
    val smart by app.smartStore.state.collectAsState()
    val offline = settings.offlineMode || !online

    if (offline) {
        OfflineHome()
        return
    }
    val api = app.api ?: return
    val recent = rememberLoad("home:recent:${api.server.id}") { api.getAlbumList(AlbumListType.RECENT, 20) }
    val newest = rememberLoad("home:newest:${api.server.id}") { api.getAlbumList(AlbumListType.NEWEST, 20) }
    val frequent = rememberLoad("home:frequent:${api.server.id}") { api.getAlbumList(AlbumListType.FREQUENT, 20) }
    val random = rememberLoad("home:random:${api.server.id}") { api.getAlbumList(AlbumListType.RANDOM, 20) }
    val starred = rememberLoad("home:starred:${api.server.id}") { api.getAlbumList(AlbumListType.STARRED, 20) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tarang", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton({ MemCache.invalidate("home:"); recent.reload(); newest.reload(); frequent.reload(); random.reload(); starred.reload() }) {
                    Icon(Icons.Rounded.Refresh, "Refresh")
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickAction(Icons.Rounded.Shuffle, "Shuffle all") {
                        app.launch {
                            val songs = app.library.index.value.songs.shuffled().take(500).ifEmpty { runCatching { api.getRandomSongs(200) }.getOrDefault(emptyList()) }
                            if (songs.isNotEmpty()) app.player.play(songs)
                        }
                    }
                }
                item {
                    QuickAction(Icons.Rounded.Favorite, "Favourites") {
                        app.launch {
                            val songs = runCatching { api.getStarred().song }.getOrDefault(emptyList())
                            if (songs.isEmpty()) app.toast("No favourite songs yet") else app.player.play(songs.shuffled())
                        }
                    }
                }
                item {
                    QuickAction(Icons.Rounded.History, "Resume") {
                        app.launch {
                            if (app.coordinator?.restoreServerQueue(true) != true) app.toast("Nothing saved on the server")
                        }
                    }
                }
                item { QuickAction(Icons.Rounded.Radio, "Radio") { nav.push(Screen.Radio) } }
            }
        }
        if (smart.playlists.isNotEmpty()) {
            item { SectionHeader("Mixes for you", "Edit") { nav.push(Screen.SmartEdit(null)) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(smart.playlists, key = { it.id }) { pl ->
                        ElevatedCard(Modifier.width(170.dp).height(86.dp).clip(RoundedCornerShape(12.dp)).clickable { nav.push(Screen.SmartDetail(pl.id)) }) {
                            Column(Modifier.padding(12.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(pl.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        homeSection("Recently played", recent.value) { nav.push(Screen.AlbumList(AlbumListType.RECENT)) }
        homeSection("Recently added", newest.value) { nav.push(Screen.AlbumList(AlbumListType.NEWEST)) }
        homeSection("Most played", frequent.value) { nav.push(Screen.AlbumList(AlbumListType.FREQUENT)) }
        homeSection("Favourite albums", starred.value) { nav.push(Screen.AlbumList(AlbumListType.STARRED)) }
        homeSection("Discover", random.value) { nav.push(Screen.AlbumList(AlbumListType.RANDOM)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeSection(title: String, albums: List<com.westtiwan.tarang.data.Album>?, onMore: () -> Unit) {
    if (albums.isNullOrEmpty()) return
    item { SectionHeader(title, "See all", onMore) }
    item { AlbumRow(albums) }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OfflineHome() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val downloads by app.downloads.state.collectAsState()
    val albums = androidx.compose.runtime.remember(downloads) { OfflineLibrary.albums(app) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Offline", style = MaterialTheme.typography.headlineSmall)
                    Text("Playing from downloads. Plays are scrobbled when you are back online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (albums.isEmpty()) {
            item { com.westtiwan.tarang.ui.CenteredMessage("Nothing downloaded yet.") }
        } else {
            item {
                QuickAction(Icons.Rounded.Shuffle, "Shuffle downloads") { app.player.play(OfflineLibrary.songs(app).shuffled()) }
            }
            items(albums.chunked(2)) { pair ->
                Row(Modifier.padding(horizontal = 12.dp)) {
                    pair.forEach { AlbumCard(it, Modifier.weight(1f), width = null) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}
