package com.westtiwan.tarang.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplayGain(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
    val baseGain: Double? = null,
)

@Serializable
data class NamedItem(val id: String? = null, val name: String = "")

@Serializable
data class Song(
    val id: String,
    val parent: String? = null,
    val isDir: Boolean = false,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val samplingRate: Int? = null,
    val channelCount: Int? = null,
    val path: String? = null,
    val playCount: Long? = null,
    val discNumber: Int? = null,
    val created: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val played: String? = null,
    val bpm: Int? = null,
    val comment: String? = null,
    val musicBrainzId: String? = null,
    val replayGain: ReplayGain? = null,
    val artists: List<NamedItem> = emptyList(),
    val albumArtists: List<NamedItem> = emptyList(),
    val displayAlbumArtist: String? = null,
    val genres: List<NamedItem> = emptyList(),
    /** Set locally for internet radio items. */
    val streamUrlOverride: String? = null,
) {
    val durationMs: Long get() = (duration ?: 0) * 1000L
    val isStarred: Boolean get() = starred != null
    val isLossless: Boolean
        get() = suffix?.lowercase() in setOf("flac", "alac", "wav", "aiff", "aif", "ape", "wv", "dsf", "dff")
    val isHiRes: Boolean get() = (bitDepth ?: 16) > 16 || (samplingRate ?: 44100) > 48000
    val qualityLabel: String
        get() = buildString {
            suffix?.let { append(it.uppercase()) }
            if (bitDepth != null && samplingRate != null && bitDepth > 0) {
                append(" ${bitDepth}/${formatKhz(samplingRate)}")
            } else if (bitRate != null && bitRate > 0) {
                append(" ${bitRate}k")
            }
        }.trim()
}

fun formatKhz(hz: Int): String {
    val k = hz / 1000.0
    return if (k == k.toLong().toDouble()) "${k.toLong()}" else ((k * 10).toLong() / 10.0).toString()
}

@Serializable
data class Album(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Long? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val song: List<Song> = emptyList(),
    val genres: List<NamedItem> = emptyList(),
    val artists: List<NamedItem> = emptyList(),
    val isCompilation: Boolean? = null,
) {
    val isStarred: Boolean get() = starred != null
}

@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val artistImageUrl: String? = null,
    val album: List<Album> = emptyList(),
) {
    val isStarred: Boolean get() = starred != null
}

@Serializable
data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())

@Serializable
data class ArtistsResponse(val index: List<ArtistIndex> = emptyList())

@Serializable
data class ArtistInfo(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<Artist> = emptyList(),
)

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class Genre(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Starred2(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class StructuredLine(val start: Long? = null, val value: String = "")

@Serializable
data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Long = 0,
    val synced: Boolean = false,
    val line: List<StructuredLine> = emptyList(),
)

@Serializable
data class LyricsList(val structuredLyrics: List<StructuredLyrics> = emptyList())

@Serializable
data class LegacyLyrics(val artist: String? = null, val title: String? = null, val value: String? = null)

@Serializable
data class PlayQueue(
    val current: String? = null,
    val position: Long = 0,
    val changedBy: String? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class RadioStation(
    val id: String,
    val name: String = "",
    val streamUrl: String = "",
    val homePageUrl: String? = null,
)

@Serializable
data class MusicFolder(val id: String, val name: String = "")

@Serializable
data class Directory(
    val id: String,
    val name: String = "",
    val parent: String? = null,
    val child: List<Song> = emptyList(),
)

@Serializable
data class OpenSubsonicExtension(val name: String, val versions: List<Int> = emptyList())

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val legacyAuth: Boolean = false,
)

@Serializable
enum class RepeatMode { OFF, ALL, ONE }

@Serializable
enum class ReplayGainMode { OFF, TRACK, ALBUM, AUTO }

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
data class AppSettings(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    /** 0 means original quality. */
    val wifiMaxBitrate: Int = 0,
    val mobileMaxBitrate: Int = 0,
    /** "raw" streams the original file, otherwise a server transcoding format like opus or mp3. */
    val wifiFormat: String = "raw",
    val mobileFormat: String = "raw",
    val downloadFormat: String = "raw",
    val downloadMaxBitrate: Int = 0,
    val cacheSizeMb: Int = 4096,
    val scrobble: Boolean = true,
    val listenBrainzToken: String = "",
    val lyricsSources: List<String> = listOf("server", "lrclib"),
    val showTranslation: Boolean = false,
    /** Turn translation on automatically for songs that are not in the target language. */
    val autoTranslateForeign: Boolean = true,
    val showRomanized: Boolean = true,
    val translateTarget: String = "en",
    /** google, gemini, deepl, openai, libre or device. */
    val translateEngine: String = "google",
    val geminiKey: String = "",
    val geminiModel: String = "gemini-flash-latest",
    val deeplKey: String = "",
    val openAiUrl: String = "",
    val openAiKey: String = "",
    val openAiModel: String = "",
    val libreTranslateUrl: String = "",
    val libreTranslateKey: String = "",
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    val replayGainPreampDb: Double = 0.0,
    val bitPerfect: Boolean = true,
    val gapless: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    /** Theme the player with colours from the album art. */
    val albumArtColors: Boolean = true,
    /** Use album art colours for the whole app instead of the wallpaper. */
    val albumArtEverywhere: Boolean = false,
    val syncPlayQueue: Boolean = true,
    val syncEqToServer: Boolean = true,
    val offlineMode: Boolean = false,
    val lyricsFontScale: Float = 1f,
    val autoUpdate: Boolean = true,
    val updateRepo: String = "West-Tiwan/Tarang",
    /** Read-only GitHub token, only needed while the repo is private. */
    val githubToken: String = "",
    /** Last version the user has seen, used to show what's new after an update. */
    val lastSeenVersion: String = "",
)

@Serializable
data class SubsonicError(val code: Int = 0, val message: String = "")

class SubsonicException(val code: Int, message: String) : Exception(message)

@Serializable
enum class AlbumListType(val api: String, val label: String) {
    @SerialName("newest") NEWEST("newest", "Recently added"),
    @SerialName("recent") RECENT("recent", "Recently played"),
    @SerialName("frequent") FREQUENT("frequent", "Most played"),
    @SerialName("random") RANDOM("random", "Random"),
    @SerialName("highest") HIGHEST("highest", "Top rated"),
    @SerialName("starred") STARRED("starred", "Favourites"),
    @SerialName("alphabeticalByName") BY_NAME("alphabeticalByName", "A to Z"),
    @SerialName("alphabeticalByArtist") BY_ARTIST("alphabeticalByArtist", "By artist"),
    @SerialName("byYear") BY_YEAR("byYear", "By year"),
    @SerialName("byGenre") BY_GENRE("byGenre", "By genre"),
}
