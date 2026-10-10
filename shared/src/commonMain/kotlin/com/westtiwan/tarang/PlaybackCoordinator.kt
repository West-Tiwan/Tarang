package com.westtiwan.tarang

import com.westtiwan.tarang.data.JsonStore
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.platform.joinPath
import com.westtiwan.tarang.platform.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Serializable
data class SavedQueue(val songs: List<Song> = emptyList(), val index: Int = 0, val positionMs: Long = 0)

/**
 * Platform independent playback side effects: scrobbling, play queue sync across devices,
 * EQ following the output device, and the sleep timer.
 */
class PlaybackCoordinator(private val app: AppContainer) {
    private val player get() = app.player
    private val saved = JsonStore(joinPath(Platform.dataDir(), "queue.json"), SavedQueue.serializer(), { SavedQueue() }, app.scope)

    var sleepAtMs: Long? = null
        private set
    var sleepAfterCurrent: Boolean = false
        private set

    fun start() {
        app.scope.launch {
            // Give the platform player a moment to connect, then restore the last session if nothing is playing.
            delay(1500)
            val q = saved.value
            if (player.state.value.queue.isEmpty() && q.songs.isNotEmpty()) {
                player.play(q.songs, q.index.coerceIn(0, q.songs.lastIndex), q.positionMs, autoPlay = false)
            }
            while (true) {
                delay(5000)
                val st = player.state.value
                if (st.queue.isEmpty()) continue
                val songs = st.queue.filter { it.streamUrlOverride == null }.take(2000)
                if (songs.isEmpty()) continue
                val next = SavedQueue(songs, st.index.coerceAtLeast(0), st.positionMs)
                if (next != saved.value) saved.set(next)
            }
        }
        app.scope.launch {
            player.state.map { it.current?.id }.distinctUntilChanged().collect { id ->
                val song = player.state.value.current ?: return@collect
                if (id == null) return@collect
                app.scrobbler.onTrackStarted(song, nowMillis())
                val s = app.settings.value
                app.scrobbler.nowPlaying(if (app.offline) null else app.api, song, s.scrobble, s.listenBrainzToken)
                if (sleepAfterCurrent) {
                    sleepAfterCurrent = false
                    player.pause()
                }
            }
        }
        app.scope.launch {
            player.state.collect { st ->
                val song = st.current ?: return@collect
                val item = app.scrobbler.onProgress(song, st.positionMs, st.playing) ?: return@collect
                val s = app.settings.value
                app.scrobbler.submit(if (app.offline) null else app.api, item, s.scrobble, s.listenBrainzToken)
                app.library.patch(song.id) { it.copy(playCount = (it.playCount ?: 0) + 1) }
            }
        }
        app.scope.launch {
            player.currentOutput.collect { out -> if (out != null) app.eq.onOutputChanged(out) }
        }
        app.scope.launch {
            app.eqStore.state.collect { player.refreshAudio() }
        }
        app.scope.launch {
            var lastSaved = 0L
            var lastKey = ""
            while (true) {
                delay(10_000)
                val st = player.state.value
                if (!app.settings.value.syncPlayQueue || app.offline || st.queue.isEmpty()) continue
                val key = st.queue.joinToString(",") { it.id } + st.index
                val now = nowMillis()
                if (key != lastKey || (st.playing && now - lastSaved > 30_000)) {
                    runCatching {
                        app.api?.savePlayQueue(st.queue.filter { it.streamUrlOverride == null }.map { it.id }.take(1000), st.current?.id, st.positionMs)
                    }
                    lastKey = key
                    lastSaved = now
                }
            }
        }
        app.scope.launch {
            while (true) {
                delay(1000)
                val at = sleepAtMs ?: continue
                if (nowMillis() >= at) {
                    sleepAtMs = null
                    player.pause()
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepAfterCurrent = false
        sleepAtMs = minutes?.let { nowMillis() + it * 60_000L }
    }

    fun sleepAfterTrack() {
        sleepAtMs = null
        sleepAfterCurrent = true
    }

    /** Restores the queue saved by another device (or this one) on the server. */
    suspend fun restoreServerQueue(autoPlay: Boolean = false): Boolean {
        val q = app.api?.getPlayQueue() ?: return false
        if (q.entry.isEmpty()) return false
        val idx = q.entry.indexOfFirst { it.id == q.current }.coerceAtLeast(0)
        player.play(q.entry, idx, q.position, autoPlay)
        return true
    }
}
