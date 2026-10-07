package com.westtiwan.tarang.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class LibraryIndex(
    val serverId: String = "",
    val syncedAt: Long = 0,
    val songs: List<Song> = emptyList(),
)

data class SyncProgress(val running: Boolean = false, val fetched: Int = 0, val error: String? = null)

/**
 * Full song index pulled from the server. Powers smart playlists, offline search and fast song lists.
 */
class LibrarySync(private val store: JsonStore<LibraryIndex>) {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress
    val index: StateFlow<LibraryIndex> get() = store.state

    suspend fun sync(api: SubsonicApi, now: Long) {
        if (_progress.value.running) return
        _progress.value = SyncProgress(running = true)
        try {
            val all = ArrayList<Song>()
            val page = 500
            var offset = 0
            while (true) {
                val batch = api.search("", artistCount = 0, albumCount = 0, songCount = page, songOffset = offset).song
                all += batch
                offset += batch.size
                _progress.value = SyncProgress(running = true, fetched = all.size)
                if (batch.size < page) break
            }
            store.set(LibraryIndex(api.server.id, now, all.distinctBy { it.id }))
            store.flush()
            _progress.value = SyncProgress(running = false, fetched = all.size)
        } catch (e: Exception) {
            _progress.value = SyncProgress(running = false, fetched = 0, error = e.message)
        }
    }

    fun updateSong(song: Song) {
        store.update { idx -> idx.copy(songs = idx.songs.map { if (it.id == song.id) song else it }) }
    }

    fun patch(id: String, transform: (Song) -> Song) {
        store.update { idx -> idx.copy(songs = idx.songs.map { if (it.id == id) transform(it) else it }) }
    }
}
