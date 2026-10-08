package com.westtiwan.tarang.lyrics

import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.data.SubsonicApi
import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.joinPath
import com.westtiwan.tarang.platform.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class LyricLine(val startMs: Long? = null, val text: String)

@Serializable
data class LyricsDoc(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
    val lang: String? = null,
) {
    val plainText: String get() = lines.joinToString("\n") { it.text }

    /** Index of the line that should be highlighted at [positionMs]. */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var idx = -1
        for (i in lines.indices) {
            val s = lines[i].startMs ?: continue
            if (s <= positionMs) idx = i else break
        }
        return idx
    }
}

object Lrc {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(text: String): LyricsDoc? {
        if (text.isBlank()) return null
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        for (raw in text.lines()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) {
                if (!raw.trim().matches(Regex("""\[[a-zA-Z]+:.*]"""))) plain += LyricLine(null, raw.trim())
                continue
            }
            val content = raw.substring(tags.last().range.last + 1).trim()
            for (t in tags) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val fracStr = t.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                timed += LyricLine(maxOf(0, min * 60_000 + sec * 1000 + frac - offset), content)
            }
        }
        return if (timed.isNotEmpty()) LyricsDoc(timed.sortedBy { it.startMs }, true, "lrc")
        else LyricsDoc(trimBlank(plain), false, "text")
    }

    private fun trimBlank(lines: List<LyricLine>) = lines.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
}

/**
 * Finds lyrics for a song: OpenSubsonic structured lyrics from the server first, then the legacy
 * getLyrics endpoint, then LRCLIB. Results are cached on disk per song.
 */
class LyricsService(
    private val http: HttpClient,
    private val cacheDir: String,
) {
    init { Files.mkdirs(cacheDir) }

    private fun cachePath(song: Song) = joinPath(cacheDir, "${song.id}.json")

    fun cached(song: Song): LyricsDoc? = runCatching {
        Files.readText(cachePath(song))?.let { json.decodeFromString(LyricsDoc.serializer(), it) }
    }.getOrNull()

    fun clearCache(song: Song) { Files.delete(cachePath(song)) }

    suspend fun load(song: Song, api: SubsonicApi?, sources: List<String>, force: Boolean = false): LyricsDoc? {
        if (!force) cached(song)?.let { return it.takeIf { d -> d.lines.isNotEmpty() } }
        var best: LyricsDoc? = null
        for (src in sources) {
            val doc = runCatching {
                when (src) {
                    "server" -> api?.let { fromServer(song, it) }
                    "lrclib" -> fromLrclib(song)
                    else -> null
                }
            }.getOrNull() ?: continue
            if (doc.synced) { best = doc; break }
            if (best == null) best = doc
        }
        Files.writeText(cachePath(song), json.encodeToString(LyricsDoc.serializer(), best ?: LyricsDoc(emptyList(), false, "none")))
        return best
    }

    private suspend fun fromServer(song: Song, api: SubsonicApi): LyricsDoc? {
        val structured = runCatching { api.getLyricsBySongId(song.id) }.getOrDefault(emptyList())
        val pick = structured.firstOrNull { it.synced && it.line.isNotEmpty() } ?: structured.firstOrNull { it.line.isNotEmpty() }
        if (pick != null) {
            val lines = pick.line.map { LyricLine(if (pick.synced) maxOf(0, (it.start ?: 0) - pick.offset) else null, it.value) }
            return LyricsDoc(lines, pick.synced, "server", pick.lang?.takeIf { it != "xxx" && it != "und" })
        }
        val legacy = runCatching { api.getLyrics(song.artist, song.title) }.getOrNull()
        return legacy?.value?.takeIf { it.isNotBlank() }?.let { Lrc.parse(it)?.copy(source = "server") }
    }

    private suspend fun fromLrclib(song: Song): LyricsDoc? {
        val artist = song.artist ?: return null
        val params = buildString {
            append("artist_name=").append(urlEncode(artist))
            append("&track_name=").append(urlEncode(song.title))
            song.album?.let { append("&album_name=").append(urlEncode(it)) }
            song.duration?.let { append("&duration=").append(it) }
        }
        val res = http.get("https://lrclib.net/api/get?$params") { header("User-Agent", "Tarang (https://github.com/West-Tiwan)") }
        val obj: JsonObject? = if (res.status.isSuccess()) json.parseToJsonElement(res.bodyAsText()).jsonObject else {
            // Fall back to search which tolerates small metadata differences.
            val q = "q=" + urlEncode("$artist ${song.title}")
            val sr = http.get("https://lrclib.net/api/search?$q") { header("User-Agent", "Tarang") }
            if (!sr.status.isSuccess()) null else {
                val arr = json.parseToJsonElement(sr.bodyAsText()) as? JsonArray
                val d = song.duration ?: 0
                arr?.map { it.jsonObject }?.minByOrNull { o ->
                    val od = o["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val synced = if (o["syncedLyrics"]?.jsonPrimitive?.content.isNullOrBlank() || o["syncedLyrics"].toString() == "null") 1000 else 0
                    kotlin.math.abs(od - d) + synced
                }
            }
        }
        obj ?: return null
        val synced = obj["syncedLyrics"]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content
        val plain = obj["plainLyrics"]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content
        return (synced?.let { Lrc.parse(it) } ?: plain?.let { Lrc.parse(it) })?.copy(source = "lrclib")
    }
}

/** Translates lyric lines. Implementations: on device ML Kit on Android, LibreTranslate anywhere. */
interface Translator {
    val name: String
    suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String>
}

class LibreTranslator(private val http: HttpClient, private val baseUrl: String, private val apiKey: String) : Translator {
    override val name = "LibreTranslate"

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val body = JsonObject(
            buildMap {
                put("q", JsonArray(lines.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("source", kotlinx.serialization.json.JsonPrimitive(sourceLang ?: "auto"))
                put("target", kotlinx.serialization.json.JsonPrimitive(targetLang))
                put("format", kotlinx.serialization.json.JsonPrimitive("text"))
                if (apiKey.isNotBlank()) put("api_key", kotlinx.serialization.json.JsonPrimitive(apiKey))
            },
        )
        val res = http.post(baseUrl.trimEnd('/') + "/translate") {
            header("Content-Type", "application/json")
            setBody(body.toString())
        }
        val obj = json.parseToJsonElement(res.bodyAsText()).jsonObject
        val t = obj["translatedText"] ?: error(obj["error"]?.jsonPrimitive?.content ?: "Translation failed")
        return if (t is JsonArray) t.jsonArray.map { it.jsonPrimitive.content } else listOf(t.jsonPrimitive.content)
    }
}

data class LyricsView(
    val doc: LyricsDoc,
    val romanized: List<String>?,
    val translated: List<String>?,
    val script: Script,
    val isHinglish: Boolean,
)

/** Adds pronunciation (romanization) and translation on top of a lyrics document. */
class LyricsProcessor {
    fun romanize(doc: LyricsDoc): Pair<Script, List<String>?> {
        val script = detectScript(doc.plainText)
        if (script == Script.LATIN) return script to null
        return script to doc.lines.map { Hinglish.romanize(it.text, script) }
    }

    /** True when the song is probably not in [target], so translation is worth showing. */
    fun isForeign(doc: LyricsDoc, target: String): Boolean {
        val script = detectScript(doc.plainText)
        if (script == Script.LATIN) {
            if (looksLikeHinglish(doc.plainText)) return target != "hi"
            val lang = doc.lang?.take(2)
            return lang != null && lang != "en" && lang != target
        }
        return script.lang != target
    }

    suspend fun translate(doc: LyricsDoc, translator: Translator, target: String): List<String> {
        val texts = doc.lines.map { it.text }
        val script = detectScript(doc.plainText)
        val hinglish = script == Script.LATIN && looksLikeHinglish(doc.plainText)
        val (input, source) = when {
            hinglish -> texts.map { Hinglish.hinglishToDevanagari(it) } to "hi"
            script == Script.GURMUKHI -> texts to "pa"
            script.lang != null -> texts to (doc.lang?.take(2)?.takeIf { it != "xx" } ?: script.lang)
            else -> texts to doc.lang?.take(2)
        }
        // Translate in chunks, keeping blank lines aligned.
        val nonBlank = input.withIndex().filter { it.value.isNotBlank() }
        val result = Array(texts.size) { "" }
        nonBlank.chunked(40).forEach { chunk ->
            val out = translator.translate(chunk.map { it.value }, source, target)
            chunk.forEachIndexed { i, iv -> result[iv.index] = out.getOrElse(i) { "" } }
        }
        return result.toList()
    }
}
