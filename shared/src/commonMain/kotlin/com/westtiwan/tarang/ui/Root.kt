package com.westtiwan.tarang.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.ui.screens.AlbumListScreen
import com.westtiwan.tarang.ui.screens.AlbumScreen
import com.westtiwan.tarang.ui.screens.AllSongsScreen
import com.westtiwan.tarang.ui.screens.ArtistScreen
import com.westtiwan.tarang.ui.screens.DownloadsScreen
import com.westtiwan.tarang.ui.screens.EqualizerScreen
import com.westtiwan.tarang.ui.screens.FolderScreen
import com.westtiwan.tarang.ui.screens.GenreScreen
import com.westtiwan.tarang.ui.screens.HomeScreen
import com.westtiwan.tarang.ui.screens.LibraryScreen
import com.westtiwan.tarang.ui.screens.LoginScreen
import com.westtiwan.tarang.ui.screens.MiniPlayer
import com.westtiwan.tarang.ui.screens.NowPlaying
import com.westtiwan.tarang.ui.screens.PlaylistScreen
import com.westtiwan.tarang.ui.screens.QueuePanel
import com.westtiwan.tarang.ui.screens.RadioScreen
import com.westtiwan.tarang.ui.screens.SearchScreen
import com.westtiwan.tarang.ui.screens.ServersScreen
import com.westtiwan.tarang.ui.screens.SettingsScreen
import com.westtiwan.tarang.ui.screens.SmartEditScreen
import com.westtiwan.tarang.ui.screens.SmartScreen

private fun Tab.icon() = when (this) {
    Tab.HOME -> Icons.Rounded.Home
    Tab.LIBRARY -> Icons.Rounded.LibraryMusic
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun Root() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val server by app.activeServer.collectAsState()
    val player by app.player.state.collectAsState()
    val toast = rememberToastHost()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (server == null) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { LoginScreen() }
            return@Surface
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 840.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    if (wide) {
                        NavigationRail(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                            Tab.entries.forEach { t ->
                                NavigationRailItem(selected = nav.tab == t, onClick = { nav.select(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label) })
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.let { if (wide) it else it })) {
                        AnimatedContent(nav.current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                            ScreenContent(screen)
                        }
                    }
                    if (wide && player.queue.isNotEmpty()) {
                        VerticalDivider()
                        Box(Modifier.width(340.dp).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing)) { QueuePanel(compact = true) }
                    }
                }
                if (player.current != null) MiniPlayer()
                if (!wide) {
                    ShortNavigationBar {
                        Tab.entries.forEach { t ->
                            ShortNavigationBarItem(selected = nav.tab == t, onClick = { nav.select(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label) })
                        }
                    }
                }
            }
            AnimatedVisibility(
                nav.nowPlayingOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NowPlaying()
            }
            if (toast != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp, start = 24.dp, end = 24.dp),
                ) {
                    Text(toast, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
fun ScreenContent(screen: Screen) {
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Library -> LibraryScreen()
            Screen.Search -> SearchScreen()
            Screen.Settings -> SettingsScreen()
            is Screen.AlbumDetail -> AlbumScreen(screen.id, screen.preview)
            is Screen.ArtistDetail -> ArtistScreen(screen.id, screen.preview)
            is Screen.PlaylistDetail -> PlaylistScreen(screen.id, screen.preview)
            is Screen.SmartDetail -> SmartScreen(screen.id)
            is Screen.SmartEdit -> SmartEditScreen(screen.playlist)
            is Screen.GenreDetail -> GenreScreen(screen.genre)
            is Screen.AlbumList -> AlbumListScreen(screen.type, screen.genre, screen.title)
            is Screen.Folder -> FolderScreen(screen.id, screen.name)
            Screen.Downloads -> DownloadsScreen()
            Screen.Equalizer -> EqualizerScreen()
            Screen.Servers -> ServersScreen()
            Screen.Radio -> RadioScreen()
            Screen.AllSongs -> AllSongsScreen()
        }
    }
}
