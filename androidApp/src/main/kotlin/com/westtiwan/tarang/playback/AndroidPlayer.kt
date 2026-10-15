package com.westtiwan.tarang.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.RepeatMode
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.eq.OutputDevice
import com.westtiwan.tarang.player.PlayerController
import com.westtiwan.tarang.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * [PlayerController] backed by a MediaController connected to [PlaybackService]. Indexes exposed
 * to the UI follow playback order, so the queue reads correctly with shuffle on.
 */
class AndroidPlayer(private val context: Context, private val app: AppContainer) : PlayerController {
    companion object { var instance: AndroidPlayer? = null }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    override val outputs: StateFlow<List<OutputDevice>> = AudioBus.outputs
    override val currentOutput: StateFlow<OutputDevice?> = AudioBus.current

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    /** order[displayIndex] = window index in the player timeline. */
    private var order: IntArray = IntArray(0)
    private var songs: List<Song> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) rebuildQueue(player)
            publish()
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.message ?: error.errorCodeName)
            // Skip unplayable tracks instead of stopping the whole queue.
            controller?.let { c -> if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare(); c.play() } }
        }
    }

    init {
        instance = this
        connect()
        scope.launch {
            while (true) {
                delay(500)
                if (controller?.isPlaying == true) publish()
            }
        }
        scope.launch { AudioBus.audioInfo.collect { publish() } }
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        controllerFuture = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            rebuildQueue(c)
            publish()
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Called when the service swaps its ExoPlayer instance (for example toggling bit-perfect). */
    fun onPlayerReplaced() {
        scope.launch { controller?.let { rebuildQueue(it); publish() } }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** MediaController may only be used on the main thread, so every call is routed there. */
    private fun withController(block: (MediaController) -> Unit) {
        val run = {
            val c = controller
            if (c != null) block(c) else pending += block
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) run() else main.post(run)
    }

    private fun rebuildQueue(p: Player) {
        val tl = p.currentTimeline
        val n = tl.windowCount
        val window = Timeline.Window()
        val bySongWindow = Array(n) { i -> tl.getWindow(i, window).mediaItem.toSong() }
        val ord = ArrayList<Int>(n)
        if (n > 0) {
            var i = tl.getFirstWindowIndex(p.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && ord.size < n) {
                ord += i
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
            }
        }
        order = ord.toIntArray()
        songs = order.map { bySongWindow[it] ?: Song(id = "?", title = "Unknown") }
    }

    private fun publish() {
        val c = controller ?: return
        val displayIndex = order.indexOf(c.currentMediaItemIndex)
        _state.value = PlayerState(
            queue = songs,
            index = displayIndex,
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            bufferedMs = c.bufferedPosition,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) { Player.REPEAT_MODE_ALL -> RepeatMode.ALL; Player.REPEAT_MODE_ONE -> RepeatMode.ONE; else -> RepeatMode.OFF },
            speed = c.playbackParameters.speed,
            volume = c.volume,
            audio = AudioBus.audioInfo.value,
            error = _state.value.error.takeIf { c.playerError != null },
        )
    }

    private fun window(displayIndex: Int) = order.getOrElse(displayIndex) { displayIndex }

    override fun play(songs: List<Song>, startIndex: Int, startPositionMs: Long, autoPlay: Boolean) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem(app) }
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), startPositionMs)
            c.prepare()
            if (autoPlay) c.play()
        }
    }

    override fun playNext(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem(app) })
    }

    override fun enqueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(songs.map { it.toMediaItem(app) })
    }

    override fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    override fun pause() = withController { it.pause() }
    override fun resume() = withController { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() }
    override fun next() = withController { it.seekToNextMediaItem() }
    override fun previous() = withController { if (it.currentPosition > 4000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs); publish() }
    override fun skipTo(index: Int) = withController { it.seekTo(window(index), 0); if (!it.isPlaying) it.play() }
    override fun remove(index: Int) = withController { it.removeMediaItem(window(index)) }

    override fun move(from: Int, to: Int) = withController { c ->
        if (c.shuffleModeEnabled) return@withController
        c.moveMediaItem(from, to)
    }

    override fun clearQueue() = withController { it.clearMediaItems(); it.stop() }

    override fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    override fun setRepeat(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) { RepeatMode.ALL -> Player.REPEAT_MODE_ALL; RepeatMode.ONE -> Player.REPEAT_MODE_ONE; RepeatMode.OFF -> Player.REPEAT_MODE_OFF }
    }

    override fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }
    override fun setVolume(volume: Float) = withController { it.volume = volume }
    override fun selectOutput(key: String?) { AudioBus.onSelectOutput?.invoke(key) }
    override fun refreshAudio() { AudioBus.onRefreshAudio?.invoke() }
    override fun clearAudioCache(songId: String) { AudioBus.onClearAudioCache?.invoke(songId) }

    override fun updateSong(song: Song) {
        SongRegistry.put(song)
        withController { c ->
            for (i in 0 until c.mediaItemCount) {
                if (c.getMediaItemAt(i).mediaId == "song:${song.id}") c.replaceMediaItem(i, song.toMediaItem(app))
            }
            songs = songs.map { if (it.id == song.id) song else it }
            publish()
        }
    }
}
