package com.westtiwan.tarang.data

import com.westtiwan.tarang.platform.md5Hex
import com.westtiwan.tarang.platform.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.parameters
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Client for the Subsonic API with the OpenSubsonic extensions that Navidrome implements.
 */
class SubsonicApi(val server: ServerProfile, private val http: HttpClient) {
    private val base = server.url.trimEnd('/')
    private val clientName = "Tarang"
    var extensions: Set<String> = emptySet()
        private set

    // A stable salt keeps cover art and stream URLs identical between calls so image and audio caches hit.
    private val authQuery: String by lazy {
        val user = urlEncode(server.username)
        if (server.legacyAuth) {
            val hex = server.password.encodeToByteArray().joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }
            "u=$user&p=enc:$hex&v=1.16.1&c=$clientName&f=json"
        } else {
            val salt = md5Hex(server.id + server.username + server.url).take(12)
            val token = md5Hex(server.password + salt)
            "u=$user&t=$token&s=$salt&v=1.16.1&c=$clientName&f=json"
        }
    }

    private fun authParams(): String = authQuery

    fun url(endpoint: String, params: List<Pair<String, Any?>> = emptyList()): String {
        val extra = params.filter { it.second != null }
            .joinToString("") { "&${it.first}=${urlEncode(it.second.toString())}" }
        return "$base/rest/$endpoint?${authParams()}$extra"
    }

    private suspend fun call(endpoint: String, params: List<Pair<String, Any?>> = emptyList()): JsonObject {
        val text = http.get(url(endpoint, params)).bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject["subsonic-response"]?.jsonObject
            ?: throw SubsonicException(-1, "Not a Subsonic server response")
        if (root["status"]?.jsonPrimitive?.content != "ok") {
            val err = root["error"]?.let { json.decodeFromJsonElement(SubsonicError.serializer(), it) }
            throw SubsonicException(err?.code ?: -1, err?.message ?: "Request failed")
        }
        return root
    }

    /** Same as [call] but sends parameters as a form body, for large values like synced settings. */
    private suspend fun callPost(endpoint: String, params: List<Pair<String, Any?>>): JsonObject {
        if ("formPost" !in extensions) return call(endpoint, params)
        val text = http.submitForm(
            url = "$base/rest/$endpoint?${authParams()}",
            formParameters = parameters { params.filter { it.second != null }.forEach { append(it.first, it.second.toString()) } },
        ).bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject["subsonic-response"]?.jsonObject
            ?: throw SubsonicException(-1, "Not a Subsonic server response")
        if (root["status"]?.jsonPrimitive?.content != "ok") {
            val err = root["error"]?.let { json.decodeFromJsonElement(SubsonicError.serializer(), it) }
            throw SubsonicException(err?.code ?: -1, err?.message ?: "Request failed")
        }
        return root
    }

    private fun <T> JsonObject.decode(key: String, serializer: KSerializer<T>): T? =
        this[key]?.let { json.decodeFromJsonElement(serializer, it) }

    private fun <T> JsonElement?.list(key: String, serializer: KSerializer<T>): List<T> {
        val obj = (this as? JsonObject) ?: return emptyList()
        val el = obj[key] ?: return emptyList()
        return if (el is kotlinx.serialization.json.JsonArray) json.decodeFromJsonElement(ListSerializer(serializer), el)
        else listOf(json.decodeFromJsonElement(serializer, el))
    }

    suspend fun ping(): Boolean {
        val root = call("ping")
        if (root["openSubsonic"]?.jsonPrimitive?.content == "true") {
            runCatching {
                val ext = call("getOpenSubsonicExtensions")
                extensions = ext["openSubsonicExtensions"].let { el ->
                    if (el is kotlinx.serialization.json.JsonArray) json.decodeFromJsonElement(ListSerializer(OpenSubsonicExtension.serializer()), el).map { it.name }.toSet() else emptySet()
                }
            }
        }
        return true
    }

    suspend fun getArtists(): List<Artist> =
        call("getArtists")["artists"].list("index", ArtistIndex.serializer()).flatMap { it.artist }

    suspend fun getArtist(id: String): Artist = call("getArtist", listOf("id" to id)).decode("artist", Artist.serializer())!!

    suspend fun getArtistInfo(id: String): ArtistInfo? =
        runCatching { call("getArtistInfo2", listOf("id" to id, "count" to 20)).decode("artistInfo2", ArtistInfo.serializer()) }.getOrNull()

    suspend fun getTopSongs(artist: String, count: Int = 20): List<Song> =
        runCatching { call("getTopSongs", listOf("artist" to artist, "count" to count))["topSongs"].list("song", Song.serializer()) }.getOrDefault(emptyList())

    suspend fun getSimilarSongs(id: String, count: Int = 50): List<Song> =
        call("getSimilarSongs2", listOf("id" to id, "count" to count))["similarSongs2"].list("song", Song.serializer())

    suspend fun getAlbum(id: String): Album = call("getAlbum", listOf("id" to id)).decode("album", Album.serializer())!!

    suspend fun getSong(id: String): Song = call("getSong", listOf("id" to id)).decode("song", Song.serializer())!!

    suspend fun getAlbumList(
        type: AlbumListType,
        size: Int = 50,
        offset: Int = 0,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Album> = call(
        "getAlbumList2",
        listOf("type" to type.api, "size" to size, "offset" to offset, "genre" to genre, "fromYear" to fromYear, "toYear" to toYear),
    )["albumList2"].list("album", Album.serializer())

    suspend fun getRandomSongs(size: Int = 50, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<Song> =
        call("getRandomSongs", listOf("size" to size, "genre" to genre, "fromYear" to fromYear, "toYear" to toYear))["randomSongs"].list("song", Song.serializer())

    suspend fun getSongsByGenre(genre: String, count: Int = 100, offset: Int = 0): List<Song> =
        call("getSongsByGenre", listOf("genre" to genre, "count" to count, "offset" to offset))["songsByGenre"].list("song", Song.serializer())

    suspend fun getStarred(): Starred2 = call("getStarred2").decode("starred2", Starred2.serializer()) ?: Starred2()

    suspend fun search(
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 50,
        songOffset: Int = 0,
    ): SearchResult3 = call(
        "search3",
        listOf("query" to query, "artistCount" to artistCount, "albumCount" to albumCount, "songCount" to songCount, "songOffset" to songOffset),
    ).decode("searchResult3", SearchResult3.serializer()) ?: SearchResult3()

    suspend fun getGenres(): List<Genre> = call("getGenres")["genres"].list("genre", Genre.serializer())

    suspend fun getPlaylists(): List<Playlist> = call("getPlaylists")["playlists"].list("playlist", Playlist.serializer())

    suspend fun getPlaylist(id: String): Playlist = call("getPlaylist", listOf("id" to id)).decode("playlist", Playlist.serializer())!!

    /** Creates a playlist, or replaces the songs of an existing one when [playlistId] is set. */
    suspend fun createPlaylist(name: String?, songIds: List<String>, playlistId: String? = null): Playlist? {
        val params = buildList<Pair<String, Any?>> {
            if (playlistId != null) add("playlistId" to playlistId) else add("name" to name)
            songIds.forEach { add("songId" to it) }
        }
        return call("createPlaylist", params).decode("playlist", Playlist.serializer())
    }

    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        comment: String? = null,
        public: Boolean? = null,
        add: List<String> = emptyList(),
        removeIndexes: List<Int> = emptyList(),
    ) {
        val params = buildList<Pair<String, Any?>> {
            add("playlistId" to id)
            add("name" to name)
            add("comment" to comment)
            add("public" to public)
            add.forEach { add("songIdToAdd" to it) }
            removeIndexes.forEach { add("songIndexToRemove" to it) }
        }
        callPost("updatePlaylist", params)
    }

    suspend fun deletePlaylist(id: String) { call("deletePlaylist", listOf("id" to id)) }

    suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null) {
        call("star", listOf("id" to id, "albumId" to albumId, "artistId" to artistId))
    }

    suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null) {
        call("unstar", listOf("id" to id, "albumId" to albumId, "artistId" to artistId))
    }

    suspend fun setRating(id: String, rating: Int) { call("setRating", listOf("id" to id, "rating" to rating)) }

    suspend fun scrobble(id: String, timeMs: Long?, submission: Boolean) {
        call("scrobble", listOf("id" to id, "time" to timeMs, "submission" to submission))
    }

    suspend fun getLyricsBySongId(id: String): List<StructuredLyrics> =
        call("getLyricsBySongId", listOf("id" to id))["lyricsList"].list("structuredLyrics", StructuredLyrics.serializer())

    suspend fun getLyrics(artist: String?, title: String?): LegacyLyrics? =
        call("getLyrics", listOf("artist" to artist, "title" to title)).decode("lyrics", LegacyLyrics.serializer())

    suspend fun getPlayQueue(): PlayQueue? = runCatching { call("getPlayQueue").decode("playQueue", PlayQueue.serializer()) }.getOrNull()

    suspend fun savePlayQueue(ids: List<String>, current: String?, positionMs: Long) {
        if (ids.isEmpty()) return
        val params = buildList<Pair<String, Any?>> {
            ids.forEach { add("id" to it) }
            add("current" to current)
            add("position" to positionMs)
        }
        call("savePlayQueue", params)
    }

    suspend fun getRadioStations(): List<RadioStation> =
        runCatching { call("getInternetRadioStations")["internetRadioStations"].list("internetRadioStation", RadioStation.serializer()) }.getOrDefault(emptyList())

    suspend fun getMusicFolders(): List<MusicFolder> = call("getMusicFolders")["musicFolders"].list("musicFolder", MusicFolder.serializer())

    suspend fun getIndexes(folderId: String?): List<Artist> =
        call("getIndexes", listOf("musicFolderId" to folderId))["indexes"].list("index", ArtistIndex.serializer()).flatMap { it.artist }

    suspend fun getMusicDirectory(id: String): Directory = call("getMusicDirectory", listOf("id" to id)).decode("directory", Directory.serializer())!!

    suspend fun startScan() { call("startScan") }

    fun streamUrl(id: String, maxBitrate: Int = 0, format: String = "raw", timeOffsetSec: Int? = null): String =
        url("stream", listOf("id" to id, "maxBitRate" to maxBitrate.takeIf { it > 0 }, "format" to format, "timeOffset" to timeOffsetSec, "estimateContentLength" to true))

    fun downloadUrl(id: String): String = url("download", listOf("id" to id))

    fun coverArtUrl(id: String?, size: Int? = null): String? =
        id?.let { url("getCoverArt", listOf("id" to it, "size" to size)) }
}
