package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.westtiwan.tarang.data.Album
import com.westtiwan.tarang.data.Artist
import com.westtiwan.tarang.data.Playlist
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.download.DownloadStatus
import com.westtiwan.tarang.ui.AddToPlaylistDialog
import com.westtiwan.tarang.ui.AlbumCard
import com.westtiwan.tarang.ui.ArtistCircle
import com.westtiwan.tarang.ui.ArtistImage
import com.westtiwan.tarang.ui.ArtistShape
import com.westtiwan.tarang.ui.CoverArt
import com.westtiwan.tarang.ui.LoadContent
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.MemCache
import com.westtiwan.tarang.ui.OfflineLibrary
import com.westtiwan.tarang.ui.QualityBadge
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.SectionHeader
import com.westtiwan.tarang.ui.SongRow
import com.westtiwan.tarang.ui.formatDuration
import com.westtiwan.tarang.ui.rememberLoad

@Composable
fun BackBar(title: String = "", actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun DownloadButton(songs: List<Song>) {
    val app = LocalApp.current
    val state by app.downloads.state.collectAsState()
    val done = songs.isNotEmpty() && songs.all { state.items[it.id]?.status == DownloadStatus.DONE }
    val inProgress = songs.any { state.items[it.id]?.status.let { s -> s == DownloadStatus.QUEUED || s == DownloadStatus.DOWNLOADING } }
    IconButton({
        if (done) app.downloads.remove(songs.map { it.id }) else { app.downloads.enqueue(songs); app.toast("Downloading ${songs.size} songs") }
    }) {
        when {
            done -> Icon(Icons.Rounded.DownloadDone, "Remove download", tint = MaterialTheme.colorScheme.secondary)
            inProgress -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else -> Icon(Icons.Rounded.CloudDownload, "Download")
        }
    }
}

@Composable
private fun PlayButtons(songs: List<Song>, extra: @Composable () -> Unit = {}) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ app.player.play(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
        }
        FilledTonalButton({ app.player.play(songs.shuffled()) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
        }
        Spacer(Modifier.weight(1f))
        extra()
        DownloadButton(songs)
    }
}

@Composable
private fun Header(coverArt: String?, title: String, subtitle: String?, meta: String?, icon: ImageVector, round: Boolean = false, onSubtitle: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(
            coverArt,
            Modifier.widthIn(max = 280.dp).fillMaxWidth(0.7f).aspectRatio(1f),
            size = 800,
            shape = if (round) CircleShape else RoundedCornerShape(14.dp),
            icon = icon,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) Text(
            subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = if (onSubtitle != null) Modifier.padding(top = 2.dp).clickable(onClick = onSubtitle) else Modifier,
        )
        if (meta != null) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun AlbumScreen(id: String, preview: Album?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val st by app.player.state.collectAsState()
    val album = rememberLoad("album:$id") {
        if (app.offline) OfflineLibrary.album(app, id) ?: error("Album not downloaded") else app.api?.getAlbum(id) ?: error("Not connected")
    }
    var starred by remember(album.value) { mutableStateOf(album.value?.isStarred ?: preview?.isStarred ?: false) }
    val context = coil3.compose.LocalPlatformContext.current
    Column(Modifier.fillMaxSize()) {
        BackBar {
            IconButton({
                val a = album.value ?: preview
                com.westtiwan.tarang.ui.clearImageCache(context, app, a?.coverArt)
                a?.song?.forEach { s -> if (s.coverArt != a.coverArt) com.westtiwan.tarang.ui.clearImageCache(context, app, s.coverArt) }
                app.toast("Cover reloaded")
            }) { Icon(Icons.Rounded.Refresh, "Reload cover") }
            IconButton({
                starred = !starred
                app.launch { runCatching { if (starred) app.api?.star(albumId = id) else app.api?.unstar(albumId = id) }; MemCache.invalidate("home:") }
            }) { Icon(if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }
        LoadContent(album) { a ->
            val songs = a.song
            val discs = songs.groupBy { it.discNumber ?: 1 }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    val quality = songs.firstOrNull()
                    Header(
                        a.coverArt, a.name, a.artist,
                        listOfNotNull(a.year?.toString(), a.genre, "${songs.size} songs", formatDuration(a.duration * 1000L)).joinToString(" • "),
                        Icons.Rounded.Album,
                        onSubtitle = a.artistId?.let { aid -> { nav.push(Screen.ArtistDetail(aid)) } },
                    )
                    if (quality != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { QualityBadge(quality) }
                }
                item { PlayButtons(songs) }
                discs.toSortedMap().forEach { (disc, list) ->
                    if (discs.size > 1) item { Text("Disc $disc", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
                    items(list.size, key = { list[it].id }) { i ->
                        val s = list[i]
                        SongRow(s, onClick = { app.player.play(songs, songs.indexOf(s)) }, showCover = false, trackNumber = s.track ?: (i + 1), active = st.current?.id == s.id)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(id: String, preview: Artist?) {
    val app = LocalApp.current
    val st by app.player.state.collectAsState()
    val index by app.library.index.collectAsState()
    val artist = rememberLoad("artist:$id") {
        if (app.offline) OfflineLibrary.artist(app, id) ?: error("Artist not downloaded") else app.api?.getArtist(id) ?: error("Not connected")
    }
    val info = rememberLoad("artistinfo:$id") { if (app.offline) null else app.api?.getArtistInfo(id) }
    val top = rememberLoad("top:$id:${artist.value?.name}") { artist.value?.name?.let { if (app.offline) null else app.api?.getTopSongs(it) }.orEmpty() }
    var bioExpanded by remember { mutableStateOf(false) }
    var editImage by remember { mutableStateOf(false) }
    var starred by remember(artist.value) { mutableStateOf(artist.value?.isStarred ?: preview?.isStarred ?: false) }
    // Songs credited to this artist, including ones on albums filed under another album artist.
    val credited = remember(index, id) { index.songs.filter { s -> s.artistId == id || s.artists.any { it.id == id } } }
    Column(Modifier.fillMaxSize()) {
        BackBar {
            IconButton({ editImage = true }) { Icon(Icons.Rounded.AddPhotoAlternate, "Change artist image") }
            IconButton({
                starred = !starred
                app.launch { runCatching { if (starred) app.api?.star(artistId = id) else app.api?.unstar(artistId = id) } }
            }) { Icon(if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }
        LoadContent(artist) { a ->
            val ownAlbums = a.album.sortedByDescending { it.year ?: 0 }
            val appearsOn = remember(credited, ownAlbums) {
                credited.filter { s -> ownAlbums.none { it.id == s.albumId } }.groupBy { it.albumId ?: it.album ?: "" }.map { (aid, list) ->
                    val f = list.first()
                    Album(id = f.albumId ?: aid, name = f.album ?: "", artist = f.displayAlbumArtist ?: f.artist, coverArt = f.coverArt, year = f.year, songCount = list.size)
                }.sortedByDescending { it.year ?: 0 }
            }
            val topSongs = top.value.orEmpty().ifEmpty { credited.sortedByDescending { it.playCount ?: 0 }.take(10) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtistImage(a.id, a.coverArt, Modifier.size(220.dp).clickable { editImage = true }, size = 800, shape = ArtistShape)
                        Spacer(Modifier.height(16.dp))
                        Text(a.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            listOfNotNull(
                                ownAlbums.size.takeIf { it > 0 }?.let { "$it albums" },
                                credited.size.takeIf { it > 0 }?.let { "$it songs" },
                            ).joinToString(" • ").ifBlank { "No albums" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Button({
                            app.launch {
                                val songs = ownAlbums.flatMap { al -> runCatching { app.api?.getAlbum(al.id)?.song }.getOrNull() ?: al.song } + credited
                                val unique = songs.distinctBy { it.id }
                                if (unique.isNotEmpty()) app.player.play(unique.shuffled())
                            }
                        }) { Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                        FilledTonalButton({
                            app.launch {
                                val seed = topSongs.firstOrNull() ?: return@launch
                                val songs = runCatching { app.api?.getSimilarSongs(seed.id, 100) }.getOrNull().orEmpty()
                                app.player.play(topSongs.take(5) + songs)
                            }
                        }) { Icon(Icons.Rounded.Radio, null); Spacer(Modifier.width(6.dp)); Text("Radio") }
                    }
                }
                info.value?.biography?.let { bio ->
                    val clean = bio.replace(Regex("<[^>]+>"), "").trim()
                    if (clean.isNotBlank()) item {
                        Text(
                            clean,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(16.dp, 4.dp),
                        )
                        TextButton({ bioExpanded = !bioExpanded }, Modifier.padding(start = 8.dp)) { Text(if (bioExpanded) "Less" else "More") }
                    }
                }
                if (topSongs.isNotEmpty()) {
                    item { SectionHeader(if (top.value.isNullOrEmpty()) "Songs" else "Top songs") }
                    items(minOf(10, topSongs.size)) { i -> SongRow(topSongs[i], onClick = { app.player.play(topSongs, i) }, active = st.current?.id == topSongs[i].id) }
                }
                if (ownAlbums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(ownAlbums.chunked(2)) { pair ->
                        Row(Modifier.padding(horizontal = 12.dp)) {
                            pair.forEach { AlbumCard(it, Modifier.weight(1f), width = null) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                if (appearsOn.isNotEmpty()) {
                    item { SectionHeader("Appears on") }
                    item { com.westtiwan.tarang.ui.AlbumRow(appearsOn) }
                }
                val similar = info.value?.similarArtist.orEmpty()
                if (similar.isNotEmpty()) {
                    item { SectionHeader("Similar artists") }
                    item { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) { items(similar, key = { it.id }) { ArtistCircle(it) } } }
                }
            }
        }
    }
    if (editImage) artist.value?.let { ArtistImageDialog(it, info.value) { editImage = false } }
}

@Composable
fun PlaylistScreen(id: String, preview: Playlist?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val st by app.player.state.collectAsState()
    val playlist = rememberLoad("playlist:$id") { app.api?.getPlaylist(id) ?: error("Not connected") }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackBar {
            IconButton({ rename = true }) { Icon(Icons.Rounded.Edit, "Rename") }
            IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
        }
        LoadContent(playlist) { pl ->
            val songs = pl.entry
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Header(pl.coverArt ?: preview?.coverArt, pl.name, pl.owner, "${songs.size} songs • ${formatDuration(pl.duration * 1000L)}", Icons.AutoMirrored.Rounded.PlaylistAdd)
                    pl.comment?.takeIf { it.isNotBlank() && !it.startsWith("Smart playlist from Tarang") }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp))
                    }
                }
                item { PlayButtons(songs) { IconButton({ addAll = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to another playlist") } } }
                items(songs.size, key = { "$it-${songs[it].id}" }) { i ->
                    SongRow(
                        songs[i], onClick = { app.player.play(songs, i) }, active = st.current?.id == songs[i].id,
                        extraMenu = { close ->
                            DropdownMenuItem({ Text("Remove from playlist") }, {
                                close()
                                app.launch {
                                    runCatching { app.api?.updatePlaylist(id, removeIndexes = listOf(i)) }
                                    MemCache.invalidate("playlist:$id")
                                    playlist.reload()
                                }
                            }, leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) })
                        },
                    )
                }
            }
        }
    }
    if (rename) {
        var name by remember { mutableStateOf(playlist.value?.name ?: "") }
        var comment by remember { mutableStateOf(playlist.value?.comment ?: "") }
        var public by remember { mutableStateOf(playlist.value?.public ?: false) }
        AlertDialog(
            onDismissRequest = { rename = false },
            confirmButton = {
                TextButton({
                    rename = false
                    app.launch { runCatching { app.api?.updatePlaylist(id, name = name, comment = comment, public = public) }; MemCache.invalidate("playlist"); playlist.reload() }
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ rename = false }) { Text("Cancel") } },
            title = { Text("Edit playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(comment, { comment = it }, label = { Text("Description") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(public, { public = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Public")
                    }
                }
            },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        confirmButton = {
            TextButton({
                confirmDelete = false
                app.launch { runCatching { app.api?.deletePlaylist(id) }; MemCache.invalidate("playlists") }
                nav.back()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        title = { Text("Delete playlist?") },
        text = { Text("This removes the playlist from the server for all clients.") },
    )
    if (addAll) AddToPlaylistDialog(playlist.value?.entry.orEmpty()) { addAll = false }
}
