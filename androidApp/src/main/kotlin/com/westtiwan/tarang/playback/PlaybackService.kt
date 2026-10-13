package com.westtiwan.tarang.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.westtiwan.tarang.MainActivity
import com.westtiwan.tarang.R
import com.westtiwan.tarang.TarangApp
import com.westtiwan.tarang.player.AudioInfo
import com.westtiwan.tarang.widget.NowPlayingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val app get() = (application as TarangApp).app
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var tree: LibraryTree
    private lateinit var outputs: AudioOutputs
    private val eq = EqAudioProcessor()
    private var floatOutput = false
    private var inputFormat: Format? = null
    private var trackConfig: AudioSink.AudioTrackConfig? = null

    companion object {
        private const val CMD_LIKE = "tarang.like"
        private var cache: SimpleCache? = null

        @Synchronized
        fun cache(service: PlaybackService, sizeMb: Int): SimpleCache = cache ?: SimpleCache(
            File(service.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(sizeMb.toLong() * 1024 * 1024),
            StandaloneDatabaseProvider(service),
        ).also { cache = it }
    }

    override fun onCreate() {
        super.onCreate()
        tree = LibraryTree(app)
        outputs = AudioOutputs(this) { onRouteChanged() }
        floatOutput = app.settings.value.bitPerfect
        player = buildPlayer()
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("open_player", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(openApp)
            .build()
        updateLikeButton()
        outputs.start()

        AudioBus.onSelectOutput = { key ->
            val dev = outputs.select(key)
            player.setPreferredAudioDevice(dev)
        }
        AudioBus.onRefreshAudio = { scope.launch { applyAudio() } }
        AudioBus.onClearAudioCache = { songId ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val c = cache(this@PlaybackService, app.settings.value.cacheSizeMb)
                c.keys.filter { it.startsWith("$songId:") }.forEach { runCatching { c.removeResource(it) } }
            }
        }

        scope.launch {
            app.settings.map { it.bitPerfect }.distinctUntilChanged().drop(1).collect { bp ->
                floatOutput = bp
                rebuildPlayer()
            }
        }
        scope.launch {
            app.settings.map { Triple(it.replayGain, it.replayGainPreampDb, it.bitPerfect) }.distinctUntilChanged().collect { applyAudio() }
        }
        scope.launch { app.eqStore.state.collect { applyAudio() } }
    }

    private fun buildPlayer(): ExoPlayer {
        val ok = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
        val http = OkHttpDataSource.Factory(ok).setUserAgent("Tarang")
        val direct = DefaultDataSource.Factory(this, http)
        val cached = CacheDataSource.Factory()
            .setCache(cache(this, app.settings.value.cacheSizeMb))
            .setUpstreamDataSourceFactory(direct)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val sourceFactory = DefaultMediaSourceFactory(RoutingDataSource.Factory(cached, direct))
        val useFloat = floatOutput
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(useFloat)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(eq))
                    .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sourceFactory)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                inputFormat = format
                scope.launch { applyAudio() }
            }

            override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
                trackConfig = audioTrackConfig
                publishAudioInfo()
            }
        })
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateLikeButton()
                updateWidget()
                scope.launch { applyAudio() }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateWidget()
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = updateWidget()
        })
        return p
    }

    private fun rebuildPlayer() {
        val old = player
        val items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val index = old.currentMediaItemIndex
        val pos = old.currentPosition
        val play = old.playWhenReady
        val shuffle = old.shuffleModeEnabled
        val repeat = old.repeatMode
        val speed = old.playbackParameters
        val p = buildPlayer()
        if (items.isNotEmpty()) {
            p.setMediaItems(items, index.coerceAtLeast(0), pos)
            p.prepare()
        }
        p.shuffleModeEnabled = shuffle
        p.repeatMode = repeat
        p.playbackParameters = speed
        p.playWhenReady = play
        outputs.preferred?.let { p.setPreferredAudioDevice(it) }
        session.player = p
        player = p
        old.release()
        AndroidPlayer.instance?.onPlayerReplaced()
    }

    private fun onRouteChanged() {
        scope.launch { applyAudio() }
    }

    /** Applies EQ, replay gain and the bit-perfect mixer for the current track and route. */
    private fun applyAudio() {
        val s = app.settings.value
        val song = player.currentMediaItem?.toSong()
        val rate = inputFormat?.sampleRate?.takeIf { it > 0 } ?: song?.samplingRate
        val highRes = (song?.bitDepth ?: 16) > 16 || inputFormat?.pcmEncoding.let { it == C.ENCODING_PCM_24BIT || it == C.ENCODING_PCM_32BIT || it == C.ENCODING_PCM_FLOAT }
        val bitPerfect = outputs.applyBitPerfect(s.bitPerfect, rate, highRes && floatOutput)
        val eqState = app.eqStore.value
        val albumContext = player.mediaItemCount > 1 && run {
            val prev = if (player.currentMediaItemIndex > 0) player.getMediaItemAt(player.currentMediaItemIndex - 1).toSong() else null
            prev?.albumId != null && prev.albumId == song?.albumId
        }
        val rg = if (bitPerfect) 0.0 else app.replayGainDb(song, albumContext)
        eq.update(eqState.current, eqState.enabled && !bitPerfect, rg)
        publishAudioInfo()
    }

    private fun publishAudioInfo() {
        val f = inputFormat
        val song = player.currentMediaItem?.toSong()
        val cur = outputs.currentDevice()
        val eqState = app.eqStore.value
        val bp = outputs.bitPerfectActive
        val pipeline = buildList {
            add(if (f?.sampleMimeType == MimeTypes.AUDIO_RAW) "PCM passthrough" else "Decoder: ${f?.codecs ?: f?.sampleMimeType?.substringAfter('/') ?: "?"}")
            if (eqState.enabled && !bp) add("EQ: ${eqState.current.name}")
            if (eqState.enabled && bp) add("EQ bypassed for bit-perfect output")
            val rg = app.replayGainDb(song, false)
            if (!bp && rg != 0.0) add("Replay gain ${(rg * 10).toInt() / 10.0} dB")
            trackConfig?.let { add("AudioTrack ${it.sampleRate} Hz, " + encodingName(it.encoding)) }
            if (bp) add("Bit-perfect mixer ${outputs.bitPerfectFormat?.sampleRate ?: ""} Hz") else add("Android mixer")
        }
        AudioBus.audioInfo.value = AudioInfo(
            codec = song?.suffix ?: f?.sampleMimeType?.substringAfter('/'),
            sampleRate = f?.sampleRate?.takeIf { it > 0 } ?: song?.samplingRate,
            bitDepth = song?.bitDepth?.takeIf { it > 0 } ?: pcmBits(f?.pcmEncoding),
            channels = f?.channelCount?.takeIf { it > 0 } ?: song?.channelCount,
            bitrate = f?.bitrate?.takeIf { it > 0 } ?: song?.bitRate?.let { it * 1000 },
            bitPerfect = bp,
            output = cur?.let { outputs.toOutput(it).name },
            outputRate = trackConfig?.sampleRate,
            pipeline = pipeline,
            source = player.currentMediaItem?.localConfiguration?.uri?.let { if (it.scheme == "file") "Downloaded file" else "Stream from server" },
        )
    }

    private fun encodingName(e: Int) = when (e) {
        C.ENCODING_PCM_16BIT -> "16-bit"
        C.ENCODING_PCM_24BIT -> "24-bit"
        C.ENCODING_PCM_32BIT -> "32-bit"
        C.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> "encoding $e"
    }

    private fun pcmBits(e: Int?) = when (e) {
        C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_24BIT -> 24; C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32; else -> null
    }

    private fun updateLikeButton() {
        val starred = player.currentMediaItem?.toSong()?.isStarred == true
        val like = CommandButton.Builder(if (starred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (starred) "Remove favourite" else "Favourite")
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .build()
        session.setMediaButtonPreferences(ImmutableList.of(like))
    }

    private fun updateWidget() {
        val song = player.currentMediaItem?.toSong()
        NowPlayingWidget.update(this, song, player.isPlaying, app)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        AudioBus.onSelectOutput = null
        AudioBus.onRefreshAudio = null
        AudioBus.onClearAudioCache = null
        outputs.stop()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch {
            try { f.set(block()) } catch (e: Throwable) { f.setException(e) }
        }
        return f
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_LIKE) {
                player.currentMediaItem?.toSong()?.let { song ->
                    app.toggleStar(song)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(tree.root, params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            future {
                val all = runCatching { tree.children(parentId) }.getOrDefault(emptyList())
                val from = (page * pageSize).coerceAtMost(all.size)
                val to = (from + pageSize).coerceAtMost(all.size)
                LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
            }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> =
            future {
                val song = if (mediaId.startsWith("song:")) tree.resolveSong(mediaId.removePrefix("song:")) else null
                if (song != null) LibraryResult.ofItem(song.toMediaItem(app), null) else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
            }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val results = tree.search(query)
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            future { LibraryResult.ofItemList(ImmutableList.copyOf(tree.search(query).take(pageSize)), params) }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            future { resolve(mediaItems).toMutableList() }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            // A single browsable item (album, playlist, mix) from Android Auto expands to its songs.
            val single = mediaItems.singleOrNull()
            if (single != null && !single.mediaId.startsWith("song:") && single.localConfiguration == null) {
                val songs = tree.songsFor(single.mediaId)
                MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem(app) }, 0, C.TIME_UNSET)
            } else {
                MediaSession.MediaItemsWithStartPosition(resolve(mediaItems), startIndex, startPositionMs)
            }
        }

        private suspend fun resolve(items: List<MediaItem>): List<MediaItem> = items.mapNotNull { item ->
            if (item.localConfiguration != null) return@mapNotNull item
            val song = item.toSong() ?: if (item.mediaId.startsWith("song:")) tree.resolveSong(item.mediaId.removePrefix("song:")) else null
            song?.toMediaItem(app)
        }
    }
}
