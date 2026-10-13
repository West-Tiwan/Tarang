package com.westtiwan.tarang.art

import com.westtiwan.tarang.data.JsonStore
import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.joinPath
import com.westtiwan.tarang.platform.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ArtistArtState(
    /** Artist id to an image URL or a file:// path of an uploaded image. */
    val images: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0,
)

data class ArtCandidate(val url: String, val label: String, val source: String)

/**
 * Lets the user replace an artist picture, either with one found online or an uploaded image.
 * Web picks sync through the server with the rest of the settings, uploads stay on the device.
 */
class ArtistArt(
    private val store: JsonStore<ArtistArtState>,
    private val http: HttpClient,
    private val dir: String,
    private val now: () -> Long,
) {
    val state: StateFlow<ArtistArtState> get() = store.state

    init { Files.mkdirs(dir) }

    fun imageFor(artistId: String?): String? = artistId?.let { store.value.images[it] }

    fun setUrl(artistId: String, url: String) = store.update { it.copy(images = it.images + (artistId to url), updatedAt = now()) }

    fun setUpload(artistId: String, bytes: ByteArray): String {
        // A fresh file name each time so image caches pick up the change.
        imageFor(artistId)?.takeIf { it.startsWith("file://") }?.let { Files.delete(it.removePrefix("file://")) }
        val path = joinPath(dir, "$artistId-${now()}.img")
        val sink = Files.openWriter(path)
        sink.write(bytes, 0, bytes.size)
        sink.close()
        setUrl(artistId, "file://$path")
        return path
    }

    fun reset(artistId: String) {
        imageFor(artistId)?.takeIf { it.startsWith("file://") }?.let { Files.delete(it.removePrefix("file://")) }
        store.update { it.copy(images = it.images - artistId, updatedAt = now()) }
    }

    /** Synced copy without local uploads. */
    fun syncable(): Map<String, String> = store.value.images.filterValues { it.startsWith("http") }

    fun merge(remote: Map<String, String>, remoteUpdatedAt: Long) {
        if (remoteUpdatedAt <= store.value.updatedAt) return
        store.update { s ->
            val uploads = s.images.filterValues { it.startsWith("file://") }
            s.copy(images = remote + uploads, updatedAt = remoteUpdatedAt)
        }
    }

    suspend fun search(name: String): List<ArtCandidate> = coroutineScope {
        val deezer = async { runCatching { deezer(name) }.getOrDefault(emptyList()) }
        val audioDb = async { runCatching { audioDb(name) }.getOrDefault(emptyList()) }
        deezer.await() + audioDb.await()
    }

    private suspend fun deezer(name: String): List<ArtCandidate> {
        val res = http.get("https://api.deezer.com/search/artist?q=${urlEncode(name)}&limit=12")
        if (!res.status.isSuccess()) return emptyList()
        val data = json.parseToJsonElement(res.bodyAsText()).jsonObject["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val o = el.jsonObject
            val pic = o["picture_xl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && !it.contains("/artist//") } ?: return@mapNotNull null
            val fans = o["nb_fan"]?.jsonPrimitive?.content ?: ""
            ArtCandidate(pic, o["name"]?.jsonPrimitive?.content ?: name, "Deezer" + if (fans.isNotBlank()) " • $fans fans" else "")
        }
    }

    private suspend fun audioDb(name: String): List<ArtCandidate> {
        val res = http.get("https://www.theaudiodb.com/api/v1/json/2/search.php?s=${urlEncode(name)}") { header("User-Agent", "Tarang") }
        if (!res.status.isSuccess()) return emptyList()
        val arr = json.parseToJsonElement(res.bodyAsText()).jsonObject["artists"] as? JsonArray ?: return emptyList()
        return arr.flatMap { el ->
            val o = el.jsonObject
            val n = o["strArtist"]?.jsonPrimitive?.content ?: name
            listOf("strArtistThumb" to "TheAudioDB", "strArtistWideThumb" to "TheAudioDB wide", "strArtistFanart" to "TheAudioDB fanart")
                .mapNotNull { (k, label) ->
                    o[k]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }?.let { ArtCandidate(it, n, label) }
                }
        }
    }
}
