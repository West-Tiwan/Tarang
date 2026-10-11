package com.westtiwan.tarang.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.AlbumListType
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.download.DownloadStatus
import com.westtiwan.tarang.sync.SettingsSync

/** Browse tree for Android Auto, Wear and other media browsers. */
class LibraryTree(private val app: AppContainer) {
    companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val NEWEST = "newest"
        const val FREQUENT = "frequent"
        const val PLAYLISTS = "playlists"
        const val MIXES = "mixes"
        const val DOWNLOADS = "downloads"
        const val FAVOURITES = "favourites"
        const val SHUFFLE = "shuffle"
    }

    val root: MediaItem = browsable(ROOT, "Tarang")

    private fun cover(id: String?) = app.coverUrl(id, 400)?.let(Uri::parse)

    suspend fun children(parentId: String): List<MediaItem> {
        val api = if (app.offline) null else app.api
        return when {
            parentId == ROOT -> listOf(
                browsable(SHUFFLE, "Shuffle all", playable = true, type = MediaMetadata.MEDIA_TYPE_PLAYLIST),
                browsable(RECENT, "Recently played", type = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsable(NEWEST, "Recently added", type = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsable(MIXES, "Smart mixes", type = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(PLAYLISTS, "Playlists", type = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(FAVOURITES, "Favourites", playable = true, type = MediaMetadata.MEDIA_TYPE_PLAYLIST),
                browsable(DOWNLOADS, "Downloads", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            ).filter { api != null || it.mediaId == DOWNLOADS || it.mediaId == SHUFFLE || it.mediaId == MIXES }
            parentId == RECENT || parentId == NEWEST || parentId == FREQUENT -> {
                val type = when (parentId) { RECENT -> AlbumListType.RECENT; NEWEST -> AlbumListType.NEWEST; else -> AlbumListType.FREQUENT }
                api?.getAlbumList(type, 40).orEmpty().map {
                    browsable("album:${it.id}", it.name, it.artist, cover(it.coverArt), MediaMetadata.MEDIA_TYPE_ALBUM, playable = true)
                }
            }
            parentId == PLAYLISTS -> api?.getPlaylists().orEmpty().filterNot { SettingsSync.isSettingsPlaylist(it.name) }.map {
                browsable("playlist:${it.id}", it.name, "${it.songCount} songs", cover(it.coverArt), MediaMetadata.MEDIA_TYPE_PLAYLIST, playable = true)
            }
            parentId == MIXES -> app.smartStore.value.playlists.map {
                browsable("smart:${it.id}", it.name, null, null, MediaMetadata.MEDIA_TYPE_PLAYLIST, playable = true)
            }
            parentId == DOWNLOADS -> downloadedSongs().take(500).map { it.toMediaItem(app) }
            else -> songsFor(parentId).map { it.toMediaItem(app) }
        }
    }

    private fun downloadedSongs(): List<Song> =
        app.downloads.state.value.items.values.filter { it.status == DownloadStatus.DONE }.sortedByDescending { it.addedAt }.map { it.song }

    /** Expands a browsable id to the songs it contains. */
    suspend fun songsFor(id: String): List<Song> {
        val api = app.api
        val songs = when {
            id == SHUFFLE -> app.library.index.value.songs.shuffled().take(300).ifEmpty { if (app.offline) downloadedSongs().shuffled() else api?.getRandomSongs(200).orEmpty() }
            id == FAVOURITES -> api?.getStarred()?.song.orEmpty()
            id == DOWNLOADS -> downloadedSongs()
            id.startsWith("album:") -> api?.getAlbum(id.removePrefix("album:"))?.song.orEmpty()
            id.startsWith("playlist:") -> api?.getPlaylist(id.removePrefix("playlist:"))?.entry.orEmpty()
            id.startsWith("smart:") -> app.smartStore.value.playlists.firstOrNull { it.id == id.removePrefix("smart:") }?.let { app.evaluateSmart(it) }.orEmpty()
            id.startsWith("artist:") -> api?.getArtist(id.removePrefix("artist:"))?.album.orEmpty().flatMap { api?.getAlbum(it.id)?.song.orEmpty() }
            else -> emptyList()
        }
        SongRegistry.put(songs)
        return songs
    }

    suspend fun resolveSong(id: String): Song? {
        SongRegistry.get(id)?.let { return it }
        app.library.index.value.songs.firstOrNull { it.id == id }?.let { return it }
        app.downloads.state.value.items[id]?.song?.let { return it }
        return runCatching { app.api?.getSong(id) }.getOrNull()
    }

    suspend fun search(query: String): List<MediaItem> {
        val songs = runCatching { app.api?.search(query, 0, 0, 50)?.song }.getOrNull()
            ?: app.library.index.value.songs.filter { it.title.contains(query, true) || (it.artist?.contains(query, true) ?: false) }.take(50)
        return songs.map { it.toMediaItem(app) }
    }
}
