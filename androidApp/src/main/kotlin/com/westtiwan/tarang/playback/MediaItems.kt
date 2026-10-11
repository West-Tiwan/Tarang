package com.westtiwan.tarang.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.data.json
import java.util.concurrent.ConcurrentHashMap

object SongRegistry {
    private val songs = ConcurrentHashMap<String, Song>()
    fun put(list: Collection<Song>) = list.forEach { songs[it.id] = it }
    fun put(song: Song) { songs[song.id] = song }
    fun get(id: String): Song? = songs[id]
}

private const val EXTRA_SONG = "tarang.song"

fun Song.toMediaItem(app: AppContainer): MediaItem {
    SongRegistry.put(this)
    val uri = app.mediaUri(this)
    val extras = Bundle().apply { putString(EXTRA_SONG, json.encodeToString(Song.serializer(), this@toMediaItem)) }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setDisplayTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(displayAlbumArtist ?: artist)
        .setArtworkUri(app.coverUrl(coverArt, 800)?.let(Uri::parse))
        .setTrackNumber(track)
        .setDiscNumber(discNumber)
        .setReleaseYear(year)
        .setGenre(genre)
        .setDurationMs(duration?.let { it * 1000L })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setUserRating(HeartRating(isStarred))
        .setExtras(extras)
        .build()
    val b = MediaItem.Builder().setMediaId("song:$id").setMediaMetadata(meta)
    if (uri != null) {
        b.setUri(uri)
        if (!uri.startsWith("file:") && streamUrlOverride == null) {
            val s = app.settings.value
            b.setCustomCacheKey("$id:${s.wifiFormat}:${s.wifiMaxBitrate}:${s.mobileFormat}:${s.mobileMaxBitrate}")
        }
    }
    return b.build()
}

fun MediaItem.toSong(): Song? {
    mediaMetadata.extras?.getString(EXTRA_SONG)?.let { return runCatching { json.decodeFromString(Song.serializer(), it) }.getOrNull() }
    val id = mediaId.removePrefix("song:")
    return SongRegistry.get(id)
}

fun browsable(id: String, title: String, subtitle: String? = null, artwork: Uri? = null, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, playable: Boolean = false): MediaItem =
    MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setArtworkUri(artwork)
            .setIsBrowsable(true).setIsPlayable(playable).setMediaType(type).build(),
    ).build()
