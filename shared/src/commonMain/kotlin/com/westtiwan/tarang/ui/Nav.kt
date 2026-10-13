package com.westtiwan.tarang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.Album
import com.westtiwan.tarang.data.AlbumListType
import com.westtiwan.tarang.data.Artist
import com.westtiwan.tarang.data.Playlist
import com.westtiwan.tarang.smart.SmartPlaylist

enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library"), SEARCH("Search"), SETTINGS("Settings") }

sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data object Search : Screen
    data object Settings : Screen
    data class AlbumDetail(val id: String, val preview: Album? = null) : Screen
    data class ArtistDetail(val id: String, val preview: Artist? = null) : Screen
    data class PlaylistDetail(val id: String, val preview: Playlist? = null) : Screen
    data class SmartDetail(val id: String) : Screen
    data class SmartEdit(val playlist: SmartPlaylist?) : Screen
    data class GenreDetail(val genre: String) : Screen
    data class AlbumList(val type: AlbumListType, val genre: String? = null, val title: String? = null) : Screen
    data class Folder(val id: String?, val name: String) : Screen
    data object Downloads : Screen
    data object Equalizer : Screen
    data object Servers : Screen
    data object Radio : Screen
    data object AllSongs : Screen
}

class Navigator {
    var tab by mutableStateOf(Tab.HOME)
        private set
    private val stacks = Tab.entries.associateWith { t -> mutableStateListOf(root(t)) }
    var nowPlayingOpen by mutableStateOf(false)
    var nowPlayingPage by mutableStateOf(NowPlayingPage.ART)

    val stack get() = stacks.getValue(tab)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = nowPlayingOpen || stack.size > 1

    private fun root(t: Tab): Screen = when (t) {
        Tab.HOME -> Screen.Home
        Tab.LIBRARY -> Screen.Library
        Tab.SEARCH -> Screen.Search
        Tab.SETTINGS -> Screen.Settings
    }

    fun select(t: Tab) {
        if (t == tab) {
            val s = stacks.getValue(t)
            while (s.size > 1) s.removeAt(s.lastIndex)
        }
        tab = t
    }

    fun push(screen: Screen) {
        nowPlayingOpen = false
        if (stack.last() != screen) stack.add(screen)
    }

    fun replace(screen: Screen) {
        if (stack.size > 1) stack[stack.lastIndex] = screen else stack.add(screen)
    }

    fun back(): Boolean {
        if (nowPlayingOpen) {
            if (nowPlayingPage != NowPlayingPage.ART) nowPlayingPage = NowPlayingPage.ART else nowPlayingOpen = false
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (tab != Tab.HOME) {
            tab = Tab.HOME
            return true
        }
        return false
    }
}

enum class NowPlayingPage { ART, LYRICS, QUEUE }

val LocalApp = staticCompositionLocalOf<AppContainer> { error("No app") }
val LocalNav = staticCompositionLocalOf<Navigator> { error("No navigator") }

/** Hook for platform specific screens, for example Android audio output details. */
val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> { PlatformUi() }

open class PlatformUi {
    /** Extra rows for the audio section of settings, for example USB DAC controls. */
    open val audioSettings: (@Composable () -> Unit)? = null
    open val isDesktop: Boolean = false
    /** Lets the user pick an image file. Returns its bytes, or null when cancelled or unsupported. */
    open suspend fun pickImage(): ByteArray? = null
    open val supportsImagePicking: Boolean = false
}
