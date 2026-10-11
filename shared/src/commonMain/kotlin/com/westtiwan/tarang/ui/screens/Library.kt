package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.Album
import com.westtiwan.tarang.data.AlbumListType
import com.westtiwan.tarang.data.RadioStation
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.sync.SettingsSync
import com.westtiwan.tarang.ui.AlbumCard
import com.westtiwan.tarang.ui.ArtistRow
import com.westtiwan.tarang.ui.CenteredMessage
import com.westtiwan.tarang.ui.ChoiceRow
import com.westtiwan.tarang.ui.LoadContent
import com.westtiwan.tarang.ui.Loading
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.OfflineLibrary
import com.westtiwan.tarang.ui.PlaylistRow
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.SectionHeader
import com.westtiwan.tarang.ui.SongRow
import com.westtiwan.tarang.ui.rememberLoad

private val libraryTabs = listOf("Albums", "Artists", "Songs", "Playlists", "Genres", "Folders")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                IconButton({ nav.push(Screen.Downloads) }) { Icon(Icons.Rounded.Download, "Downloads") }
            },
        )
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            libraryTabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> AlbumsTab()
            1 -> ArtistsTab()
            2 -> AllSongsScreen(showTopBar = false)
            3 -> PlaylistsTab()
            4 -> GenresTab()
            5 -> FolderScreen(null, "Folders", showTopBar = false)
        }
    }
}

private val albumSorts = listOf(AlbumListType.NEWEST, AlbumListType.BY_NAME, AlbumListType.BY_ARTIST, AlbumListType.FREQUENT, AlbumListType.RECENT, AlbumListType.STARRED, AlbumListType.RANDOM)

@Composable
private fun AlbumsTab() {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    Column {
        ChoiceRow(albumSorts.map { it.label }, sort) { sort = it }
        PagedAlbumGrid(albumSorts[sort], null)
    }
}

@Composable
fun PagedAlbumGrid(type: AlbumListType, genre: String?) {
    val app = LocalApp.current
    val albums = remember(type, genre) { mutableStateListOf<Album>() }
    var loading by remember(type, genre) { mutableStateOf(false) }
    var end by remember(type, genre) { mutableStateOf(false) }
    var error by remember(type, genre) { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    if (app.offline) {
        val off = remember { OfflineLibrary.albums(app) }
        AlbumGrid(off)
        return
    }
    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        runCatching { app.api?.getAlbumList(type, 60, albums.size, genre = genre).orEmpty() }
            .onSuccess { page -> albums += page.filter { a -> albums.none { it.id == a.id } }; if (page.size < 60) end = true }
            .onFailure { error = it.message; end = true }
        loading = false
    }
    LaunchedEffect(type, genre) { loadMore() }
    LaunchedEffect(gridState, type, genre) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last > albums.size - 20) loadMore()
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), state = gridState, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
        items(albums, key = { it.id }) { AlbumCard(it, width = null) }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
        if (error != null && albums.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { CenteredMessage(error!!) }
    }
}

@Composable
fun AlbumGrid(albums: List<Album>) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
        items(albums, key = { it.id }) { AlbumCard(it, width = null) }
    }
}

@Composable
private fun ArtistsTab() {
    val app = LocalApp.current
    var filter by remember { mutableStateOf("") }
    val artists = rememberLoad("artists:${app.api?.server?.id}") {
        if (app.offline) OfflineLibrary.artists(app) else app.api?.getArtists().orEmpty()
    }
    LoadContent(artists) { list ->
        val shown = remember(list, filter) { if (filter.isBlank()) list else list.filter { it.name.contains(filter, ignoreCase = true) } }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                OutlinedTextField(filter, { filter = it }, placeholder = { Text("Filter ${list.size} artists") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(shown, key = { it.id }) { ArtistRow(it) }
        }
    }
}

@Composable
private fun PlaylistsTab() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val smart by app.smartStore.state.collectAsState()
    val playlists = rememberLoad("playlists") { app.api?.getPlaylists().orEmpty() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Smart playlists")
                Spacer(Modifier.weight(1f))
                IconButton({ nav.push(Screen.SmartEdit(null)) }, Modifier.padding(end = 8.dp)) { Icon(Icons.Rounded.Add, "New smart playlist") }
            }
        }
        items(smart.playlists, key = { "smart-" + it.id }) { pl ->
            ListItem(
                headlineContent = { Text(pl.name) },
                supportingContent = { Text("${pl.rules.size} rules" + if (pl.syncToServer) " • synced to server" else "") },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { nav.push(Screen.SmartDetail(pl.id)) },
            )
        }
        item { SectionHeader("Playlists") }
        when (val s = playlists.state) {
            is com.westtiwan.tarang.ui.Load.Ok -> items(s.value.filterNot { SettingsSync.isSettingsPlaylist(it.name) }, key = { it.id }) { PlaylistRow(it) }
            is com.westtiwan.tarang.ui.Load.Loading -> item { Loading() }
            is com.westtiwan.tarang.ui.Load.Err -> item { CenteredMessage(s.message, action = "Retry", onAction = playlists::reload) }
        }
    }
}

@Composable
private fun GenresTab() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val genres = rememberLoad("genres") { app.api?.getGenres().orEmpty().sortedByDescending { it.songCount } }
    LoadContent(genres) { list ->
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.value }) { g ->
                ListItem(
                    headlineContent = { Text(g.value) },
                    supportingContent = { Text("${g.albumCount} albums • ${g.songCount} songs") },
                    modifier = Modifier.clickable { nav.push(Screen.GenreDetail(g.value)) },
                )
            }
        }
    }
}

private val songSorts = listOf("Title", "Artist", "Album", "Recently added", "Most played", "Year")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSongsScreen(showTopBar: Boolean = true) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val index by app.library.index.collectAsState()
    val progress by app.library.progress.collectAsState()
    var filter by remember { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val songs: List<Song> = remember(index, filter, sort) {
        val base = index.songs.ifEmpty { OfflineLibrary.songs(app) }
        val f = if (filter.isBlank()) base else base.filter { s -> listOfNotNull(s.title, s.artist, s.album).any { it.contains(filter, ignoreCase = true) } }
        when (sort) {
            1 -> f.sortedWith(compareBy({ it.artist?.lowercase() }, { it.album?.lowercase() }, { it.track }))
            2 -> f.sortedWith(compareBy({ it.album?.lowercase() }, { it.discNumber }, { it.track }))
            3 -> f.sortedByDescending { it.created ?: "" }
            4 -> f.sortedByDescending { it.playCount ?: 0 }
            5 -> f.sortedByDescending { it.year ?: 0 }
            else -> f.sortedBy { it.title.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) TopAppBar(title = { Text("Songs") }, navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(filter, { filter = it }, placeholder = { Text("Filter ${index.songs.size} songs") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton({ if (songs.isNotEmpty()) app.player.play(songs.shuffled()) }) { Icon(Icons.Rounded.Shuffle, "Shuffle") }
        }
        ChoiceRow(songSorts, sort) { sort = it }
        if (progress.running) Text("Indexing library: ${progress.fetched} songs", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 4.dp))
        if (songs.isEmpty() && !progress.running) CenteredMessage("Library index is empty", action = "Sync now") {
            app.launch { app.api?.let { app.library.sync(it, com.westtiwan.tarang.platform.nowMillis()) } }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(songs.size, key = { songs[it].id }) { i ->
                SongRow(songs[i], onClick = { app.player.play(songs, i) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(id: String?, name: String, showTopBar: Boolean = true) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val content = rememberLoad("folder:$id") {
        val api = app.api ?: error("Not connected")
        if (id == null) {
            val folders = api.getMusicFolders()
            if (folders.size == 1) Pair(api.getIndexes(folders.first().id).map { Song(id = it.id, title = it.name, isDir = true) }, emptyList<Song>())
            else Pair(folders.map { Song(id = "mf:" + it.id, title = it.name, isDir = true) }, emptyList())
        } else if (id.startsWith("mf:")) {
            Pair(api.getIndexes(id.removePrefix("mf:")).map { Song(id = it.id, title = it.name, isDir = true) }, emptyList())
        } else {
            val dir = api.getMusicDirectory(id)
            Pair(dir.child.filter { it.isDir }, dir.child.filterNot { it.isDir })
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) TopAppBar(title = { Text(name) }, navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } })
        LoadContent(content) { (dirs, files) ->
            LazyColumn(Modifier.fillMaxSize()) {
                items(dirs, key = { "d" + it.id }) { d ->
                    ListItem(
                        headlineContent = { Text(d.title) },
                        leadingContent = { Icon(Icons.Rounded.Folder, null) },
                        modifier = Modifier.clickable { nav.push(Screen.Folder(d.id, d.title)) },
                    )
                }
                items(files.size, key = { "f" + files[it].id }) { i -> SongRow(files[i], onClick = { app.player.play(files, i) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val stations = rememberLoad("radio") { app.api?.getRadioStations().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Internet radio") }, navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } })
        LoadContent(stations) { list ->
            if (list.isEmpty()) CenteredMessage("No radio stations. Add them in the Navidrome web UI.")
            LazyColumn {
                items(list, key = { it.id }) { st: RadioStation ->
                    ListItem(
                        headlineContent = { Text(st.name) },
                        supportingContent = { Text(st.streamUrl, maxLines = 1) },
                        leadingContent = { Icon(Icons.Rounded.Radio, null) },
                        modifier = Modifier.clickable {
                            app.player.play(listOf(Song(id = "radio:" + st.id, title = st.name, artist = "Internet radio", streamUrlOverride = st.streamUrl)))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(type: AlbumListType, genre: String?, title: String?) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title ?: genre ?: type.label) }, navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } })
        PagedAlbumGrid(type, genre)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreScreen(genre: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val songs = rememberLoad("genre-songs:$genre") { app.api?.getSongsByGenre(genre, 500).orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(genre) },
            navigationIcon = { IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
            actions = { IconButton({ songs.value?.let { if (it.isNotEmpty()) app.player.play(it.shuffled()) } }) { Icon(Icons.Rounded.Shuffle, "Shuffle") } },
        )
        ChoiceRow(listOf("Albums", "Songs"), tab) { tab = it }
        if (tab == 0) PagedAlbumGrid(AlbumListType.BY_GENRE, genre)
        else LoadContent(songs) { list ->
            LazyColumn { items(list.size, key = { list[it].id }) { i -> SongRow(list[i], onClick = { app.player.play(list, i) }) } }
        }
    }
}
