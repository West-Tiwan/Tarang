package com.westtiwan.tarang.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import com.westtiwan.tarang.data.Album
import com.westtiwan.tarang.data.Artist
import com.westtiwan.tarang.data.Playlist
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.download.DownloadStatus
import com.westtiwan.tarang.sync.SettingsSync

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "${((b * 10) shr 30) / 10.0} GB"
    b >= 1L shl 20 -> "${b shr 20} MB"
    b >= 1L shl 10 -> "${b shr 10} KB"
    else -> "$b B"
}

@Composable
fun CoverArt(
    coverArt: String?,
    modifier: Modifier = Modifier,
    size: Int = 300,
    shape: Shape = RoundedCornerShape(8.dp),
    icon: ImageVector = Icons.Rounded.MusicNote,
) {
    val app = LocalApp.current
    val epoch by app.coverEpoch.collectAsState()
    val url = remember(coverArt, size, epoch) { app.coverUrl(coverArt, size) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f))
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f)) },
            )
        }
    }
}

/** Artist picture, preferring an image the user picked over the server's. */
@Composable
fun ArtistImage(artistId: String?, coverArt: String?, modifier: Modifier = Modifier, size: Int = 300, shape: Shape = CircleShape) {
    val app = LocalApp.current
    val art by app.artistArt.state.collectAsState()
    val epoch by app.coverEpoch.collectAsState()
    val override = artistId?.let { art.images[it] }?.let { if (epoch > 0 && it.startsWith("http")) "$it#$epoch" else it }
    if (override == null) {
        CoverArt(coverArt, modifier, size = size, shape = shape, icon = Icons.Rounded.Person)
        return
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = override,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = { CoverArt(coverArt, Modifier.fillMaxSize(), size = size, shape = shape, icon = Icons.Rounded.Person) },
        )
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun QualityBadge(song: Song, modifier: Modifier = Modifier) {
    val label = song.qualityLabel
    if (label.isBlank()) return
    val hi = song.isHiRes && song.isLossless
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (hi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (hi) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    trackNumber: Int? = null,
    active: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val app = LocalApp.current
    val downloads by app.downloads.state.collectAsState()
    val dl = downloads.items[song.id]
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            CoverArt(song.coverArt, Modifier.size(52.dp), size = 120, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
        } else if (trackNumber != null) {
            Text(
                trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dl?.status == DownloadStatus.DONE) {
                    Icon(Icons.Rounded.DownloadDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                } else if (dl?.status == DownloadStatus.DOWNLOADING) {
                    CircularProgressIndicator(progress = { dl.progress }, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                }
                if (song.isStarred) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(song.artist, song.duration?.let { formatDuration(it * 1000L) }).joinToString(" • "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.isLossless && song.isHiRes) {
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(song)
                }
            }
        }
        trailing?.invoke()
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            SongMenu(song, menu, { menu = false }, extraMenu)
        }
    }
}

@Composable
fun SongMenu(song: Song, expanded: Boolean, onDismiss: () -> Unit, extra: (@Composable (close: () -> Unit) -> Unit)? = null) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var addToPlaylist by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val downloaded = app.downloads.isDownloaded(song.id)
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem({ Text("Play next") }, { app.player.playNext(listOf(song)); app.toast("Playing next"); onDismiss() }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) })
        DropdownMenuItem({ Text("Add to queue") }, { app.player.enqueue(listOf(song)); app.toast("Added to queue"); onDismiss() }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) })
        DropdownMenuItem({ Text("Add to playlist") }, { addToPlaylist = true }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) })
        DropdownMenuItem({ Text("Instant mix") }, {
            onDismiss()
            app.launch {
                val similar = runCatching { app.api?.getSimilarSongs(song.id) }.getOrNull().orEmpty()
                if (similar.isEmpty()) app.toast("No similar songs found") else app.player.play(listOf(song) + similar)
            }
        }, leadingIcon = { Icon(Icons.Rounded.Radio, null) })
        DropdownMenuItem(
            { Text(if (song.isStarred) "Remove favourite" else "Favourite") },
            { app.toggleStar(song); onDismiss() },
            leadingIcon = { Icon(if (song.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
        )
        DropdownMenuItem(
            { Text(if (downloaded) "Remove download" else "Download") },
            { if (downloaded) app.downloads.remove(listOf(song.id)) else app.downloads.enqueue(listOf(song)); onDismiss() },
            leadingIcon = { Icon(if (downloaded) Icons.Rounded.Delete else Icons.Rounded.CloudDownload, null) },
        )
        song.albumId?.let { id -> DropdownMenuItem({ Text("Go to album") }, { nav.push(Screen.AlbumDetail(id)); onDismiss() }, leadingIcon = { Icon(Icons.Rounded.Album, null) }) }
        song.artistId?.let { id -> DropdownMenuItem({ Text("Go to artist") }, { nav.push(Screen.ArtistDetail(id)); onDismiss() }, leadingIcon = { Icon(Icons.Rounded.Person, null) }) }
        DropdownMenuItem({ Text("Details") }, { info = true }, leadingIcon = { Icon(Icons.Rounded.Info, null) })
        DropdownMenuItem({ Text("Clear cached data") }, { clear = true }, leadingIcon = { Icon(Icons.Rounded.CleaningServices, null) })
        extra?.invoke(onDismiss)
    }
    if (addToPlaylist) AddToPlaylistDialog(listOf(song)) { addToPlaylist = false; onDismiss() }
    if (info) SongInfoDialog(song) { info = false; onDismiss() }
    if (clear) ClearCacheDialog(song) { clear = false; onDismiss() }
}

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val rows = listOfNotNull(
        "Title" to song.title,
        song.artist?.let { "Artist" to it },
        song.album?.let { "Album" to it },
        song.year?.let { "Year" to it.toString() },
        song.genre?.let { "Genre" to it },
        song.suffix?.let { "Format" to it.uppercase() + (song.contentType?.let { c -> " ($c)" } ?: "") },
        song.bitRate?.let { "Bitrate" to "$it kbps" },
        song.samplingRate?.let { "Sample rate" to "$it Hz" },
        song.bitDepth?.let { "Bit depth" to "$it bit" },
        song.channelCount?.let { "Channels" to it.toString() },
        song.size?.let { "Size" to formatBytes(it) },
        song.playCount?.let { "Plays" to it.toString() },
        song.replayGain?.trackGain?.let { "Track gain" to "$it dB" },
        song.replayGain?.albumGain?.let { "Album gain" to "$it dB" },
        song.bpm?.takeIf { it > 0 }?.let { "BPM" to it.toString() },
        song.path?.let { "Path" to it },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Details") },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val playlists = rememberLoad("playlists") { app.api?.getPlaylists().orEmpty() }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                app.launch {
                    runCatching { app.api?.createPlaylist(newName, songs.map { it.id }) }
                        .onSuccess { app.toast("Created $newName"); MemCache.invalidate("playlists") }
                        .onFailure { app.toast(it.message) }
                }
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Add to playlist") },
        text = {
            Column {
                OutlinedTextField(newName, { newName = it }, label = { Text("New playlist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                when (val s = playlists.state) {
                    is Load.Ok -> s.value.filterNot { SettingsSync.isSettingsPlaylist(it.name) }.take(30).forEach { pl ->
                        Text(
                            pl.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                app.launch {
                                    runCatching { app.api?.updatePlaylist(pl.id, add = songs.map { it.id }) }
                                        .onSuccess { app.toast("Added to ${pl.name}"); MemCache.invalidate("playlist:${pl.id}") }
                                        .onFailure { app.toast(it.message) }
                                }
                                onDismiss()
                            }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                    is Load.Loading -> LoadingDots()
                    is Load.Err -> Text(s.message)
                }
            }
        },
    )
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, width: Dp? = 150.dp) {
    val nav = LocalNav.current
    Column(
        (if (width != null) modifier.width(width) else modifier)
            .clip(RoundedCornerShape(24.dp))
            .clickable { nav.push(Screen.AlbumDetail(album.id, album)) }
            .padding(4.dp),
    ) {
        CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), size = 400, shape = CoverShape, icon = Icons.Rounded.Album)
        Spacer(Modifier.height(6.dp))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            listOfNotNull(album.artist, album.year?.toString()).joinToString(" • "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlbumRow(albums: List<Album>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(albums, key = { it.id }) { AlbumCard(it) }
    }
}

@Composable
fun ArtistCircle(artist: Artist, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    Column(
        modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).clickable { nav.push(Screen.ArtistDetail(artist.id, artist)) }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistImage(artist.id, artist.coverArt, Modifier.size(96.dp), size = 300, shape = ArtistShape)
        Spacer(Modifier.height(6.dp))
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ArtistRow(artist: Artist) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().clickable { nav.push(Screen.ArtistDetail(artist.id, artist)) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistImage(artist.id, artist.coverArt, Modifier.size(48.dp), size = 120, shape = ArtistShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.albumCount > 0) Text("${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (artist.isStarred) Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PlaylistRow(pl: Playlist) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().clickable { nav.push(Screen.PlaylistDetail(pl.id, pl)) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(pl.coverArt, Modifier.size(56.dp), size = 150, shape = RoundedCornerShape(16.dp), icon = Icons.AutoMirrored.Rounded.QueueMusic)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pl.songCount} songs • ${formatDuration(pl.duration * 1000L)}" + (pl.owner?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onAction) { Text(action) }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.LoadingIndicator() }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    androidx.compose.material3.LoadingIndicator(modifier.size(36.dp))
}

/** Renders a [Loader] state with loading and error handling. */
@Composable
fun <T> LoadContent(loader: Loader<T>, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        is Load.Loading -> Loading()
        is Load.Err -> CenteredMessage(s.message, action = "Retry", onAction = loader::reload)
        is Load.Ok -> content(s.value)
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            androidx.compose.material3.FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(options[i]) })
        }
    }
}

@Composable
fun rememberToastHost(): String? {
    val app = LocalApp.current
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        app.toast.filterNotNull().collectLatest { msg ->
            shown = msg
            app.toast(null)
            kotlinx.coroutines.delay(2500)
            shown = null
        }
    }
    return shown
}
