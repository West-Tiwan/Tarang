package com.westtiwan.tarang.desktop

import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.data.RepeatMode
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.eq.EqEngine
import com.westtiwan.tarang.eq.OutputDevice
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.player.AudioInfo
import com.westtiwan.tarang.player.PlayerController
import com.westtiwan.tarang.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/** A track being downloaded to disk while it plays. */
private class CachedTrack(val file: File, val suffix: String) {
    @Volatile var complete = false
    @Volatile var failed: Throwable? = null
    @Volatile var total: Long = -1
    @Volatile var cancelled = false

    fun stream(): InputStream = object : InputStream() {
        private val raf = RandomAccessFile(file, "r")
        private var pos = 0L
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xff
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                if (cancelled) return -1
                val avail = file.length() - pos
                if (avail > 0) {
                    raf.seek(pos)
                    val n = raf.read(b, off, minOf(len.toLong(), avail).toInt())
                    if (n > 0) { pos += n; return n }
                }
                if (complete) return -1
                failed?.let { throw java.io.IOException(it) }
                Thread.sleep(15)
            }
        }
        override fun close() = raf.close()
    }
}

/**
 * Desktop playback engine written in plain Kotlin: progressive download into a disk cache,
 * pure Java FLAC/MP3/WAV decoding, the shared parametric EQ, replay gain, gapless output
 * through Java Sound, and output device selection.
 */
class DesktopPlayer(private val app: AppContainer) : PlayerController {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    private val _outputs = MutableStateFlow<List<OutputDevice>>(emptyList())
    override val outputs: StateFlow<List<OutputDevice>> = _outputs
    private val _current = MutableStateFlow<OutputDevice?>(null)
    override val currentOutput: StateFlow<OutputDevice?> = _current

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val cacheDir = File(Platform.cacheDir(), "audio").apply { mkdirs() }
    private val tracks = ConcurrentHashMap<String, CachedTrack>()
    private val lock = Object()
    /** Guards the output line so only the current track's thread can write to it. */
    private val writeLock = Object()

    private var queue: List<Song> = emptyList()
    private var order: List<Int> = emptyList()
    private var pos = -1
    @Volatile private var playing = false
    @Volatile private var generation = 0
    @Volatile private var positionMs = 0L
    @Volatile private var seekRequest: Long? = null
    @Volatile private var volume = 1f
    @Volatile private var speed = 1f
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    private var selectedMixer: Mixer.Info? = null
    private var line: SourceDataLine? = null
    private var lineFormat: AudioFormat? = null
    @Volatile private var eqEngine: EqEngine? = null
    private var audioInfo = AudioInfo()

    init {
        refreshOutputs()
        thread(isDaemon = true, name = "tarang-ticker") {
            while (true) {
                Thread.sleep(500)
                publish()
                if (_outputs.value.isEmpty() || System.currentTimeMillis() % 10_000 < 500) refreshOutputs()
            }
        }
    }

    // ---------------- queue ----------------

    private val currentSong: Song? get() = order.getOrNull(pos)?.let { queue.getOrNull(it) }

    private fun publish() {
        val song = currentSong
        _state.value = PlayerState(
            queue = order.map { queue[it] },
            index = pos,
            playing = playing,
            positionMs = positionMs,
            durationMs = song?.durationMs ?: 0,
            shuffle = shuffle,
            repeat = repeat,
            speed = speed,
            volume = volume,
            audio = audioInfo,
            error = _state.value.error,
        )
    }

    private fun rebuildOrder(keepCurrent: Boolean) {
        val cur = order.getOrNull(pos)
        val idx = queue.indices.toList()
        order = if (shuffle) {
            val rest = idx.filter { it != cur }.shuffled()
            if (keepCurrent && cur != null) listOf(cur) + rest else rest
        } else idx
        pos = if (cur != null) order.indexOf(cur).coerceAtLeast(0) else if (order.isEmpty()) -1 else 0
    }

    override fun play(songs: List<Song>, startIndex: Int, startPositionMs: Long, autoPlay: Boolean) {
        synchronized(lock) {
            queue = songs
            shuffle = false
            order = songs.indices.toList()
            pos = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        }
        if (songs.isEmpty()) return stopPlayback()
        startTrack(startPositionMs, autoPlay)
    }

    override fun playNext(songs: List<Song>) {
        if (queue.isEmpty()) return play(songs)
        synchronized(lock) {
            val base = queue.size
            queue = queue + songs
            val newIdx = songs.indices.map { base + it }
            order = order.take(pos + 1) + newIdx + order.drop(pos + 1)
        }
        publish()
        prefetchNext()
    }

    override fun enqueue(songs: List<Song>) {
        if (queue.isEmpty()) return play(songs)
        synchronized(lock) {
            val base = queue.size
            queue = queue + songs
            order = order + songs.indices.map { base + it }
        }
        publish()
    }

    override fun toggle() = if (playing) pause() else resume()

    override fun pause() {
        playing = false
        line?.stop()
        publish()
    }

    override fun resume() {
        if (currentSong == null) return
        if (decoderAlive) {
            playing = true
            line?.start()
        } else {
            startTrack(positionMs, true)
        }
        publish()
    }

    override fun next() {
        if (pos < order.lastIndex) { pos++; startTrack(0, true) }
        else if (repeat == RepeatMode.ALL && order.isNotEmpty()) { pos = 0; startTrack(0, true) }
    }

    override fun previous() {
        if (positionMs > 4000 || pos == 0) seekTo(0) else { pos--; startTrack(0, true) }
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
        if (decoderAlive) seekRequest = positionMs else startTrack(positionMs, playing)
        publish()
    }

    override fun skipTo(index: Int) {
        if (index !in order.indices) return
        pos = index
        startTrack(0, true)
    }

    override fun remove(index: Int) {
        if (index !in order.indices) return
        val wasCurrent = index == pos
        synchronized(lock) {
            order = order.filterIndexed { i, _ -> i != index }
            if (index < pos) pos--
        }
        if (wasCurrent) { if (pos in order.indices) startTrack(0, playing) else stopPlayback() }
        publish()
    }

    override fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices) return
        synchronized(lock) {
            val m = order.toMutableList()
            val item = m.removeAt(from)
            m.add(to, item)
            order = m
            pos = when (pos) {
                from -> to
                in (minOf(from, to)..maxOf(from, to)) -> if (from < to) pos - 1 else pos + 1
                else -> pos
            }
        }
        publish()
    }

    override fun clearQueue() {
        stopPlayback()
        queue = emptyList(); order = emptyList(); pos = -1
        publish()
    }

    override fun setShuffle(on: Boolean) {
        shuffle = on
        synchronized(lock) { rebuildOrder(true) }
        publish()
    }

    override fun setRepeat(mode: RepeatMode) { repeat = mode; publish() }
    override fun setSpeed(speed: Float) {
        // Time stretching is not implemented in the desktop engine yet.
        if (speed != 1f) app.toast("Playback speed is only available on Android for now")
    }
    override fun setVolume(volume: Float) { this.volume = volume.coerceIn(0f, 1f); publish() }

    override fun selectOutput(key: String?) {
        selectedMixer = AudioSystem.getMixerInfo().firstOrNull { outputKey(it) == key }
        _current.value = _outputs.value.firstOrNull { it.key == key }
        closeLine()
        if (currentSong != null) startTrack(positionMs, playing)
    }

    override fun refreshAudio() = configureEq(lineFormat?.sampleRate?.toInt(), lineFormat?.channels)

    override fun clearAudioCache(songId: String) {
        tracks.remove(songId)?.cancelled = true
        cacheDir.listFiles()?.filter { it.name.startsWith("$songId.") }?.forEach { it.delete() }
    }

    override fun updateSong(song: Song) {
        queue = queue.map { if (it.id == song.id) song else it }
        publish()
    }

    // ---------------- outputs ----------------

    private fun outputKey(m: Mixer.Info) = "${kindOf(m.name).name}:${m.name}"

    private fun kindOf(name: String): OutputDevice.Kind {
        val n = name.lowercase()
        return when {
            n.contains("speaker") -> OutputDevice.Kind.SPEAKER
            n.contains("headphone") -> OutputDevice.Kind.WIRED
            listOf("airpods", "bluetooth", "beats", "bose", "sony", "wh-", "wf-", "buds", "jabra", "sennheiser momentum").any { n.contains(it) } -> OutputDevice.Kind.BLUETOOTH
            listOf("usb", "dac", "fiio", "topping", "dragonfly", "schiit", "ifi", "moondrop", "qudelix", "focusrite", "scarlett").any { n.contains(it) } -> OutputDevice.Kind.USB
            n.contains("hdmi") || n.contains("display") -> OutputDevice.Kind.HDMI
            else -> OutputDevice.Kind.OTHER
        }
    }

    private fun refreshOutputs() {
        val list = AudioSystem.getMixerInfo().filter { info ->
            runCatching { AudioSystem.getMixer(info).sourceLineInfo.any { it.lineClass == SourceDataLine::class.java } }.getOrDefault(false) &&
                !info.name.contains("Port", ignoreCase = true)
        }.map { OutputDevice(outputKey(it), it.name.removePrefix("Default Audio Device").ifBlank { "System default" }, kindOf(it.name)) }
            .distinctBy { it.key }
        _outputs.value = list
        if (_current.value == null || list.none { it.key == _current.value?.key }) {
            _current.value = list.firstOrNull { it.key == selectedMixer?.let(::outputKey) } ?: list.firstOrNull { it.kind != OutputDevice.Kind.OTHER } ?: list.firstOrNull()
        }
    }

    // ---------------- playback ----------------

    @Volatile private var decoderAlive = false

    private fun stopPlayback() {
        generation++
        playing = false
        closeLine()
        publish()
    }

    private fun closeLine() {
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
        lineFormat = null
    }

    private fun sourceFor(song: Song): Pair<String, String>? {
        song.streamUrlOverride?.let { return it to "mp3" }
        app.downloads.localPath(song.id)?.let { path ->
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in Decoders.nativeFormats) return "file://$path" to ext
        }
        val api = app.api ?: return null
        val s = app.settings.value
        val suffix = song.suffix?.lowercase() ?: ""
        return if (s.wifiFormat == "raw" && suffix in Decoders.nativeFormats) api.streamUrl(song.id, 0, "raw") to suffix
        else api.streamUrl(song.id, if (s.wifiMaxBitrate in 1..319) s.wifiMaxBitrate else 320, "mp3") to "mp3"
    }

    private fun obtain(song: Song): CachedTrack? {
        tracks[song.id]?.let { t -> if (t.failed == null && !t.cancelled) return t }
        val (uri, suffix) = sourceFor(song) ?: return null
        if (uri.startsWith("file://")) return CachedTrack(File(uri.removePrefix("file://")), suffix).also { it.complete = true }
        val file = File(cacheDir, "${song.id}.$suffix")
        val done = File(cacheDir, "${song.id}.$suffix.done")
        val track = CachedTrack(file, suffix)
        if (file.exists() && done.exists()) { track.complete = true; tracks[song.id] = track; return track }
        file.delete()
        file.createNewFile()
        tracks[song.id] = track
        thread(isDaemon = true, name = "tarang-dl-${song.id}") {
            try {
                val req = HttpRequest.newBuilder(URI(uri)).timeout(Duration.ofMinutes(30)).GET().build()
                val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
                if (res.statusCode() !in 200..299) error("HTTP ${res.statusCode()}")
                track.total = res.headers().firstValueAsLong("content-length").orElse(-1)
                res.body().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (!track.cancelled) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (!track.cancelled && song.streamUrlOverride == null) done.createNewFile()
                track.complete = true
                trimCache()
            } catch (e: Throwable) {
                track.failed = e
                tracks.remove(song.id)
            }
        }
        return track
    }

    private fun trimCache() {
        val limit = app.settings.value.cacheSizeMb.toLong() * 1024 * 1024
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".done") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= limit) break
            val id = f.name.substringBefore('.')
            if (id == currentSong?.id) continue
            total -= f.length()
            f.delete()
            File(f.path + ".done").delete()
            tracks.remove(id)
        }
    }

    private fun prefetchNext() {
        val nextIdx = if (pos < order.lastIndex) pos + 1 else if (repeat == RepeatMode.ALL) 0 else return
        val song = order.getOrNull(nextIdx)?.let { queue.getOrNull(it) } ?: return
        if (song.streamUrlOverride == null) obtain(song)
    }

    private fun configureEq(rate: Int?, channels: Int?) {
        if (rate == null || channels == null) return
        val eqState = app.eqStore.value
        val engine = eqEngine ?: EqEngine(rate, channels).also { eqEngine = it }
        engine.configure(eqState.current, eqState.enabled, app.replayGainDb(currentSong, false))
    }

    private fun openLine(fmt: PcmFormat): SourceDataLine {
        val bits = if (fmt.bitDepth > 16) 24 else 16
        val target = AudioFormat(fmt.sampleRate.toFloat(), bits, fmt.channels, true, false)
        val current = line
        if (current != null && lineFormat?.matches(target) == true) return current
        closeLine()
        val mixer = selectedMixer
        fun tryOpen(f: AudioFormat): SourceDataLine {
            val l = if (mixer != null) AudioSystem.getSourceDataLine(f, mixer) else AudioSystem.getSourceDataLine(f)
            l.open(f, (f.frameRate * f.frameSize / 4).toInt())
            return l
        }
        val opened = runCatching { tryOpen(target) }.getOrElse {
            tryOpen(AudioFormat(fmt.sampleRate.toFloat(), 16, fmt.channels, true, false))
        }
        line = opened
        lineFormat = opened.format
        eqEngine = EqEngine(fmt.sampleRate, fmt.channels)
        configureEq(fmt.sampleRate, fmt.channels)
        return opened
    }

    private fun startTrack(startMs: Long, autoPlay: Boolean) {
        val gen = ++generation
        seekRequest = null
        // Drop whatever the previous track left in the output buffer right away.
        line?.let { l -> thread(isDaemon = true) { synchronized(writeLock) { if (gen == generation) runCatching { l.flush() } } } }
        val song = currentSong ?: return
        positionMs = startMs
        playing = autoPlay
        _state.value = _state.value.copy(error = null)
        publish()
        thread(isDaemon = true, name = "tarang-play") { runTrack(gen, song, startMs) }
    }

    private fun runTrack(gen: Int, song: Song, startMs: Long) {
        decoderAlive = true
        try {
            val track = obtain(song) ?: error("Not connected to a server")
            DevLog.log("PLAY id=${song.id} title=\"${song.title}\" artist=\"${song.artist}\" album=\"${song.album}\" suffix=${song.suffix} path=\"${song.path}\" file=${track.file.name}")
            var decoder = openDecoder(track)
            if (gen != generation) { decoder.close(); return }
            thread(isDaemon = true) {
                // Once enough of the file is here, compare its tags with what the server said it is.
                var waited = 0
                while (!track.complete && track.file.length() < 256 * 1024 && waited < 200) { Thread.sleep(50); waited++ }
                val tags = TagPeek.read(track.file)
                val match = tags["TITLE"]?.let { it.equals(song.title, ignoreCase = true) }
                DevLog.log("TAGS id=${song.id} file=${track.file.name} tags=$tags matchesTitle=$match")
            }
            val fmt = decoder.format
            val l = synchronized(writeLock) {
                if (gen != generation) { decoder.close(); return }
                openLine(fmt).also { it.flush() }
            }
            if (startMs > 0) decoder.skipFrames(startMs * fmt.sampleRate / 1000)
            var framesPlayed = startMs * fmt.sampleRate / 1000
            audioInfo = AudioInfo(
                codec = fmt.codec, sampleRate = fmt.sampleRate, bitDepth = fmt.bitDepth, channels = fmt.channels, bitrate = song.bitRate?.let { it * 1000 },
                bitPerfect = false, output = _current.value?.name, outputRate = l.format.sampleRate.toInt(),
                pipeline = buildList {
                    add("Decoder: ${fmt.codec.uppercase()} (pure Java)")
                    val eq = app.eqStore.value
                    if (eq.enabled) add("EQ: ${eq.current.name}")
                    val rg = app.replayGainDb(song, false)
                    if (rg != 0.0) add("Replay gain ${(rg * 10).toInt() / 10.0} dB")
                    add("Java Sound ${l.format.sampleSizeInBits}-bit ${l.format.sampleRate.toInt()} Hz")
                },
                source = if (track.complete && song.streamUrlOverride == null && app.downloads.isDownloaded(song.id)) "Downloaded file" else "Stream from server",
            )
            if (playing) l.start()
            val buf = FloatArray(4096 * fmt.channels)
            val bytesPer = l.format.sampleSizeInBits / 8
            val out = ByteArray(buf.size * bytesPer)
            var prefetched = false
            while (gen == generation) {
                seekRequest?.let { target ->
                    seekRequest = null
                    decoder.close()
                    decoder = openDecoder(track)
                    decoder.skipFrames(target * fmt.sampleRate / 1000)
                    framesPlayed = target * fmt.sampleRate / 1000
                    l.flush()
                }
                if (!playing) { Thread.sleep(30); continue }
                val n = decoder.read(buf)
                if (n < 0) break
                eqEngine?.process(buf, n)
                val v = volume
                var o = 0
                for (i in 0 until n) {
                    val s = (buf[i] * v).coerceIn(-1f, 1f)
                    if (bytesPer == 3) {
                        val x = (s * 8_388_607f).toInt()
                        out[o++] = x.toByte(); out[o++] = (x shr 8).toByte(); out[o++] = (x shr 16).toByte()
                    } else {
                        val x = (s * 32_767f).toInt()
                        out[o++] = x.toByte(); out[o++] = (x shr 8).toByte()
                    }
                }
                val wrote = synchronized(writeLock) {
                    if (gen == generation) { l.write(out, 0, o); true } else false
                }
                if (!wrote) break
                framesPlayed += n / fmt.channels
                // Account for audio still sitting in the line buffer.
                val buffered = (l.bufferSize - l.available()) / l.format.frameSize
                positionMs = ((framesPlayed - buffered).coerceAtLeast(0) * 1000) / fmt.sampleRate
                if (!prefetched && song.durationMs > 0 && positionMs > song.durationMs - 30_000) {
                    prefetched = true
                    prefetchNext()
                }
            }
            decoder.close()
            if (gen == generation) onTrackEnded()
        } catch (e: Throwable) {
            if (gen == generation) {
                _state.value = _state.value.copy(error = "Can't play ${song.title}: ${e.message ?: e::class.simpleName}")
                tracks.remove(song.id)?.cancelled = true
                if (pos < order.lastIndex) { pos++; startTrack(0, true) } else { playing = false; publish() }
            }
        } finally {
            if (gen == generation) decoderAlive = false
        }
    }

    private fun openDecoder(track: CachedTrack): PcmDecoder {
        var suffix = track.suffix
        if (track.complete || track.file.length() > 16) Decoders.sniff(track.file)?.let { suffix = it }
        return Decoders.open(track.stream(), suffix)
    }

    private fun onTrackEnded() {
        when {
            repeat == RepeatMode.ONE -> startTrack(0, true)
            pos < order.lastIndex -> { pos++; startTrack(0, true) }
            repeat == RepeatMode.ALL && order.isNotEmpty() -> { pos = 0; startTrack(0, true) }
            else -> {
                line?.drain()
                playing = false
                positionMs = 0
                publish()
            }
        }
    }
}
