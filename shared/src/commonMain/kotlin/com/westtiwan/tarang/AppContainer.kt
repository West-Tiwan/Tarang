package com.westtiwan.tarang

import com.westtiwan.tarang.data.AppSettings
import com.westtiwan.tarang.data.JsonStore
import com.westtiwan.tarang.data.LibraryIndex
import com.westtiwan.tarang.data.LibrarySync
import com.westtiwan.tarang.data.ReplayGainMode
import com.westtiwan.tarang.data.ServerProfile
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.data.SubsonicApi
import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.download.DownloadManager
import com.westtiwan.tarang.download.DownloadsState
import com.westtiwan.tarang.eq.EqManager
import com.westtiwan.tarang.eq.EqState
import com.westtiwan.tarang.lyrics.LibreTranslator
import com.westtiwan.tarang.lyrics.LyricsProcessor
import com.westtiwan.tarang.lyrics.LyricsService
import com.westtiwan.tarang.lyrics.Translator
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.platform.joinPath
import com.westtiwan.tarang.platform.nowMillis
import com.westtiwan.tarang.player.PlayerController
import com.westtiwan.tarang.scrobble.ScrobbleQueue
import com.westtiwan.tarang.scrobble.Scrobbler
import com.westtiwan.tarang.smart.SmartEngine
import com.westtiwan.tarang.smart.SmartPlaylist
import com.westtiwan.tarang.smart.SmartState
import com.westtiwan.tarang.sync.SettingsSync
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Process wide object graph. Created once per process by the platform entry point.
 */
class AppContainer(
    /** Platform translator, for example on device ML Kit on Android. */
    private val platformTranslator: Translator? = null,
    val appVersion: String = "dev",
    updateInstaller: com.westtiwan.tarang.update.UpdateInstaller? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
    private val dataDir = Platform.dataDir()

    val settingsStore = JsonStore(joinPath(dataDir, "settings.json"), AppSettings.serializer(), { AppSettings() }, scope)
    val settings: StateFlow<AppSettings> get() = settingsStore.state

    val activeServer: StateFlow<ServerProfile?> = settingsStore.state
        .map { s -> s.servers.firstOrNull { it.id == s.activeServerId } ?: s.servers.firstOrNull() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, settingsStore.value.let { s -> s.servers.firstOrNull { it.id == s.activeServerId } ?: s.servers.firstOrNull() })

    private var apiCache: SubsonicApi? = null
    val api: SubsonicApi?
        get() {
            val server = activeServer.value ?: return null
            val c = apiCache
            if (c != null && c.server == server) return c
            return SubsonicApi(server, http).also { apiCache = it }
        }

    private fun serverDir(name: String) = joinPath(dataDir, "servers", activeServer.value?.id ?: "none", name)

    val library = LibrarySync(JsonStore(joinPath(dataDir, "library.json"), LibraryIndex.serializer(), { LibraryIndex() }, scope))
    val eqStore = JsonStore(joinPath(dataDir, "eq.json"), EqState.serializer(), { EqState() }, scope)
    val eq = EqManager(eqStore, http) { nowMillis() }
    val smartStore = JsonStore(joinPath(dataDir, "smart.json"), SmartState.serializer(), { SmartState() }, scope)
    val scrobbler = Scrobbler(JsonStore(joinPath(dataDir, "scrobbles.json"), ScrobbleQueue.serializer(), { ScrobbleQueue() }, scope), http)
    val downloads = DownloadManager(
        JsonStore(joinPath(dataDir, "downloads.json"), DownloadsState.serializer(), { DownloadsState() }, scope),
        http, joinPath(dataDir, "downloads"), scope, { api }, { settings.value },
    )
    val artistArt = com.westtiwan.tarang.art.ArtistArt(
        JsonStore(joinPath(dataDir, "artist_art.json"), com.westtiwan.tarang.art.ArtistArtState.serializer(), { com.westtiwan.tarang.art.ArtistArtState() }, scope),
        http, joinPath(dataDir, "artist_art"), { nowMillis() },
    )
    val lyrics = LyricsService(http, joinPath(Platform.cacheDir(), "lyrics"))
    val lyricsProcessor = LyricsProcessor()

    lateinit var player: PlayerController
        private set

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    val offline: Boolean get() = settings.value.offlineMode || !_online.value

    /** Bumped when cover caches are cleared so images reload. */
    val coverEpoch = MutableStateFlow(0)
    /** Bumped when lyrics or translation caches are cleared so the lyrics view reloads. */
    val lyricsEpoch = MutableStateFlow(0)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun toast(msg: String?) { _toast.value = msg }

    val updater = com.westtiwan.tarang.update.Updater(
        http, appVersion, joinPath(Platform.cacheDir(), "updates"), updateInstaller,
        repo = { settings.value.updateRepo }, token = { settings.value.githubToken },
    )

    val translations = com.westtiwan.tarang.lyrics.TranslationCache(joinPath(Platform.cacheDir(), "translations"))

    /** The chosen engine first, then Google, then the on-device translator as fallbacks. */
    fun translator(): com.westtiwan.tarang.lyrics.FallbackTranslator {
        val s = settings.value
        val google = com.westtiwan.tarang.lyrics.GoogleTranslator(http)
        val chosen: Translator? = when (s.translateEngine) {
            "gemini" -> s.geminiKey.takeIf { it.isNotBlank() }?.let { com.westtiwan.tarang.lyrics.GeminiTranslator(http, it, s.geminiModel) }
            "deepl" -> s.deeplKey.takeIf { it.isNotBlank() }?.let { com.westtiwan.tarang.lyrics.DeepLTranslator(http, it) }
            "openai" -> s.openAiUrl.takeIf { it.isNotBlank() && s.openAiModel.isNotBlank() }?.let { com.westtiwan.tarang.lyrics.OpenAiTranslator(http, it, s.openAiKey, s.openAiModel) }
            "libre" -> s.libreTranslateUrl.takeIf { it.isNotBlank() }?.let { LibreTranslator(http, it, s.libreTranslateKey) }
            "device" -> platformTranslator
            else -> google
        }
        return com.westtiwan.tarang.lyrics.FallbackTranslator(listOfNotNull(chosen, google.takeIf { chosen !== it }, platformTranslator.takeIf { chosen !== it }))
    }

    fun updateSettings(f: (AppSettings) -> AppSettings) = settingsStore.update(f)

    fun addServer(profile: ServerProfile) = updateSettings { s ->
        s.copy(servers = s.servers.filterNot { it.id == profile.id } + profile, activeServerId = profile.id)
    }

    fun removeServer(id: String) = updateSettings { s ->
        val left = s.servers.filterNot { it.id == id }
        s.copy(servers = left, activeServerId = if (s.activeServerId == id) left.firstOrNull()?.id else s.activeServerId)
    }

    // -------- Playback helpers used by the platform players --------

    /** URL or file path for a song, preferring the downloaded copy. */
    fun mediaUri(song: Song): String? {
        song.streamUrlOverride?.let { return it }
        downloads.localPath(song.id)?.let { return "file://$it" }
        val a = api ?: return null
        val s = settings.value
        val unmetered = Platform.isUnmeteredNetwork()
        val format = if (unmetered) s.wifiFormat else s.mobileFormat
        val bitrate = if (unmetered) s.wifiMaxBitrate else s.mobileMaxBitrate
        return a.streamUrl(song.id, bitrate, format)
    }

    fun coverUrl(coverArt: String?, size: Int = 600): String? {
        downloads.localCover(coverArt)?.let { return "file://$it" }
        return api?.coverArtUrl(coverArt, size)
    }

    /** Replay gain in dB for a song according to settings. */
    fun replayGainDb(song: Song?, albumContext: Boolean): Double {
        val s = settings.value
        val rg = song?.replayGain ?: return 0.0
        val mode = if (s.replayGain == ReplayGainMode.AUTO) (if (albumContext) ReplayGainMode.ALBUM else ReplayGainMode.TRACK) else s.replayGain
        val gain = when (mode) {
            ReplayGainMode.OFF -> return 0.0
            ReplayGainMode.TRACK -> rg.trackGain ?: rg.albumGain
            ReplayGainMode.ALBUM -> rg.albumGain ?: rg.trackGain
            else -> null
        } ?: return 0.0
        val peak = (if (mode == ReplayGainMode.ALBUM) rg.albumPeak else rg.trackPeak) ?: 1.0
        val total = gain + s.replayGainPreampDb + (rg.baseGain ?: 0.0)
        // Never push the peak above full scale.
        val maxGain = if (peak > 0) -20 * kotlin.math.log10(peak) else total
        return minOf(total, maxGain)
    }

    fun dbToLinear(db: Double): Float = 10.0.pow(db / 20).toFloat()

    // -------- Lifecycle --------

    private var started = false
    var coordinator: PlaybackCoordinator? = null
        private set

    fun attachPlayer(p: PlayerController) {
        player = p
        coordinator = PlaybackCoordinator(this).also { it.start() }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                _online.value = Platform.isOnline()
                delay(15_000)
            }
        }
        scope.launch {
            activeServer.collect { server ->
                if (server == null) return@collect
                val a = api ?: return@collect
                runCatching { a.ping() }
                pullSyncedSettings()
                scrobbler.flush(a)
                if (library.index.value.serverId != server.id || nowMillis() - library.index.value.syncedAt > 6 * 3_600_000L) {
                    library.sync(a, nowMillis())
                }
            }
        }
        scope.launch {
            merge(eqStore.state.map { it.updatedAt }, smartStore.state.map { it.updatedAt }, artistArt.state.map { it.updatedAt })
                .distinctUntilChanged().drop(1).debounce(4000)
                .collect { pushSyncedSettings() }
        }
        scope.launch {
            delay(5000)
            if (settings.value.autoUpdate && appVersion != "dev") updater.check()
        }
        scope.launch {
            eq.message.collect { m -> if (m != null) { toast(m); eq.consumeMessage() } }
        }
    }

    suspend fun pullSyncedSettings() {
        val a = api ?: return
        if (!settings.value.syncEqToServer) return
        val remote = runCatching { SettingsSync.pull(a) }.getOrNull() ?: return
        if (remote.eqUpdatedAt > eqStore.value.updatedAt) {
            eqStore.update { it.copy(presets = remote.eqPresets, deviceProfiles = remote.deviceProfiles, updatedAt = remote.eqUpdatedAt) }
        }
        artistArt.merge(remote.artistImages, remote.artistImagesUpdatedAt)
        if (remote.smartUpdatedAt > smartStore.value.updatedAt && remote.smart.isNotEmpty()) {
            smartStore.update { it.copy(playlists = remote.smart, updatedAt = remote.smartUpdatedAt) }
        }
    }

    suspend fun pushSyncedSettings() {
        val a = api ?: return
        if (!settings.value.syncEqToServer || offline) return
        runCatching { SettingsSync.push(a, eqStore.value, smartStore.value, artistArt.syncable(), artistArt.state.value.updatedAt) }
    }

    // -------- Smart playlists --------

    fun saveSmart(pl: SmartPlaylist) = smartStore.update { s ->
        val list = if (s.playlists.any { it.id == pl.id }) s.playlists.map { if (it.id == pl.id) pl else it } else s.playlists + pl
        s.copy(playlists = list, updatedAt = nowMillis())
    }

    fun deleteSmart(id: String) = smartStore.update { s -> s.copy(playlists = s.playlists.filterNot { it.id == id }, updatedAt = nowMillis()) }

    fun evaluateSmart(pl: SmartPlaylist): List<Song> = SmartEngine.evaluate(pl, library.index.value.songs, nowMillis())

    /** Writes the smart playlist result into a normal server playlist. */
    suspend fun syncSmartToServer(pl: SmartPlaylist): Result<Unit> = runCatching {
        val a = api ?: error("Not connected")
        val ids = evaluateSmart(pl).map { it.id }
        val existing = pl.serverPlaylistId?.let { id -> runCatching { a.getPlaylist(id) }.getOrNull() }
        if (existing != null) {
            a.createPlaylist(null, ids, playlistId = existing.id)
        } else {
            val created = a.createPlaylist("${pl.name} (smart)", ids)
            val id = created?.id ?: a.getPlaylists().firstOrNull { it.name == "${pl.name} (smart)" }?.id
            saveSmart(pl.copy(syncToServer = true, serverPlaylistId = id))
        }
        a.updatePlaylist(existing?.id ?: smartStore.value.playlists.first { it.id == pl.id }.serverPlaylistId ?: return@runCatching,
            comment = "Smart playlist from Tarang. Rules: " + json.encodeToString(com.westtiwan.tarang.smart.SmartPlaylist.serializer(), pl).take(900))
    }

    // -------- Library actions --------

    fun toggleStar(song: Song) {
        val starred = !song.isStarred
        val updated = song.copy(starred = if (starred) "now" else null)
        player.updateSong(updated)
        library.updateSong(updated)
        scope.launch {
            val a = api ?: return@launch
            runCatching { if (starred) a.star(id = song.id) else a.unstar(id = song.id) }
        }
    }

    fun setRating(song: Song, rating: Int) {
        val updated = song.copy(userRating = rating)
        player.updateSong(updated)
        library.updateSong(updated)
        scope.launch { runCatching { api?.setRating(song.id, rating) } }
    }

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
