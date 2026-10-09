package com.westtiwan.tarang.scrobble

import com.westtiwan.tarang.data.JsonStore
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.data.SubsonicApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PendingScrobble(val songId: String, val timeMs: Long, val artist: String?, val title: String, val album: String?, val durationSec: Int?)

@Serializable
data class ScrobbleQueue(val pending: List<PendingScrobble> = emptyList())

/**
 * Sends now playing and play submissions to Navidrome (which forwards them to Last.fm and
 * ListenBrainz when configured on the server) and optionally straight to ListenBrainz.
 * Plays made while offline are queued and flushed later.
 */
class Scrobbler(
    private val store: JsonStore<ScrobbleQueue>,
    private val http: HttpClient,
) {
    private var currentId: String? = null
    private var startedAt: Long = 0
    private var listenedMs: Long = 0
    private var lastPosition: Long = 0
    private var submitted = false

    fun onTrackStarted(song: Song, now: Long) {
        currentId = song.id
        startedAt = now
        listenedMs = 0
        lastPosition = 0
        submitted = false
    }

    /** Feed playback progress. Returns a scrobble to submit once the play counts. */
    fun onProgress(song: Song, positionMs: Long, playing: Boolean): PendingScrobble? {
        if (song.id != currentId || submitted) return null
        val delta = positionMs - lastPosition
        if (playing && delta in 1..5000) listenedMs += delta
        lastPosition = positionMs
        val duration = song.durationMs
        val threshold = if (duration > 0) minOf(duration / 2, 240_000L) else 240_000L
        if (duration in 1..29_999) return null
        if (listenedMs >= threshold) {
            submitted = true
            return PendingScrobble(song.id, startedAt, song.artist, song.title, song.album, song.duration)
        }
        return null
    }

    suspend fun nowPlaying(api: SubsonicApi?, song: Song, enabled: Boolean, lbToken: String) {
        if (!enabled) return
        runCatching { api?.scrobble(song.id, null, submission = false) }
        if (lbToken.isNotBlank()) runCatching { listenBrainz(lbToken, "playing_now", listOf(song.toPending(0))) }
    }

    suspend fun submit(api: SubsonicApi?, item: PendingScrobble, enabled: Boolean, lbToken: String) {
        if (!enabled) return
        val ok = api != null && runCatching { api.scrobble(item.songId, item.timeMs, submission = true) }.isSuccess
        if (lbToken.isNotBlank()) runCatching { listenBrainz(lbToken, "single", listOf(item)) }
        if (!ok) store.update { it.copy(pending = (it.pending + item).takeLast(5000)) }
    }

    suspend fun flush(api: SubsonicApi?) {
        if (api == null) return
        val items = store.value.pending
        if (items.isEmpty()) return
        val done = ArrayList<PendingScrobble>()
        for (item in items) {
            if (runCatching { api.scrobble(item.songId, item.timeMs, submission = true) }.isSuccess) done += item else break
        }
        if (done.isNotEmpty()) store.update { q -> q.copy(pending = q.pending - done.toSet()) }
    }

    val pendingCount: Int get() = store.value.pending.size

    private fun Song.toPending(time: Long) = PendingScrobble(id, time, artist, title, album, duration)

    private suspend fun listenBrainz(token: String, type: String, items: List<PendingScrobble>) {
        val payload = JsonArray(
            items.map { p ->
                JsonObject(
                    buildMap {
                        if (type != "playing_now") put("listened_at", JsonPrimitive(p.timeMs / 1000))
                        put(
                            "track_metadata",
                            JsonObject(
                                buildMap {
                                    put("artist_name", JsonPrimitive(p.artist ?: ""))
                                    put("track_name", JsonPrimitive(p.title))
                                    p.album?.let { put("release_name", JsonPrimitive(it)) }
                                    put(
                                        "additional_info",
                                        JsonObject(
                                            buildMap {
                                                put("media_player", JsonPrimitive("Tarang"))
                                                put("submission_client", JsonPrimitive("Tarang"))
                                                p.durationSec?.let { put("duration_ms", JsonPrimitive(it * 1000)) }
                                            },
                                        ),
                                    )
                                },
                            ),
                        )
                    },
                )
            },
        )
        val body = JsonObject(mapOf("listen_type" to JsonPrimitive(type), "payload" to payload))
        val res = http.post("https://api.listenbrainz.org/1/submit-listens") {
            header("Authorization", "Token $token")
            header("Content-Type", "application/json")
            setBody(body.toString())
        }
        if (!res.status.isSuccess()) error("ListenBrainz ${res.status}")
    }
}
