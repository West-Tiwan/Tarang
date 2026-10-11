package com.westtiwan.tarang.ui

import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.Album
import com.westtiwan.tarang.data.Artist
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.download.DownloadStatus

/** Builds browsable albums and artists out of downloaded songs, for offline use. */
object OfflineLibrary {
    fun songs(app: AppContainer): List<Song> =
        app.downloads.state.value.items.values.filter { it.status == DownloadStatus.DONE }.map { it.song }

    fun albums(app: AppContainer): List<Album> = songs(app).groupBy { it.albumId ?: it.album ?: "?" }.map { (id, list) ->
        val f = list.first()
        Album(
            id = f.albumId ?: id, name = f.album ?: "Unknown album", artist = f.displayAlbumArtist ?: f.artist, artistId = f.artistId,
            coverArt = f.coverArt, songCount = list.size, duration = list.sumOf { it.duration ?: 0 }, year = f.year,
            song = list.sortedWith(compareBy({ it.discNumber ?: 1 }, { it.track ?: 0 })),
        )
    }.sortedBy { it.name.lowercase() }

    fun artists(app: AppContainer): List<Artist> = songs(app).groupBy { it.artistId ?: it.artist ?: "?" }.map { (id, list) ->
        Artist(id = list.first().artistId ?: id, name = list.first().artist ?: "Unknown artist", coverArt = list.first().coverArt, albumCount = list.mapNotNull { it.albumId }.distinct().size)
    }.sortedBy { it.name.lowercase() }

    fun album(app: AppContainer, id: String): Album? = albums(app).firstOrNull { it.id == id }

    fun artist(app: AppContainer, id: String): Artist? {
        val a = artists(app).firstOrNull { it.id == id } ?: return null
        return a.copy(album = albums(app).filter { it.artistId == id })
    }

    fun search(app: AppContainer, q: String): List<Song> {
        val needle = q.lowercase()
        val pool = app.library.index.value.songs.ifEmpty { songs(app) }
        return pool.filter { s -> listOfNotNull(s.title, s.artist, s.album).any { it.lowercase().contains(needle) } }.take(200)
    }
}
