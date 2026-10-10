package com.westtiwan.tarang.download

import com.westtiwan.tarang.data.AppSettings
import com.westtiwan.tarang.data.JsonStore
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.data.SubsonicApi
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.joinPath
import com.westtiwan.tarang.platform.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

@Serializable
data class DownloadItem(
    val song: Song,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val path: String? = null,
    val progress: Float = 0f,
    val bytes: Long = 0,
    val error: String? = null,
    val addedAt: Long = 0,
    val format: String = "raw",
)

@Serializable
data class DownloadsState(val items: Map<String, DownloadItem> = emptyMap())

class DownloadManager(
    private val store: JsonStore<DownloadsState>,
    private val http: HttpClient,
    private val dir: String,
    private val scope: CoroutineScope,
    private val api: () -> SubsonicApi?,
    private val settings: () -> AppSettings,
) {
    val state: StateFlow<DownloadsState> get() = store.state
    private val semaphore = Semaphore(3)
    private val lock = Mutex()
    private val running = HashMap<String, Job>()
    val coverDir = joinPath(dir, "covers")

    init {
        Files.mkdirs(dir)
        Files.mkdirs(coverDir)
        // Anything interrupted last time goes back to the queue.
        store.update { s -> s.copy(items = s.items.mapValues { (_, v) -> if (v.status == DownloadStatus.DOWNLOADING) v.copy(status = DownloadStatus.QUEUED) else v }) }
        scope.launch { pump() }
    }

    fun localPath(songId: String): String? =
        store.value.items[songId]?.takeIf { it.status == DownloadStatus.DONE }?.path?.takeIf { Files.exists(it) }

    fun localCover(coverArt: String?): String? =
        coverArt?.let { joinPath(coverDir, "$it.jpg") }?.takeIf { Files.exists(it) }

    fun isDownloaded(songId: String) = localPath(songId) != null

    fun enqueue(songs: List<Song>) {
        val now = nowMillis()
        val fmt = settings().downloadFormat
        store.update { s ->
            val add = songs.filter { s.items[it.id]?.status != DownloadStatus.DONE }
                .associate { it.id to DownloadItem(it, addedAt = now, format = fmt) }
            s.copy(items = s.items + add)
        }
        scope.launch { pump() }
    }

    fun remove(songIds: Collection<String>) {
        songIds.forEach { id ->
            running.remove(id)?.cancel()
            store.value.items[id]?.path?.let { Files.delete(it) }
        }
        store.update { s -> s.copy(items = s.items - songIds.toSet()) }
    }

    fun retryFailed() {
        store.update { s -> s.copy(items = s.items.mapValues { (_, v) -> if (v.status == DownloadStatus.FAILED) v.copy(status = DownloadStatus.QUEUED, error = null) else v }) }
        scope.launch { pump() }
    }

    fun totalBytes(): Long = store.value.items.values.sumOf { it.bytes }

    private fun patch(id: String, f: (DownloadItem) -> DownloadItem) =
        store.update { s -> s.items[id]?.let { s.copy(items = s.items + (id to f(it))) } ?: s }

    private suspend fun pump() {
        lock.withLock { pumpLocked() }
    }

    private fun pumpLocked() {
        val a = api() ?: return
        store.value.items.values.filter { it.status == DownloadStatus.QUEUED && it.song.id !in running }.forEach { item ->
            running[item.song.id] = scope.launch(Dispatchers.Default) {
                semaphore.withPermit { download(a, item) }
                running.remove(item.song.id)
            }
        }
    }

    private suspend fun download(api: SubsonicApi, item: DownloadItem) {
        val song = item.song
        val s = settings()
        val raw = item.format == "raw"
        val ext = if (raw) (song.suffix ?: "audio") else item.format
        val target = joinPath(dir, "${song.id}.$ext")
        val tmp = "$target.part"
        val url = if (raw) api.downloadUrl(song.id) else api.streamUrl(song.id, s.downloadMaxBitrate, item.format)
        patch(song.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f, error = null) }
        try {
            http.prepareGet(url).execute { resp ->
                if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
                val total = resp.contentLength() ?: song.size ?: -1L
                val ch = resp.bodyAsChannel()
                val sink = Files.openWriter(tmp)
                var done = 0L
                var lastReport = 0L
                val buf = ByteArray(64 * 1024)
                try {
                    while (scope.isActive) {
                        val n = ch.readAvailable(buf, 0, buf.size)
                        if (n == -1) break
                        if (n > 0) {
                            sink.write(buf, 0, n)
                            done += n
                            if (done - lastReport > 512 * 1024) {
                                lastReport = done
                                val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                                patch(song.id) { it.copy(progress = p, bytes = done) }
                            }
                        }
                    }
                } finally {
                    sink.close()
                }
                Files.rename(tmp, target)
                patch(song.id) { it.copy(status = DownloadStatus.DONE, path = target, progress = 1f, bytes = done) }
            }
            downloadCover(api, song.coverArt)
        } catch (e: Throwable) {
            Files.delete(tmp)
            if (e is kotlinx.coroutines.CancellationException) throw e
            patch(song.id) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: e::class.simpleName) }
        }
    }

    private suspend fun downloadCover(api: SubsonicApi, coverArt: String?) {
        val id = coverArt ?: return
        val path = joinPath(coverDir, "$id.jpg")
        if (Files.exists(path)) return
        val url = api.coverArtUrl(id, 1000) ?: return
        runCatching {
            http.prepareGet(url).execute { resp ->
                if (!resp.status.isSuccess()) return@execute
                val ch = resp.bodyAsChannel()
                val sink = Files.openWriter("$path.part")
                val buf = ByteArray(32 * 1024)
                try {
                    while (true) {
                        val n = ch.readAvailable(buf, 0, buf.size)
                        if (n == -1) break
                        if (n > 0) sink.write(buf, 0, n)
                    }
                } finally { sink.close() }
                Files.rename("$path.part", path)
            }
        }
    }
}
