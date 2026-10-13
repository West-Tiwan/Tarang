package com.westtiwan.tarang.lyrics

import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.platform.Files
import com.westtiwan.tarang.platform.joinPath
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val languageNames = mapOf(
    "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "hi" to "Hindi", "pa" to "Punjabi", "ur" to "Urdu",
    "es" to "Spanish", "fr" to "French", "de" to "German", "ta" to "Tamil", "te" to "Telugu", "bn" to "Bengali", "mr" to "Marathi",
)

fun languageName(code: String?) = code?.let { languageNames[it.take(2)] ?: it }

/**
 * Google Translate's free web endpoint. No key needed. The whole song goes in one request so
 * neighbouring lines give each other context, then it is split back into lines.
 */
class GoogleTranslator(private val http: HttpClient) : Translator {
    override val name = "Google Translate"
    override val contextual = true

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val whole = request(lines.joinToString("\n"), sourceLang, targetLang).split("\n").map { it.trim() }
        if (whole.size == lines.size) return whole
        // Line breaks got merged somewhere: fall back to one request per line.
        return lines.map { request(it, sourceLang, targetLang).trim() }
    }

    private suspend fun request(text: String, source: String?, target: String): String {
        val res = http.submitForm(
            url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=${source ?: "auto"}&tl=$target",
            formParameters = parameters { append("q", text) },
        )
        if (!res.status.isSuccess()) error("Google Translate returned ${res.status.value}")
        val root = json.parseToJsonElement(res.bodyAsText()).jsonArray
        val segments = root[0] as? JsonArray ?: return ""
        return segments.joinToString("") { seg -> (seg as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.content } ?: "" }
    }
}

private fun lyricsPrompt(lines: List<String>, source: String?, target: String): String = buildString {
    append("You translate song lyrics. Translate each line of these ")
    append(languageName(source) ?: "")
    append(" lyrics into natural, fluent ")
    append(languageName(target))
    append(". Read the whole song first so each line fits the story and mood. Keep exactly one output line per input line, ")
    append("in the same order, never merge or split lines, keep names as they are, and add no notes. ")
    append("Reply with only a JSON array of ${lines.size} strings.\n\n")
    append(json.encodeToString(ListSerializer(String.serializer()), lines))
}

private fun parseArray(text: String, expected: Int): List<String> {
    val start = text.indexOf('[')
    val end = text.lastIndexOf(']')
    if (start < 0 || end <= start) error("The model did not return a list")
    val arr = json.parseToJsonElement(text.substring(start, end + 1)).jsonArray.map { (it as? JsonPrimitive)?.content ?: "" }
    if (arr.size != expected) error("The model returned ${arr.size} lines instead of $expected")
    return arr
}

/** Google Gemini, free tier with a key from aistudio.google.com. Best quality for lyrics. */
class GeminiTranslator(private val http: HttpClient, private val key: String, private val model: String) : Translator {
    override val name = "Gemini"
    override val contextual = true

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", lyricsPrompt(lines, sourceLang, targetLang)) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            })
        }
        val res = http.post("https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { "gemini-flash-latest" }}:generateContent") {
            header("x-goog-api-key", key)
            header("Content-Type", "application/json")
            setBody(body.toString())
        }
        val obj = json.parseToJsonElement(res.bodyAsText()).jsonObject
        obj["error"]?.let { error("Gemini: " + (it.jsonObject["message"]?.jsonPrimitive?.content ?: "request failed")) }
        val text = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" } ?: error("Gemini returned nothing")
        return parseArray(text, lines.size)
    }
}

/** Any OpenAI style chat endpoint: Ollama, LM Studio, OpenRouter, Groq and so on. */
class OpenAiTranslator(private val http: HttpClient, private val baseUrl: String, private val key: String, private val model: String) : Translator {
    override val name = "AI ($model)"
    override val contextual = true

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.3)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", lyricsPrompt(lines, sourceLang, targetLang)) })
            })
        }
        val res = http.post(baseUrl.trimEnd('/') + "/chat/completions") {
            if (key.isNotBlank()) header("Authorization", "Bearer $key")
            header("Content-Type", "application/json")
            setBody(body.toString())
        }
        val obj = json.parseToJsonElement(res.bodyAsText()).jsonObject
        obj["error"]?.let { e -> error((e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e.toString()) }
        val text = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: error("No reply from the model")
        return parseArray(text, lines.size)
    }
}

/** DeepL API Free (500k characters a month). The full song is passed as context. */
class DeepLTranslator(private val http: HttpClient, private val key: String) : Translator {
    override val name = "DeepL"
    override val contextual = true

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val target = when (targetLang) { "en" -> "EN-US"; "pt" -> "PT-BR"; "zh" -> "ZH-HANS"; else -> targetLang.uppercase() }
        val body = buildJsonObject {
            put("text", JsonArray(lines.map { JsonPrimitive(it) }))
            sourceLang?.let { put("source_lang", it.take(2).uppercase()) }
            put("target_lang", target)
            put("context", lines.joinToString("\n"))
        }
        val host = if (key.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        val res = http.post("https://$host/v2/translate") {
            header("Authorization", "DeepL-Auth-Key $key")
            header("Content-Type", "application/json")
            setBody(body.toString())
        }
        if (!res.status.isSuccess()) error("DeepL returned ${res.status.value}")
        return json.parseToJsonElement(res.bodyAsText()).jsonObject["translations"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content }
    }
}

/** Tries each translator in turn, so a missing key or a network hiccup still gives a result. */
class FallbackTranslator(private val chain: List<Translator>) : Translator {
    override val name: String get() = chain.firstOrNull()?.name ?: "None"
    override val contextual: Boolean get() = chain.firstOrNull()?.contextual ?: false
    var usedName: String? = null
        private set

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        var last: Throwable? = null
        for (t in chain) {
            try {
                return t.translate(lines, sourceLang, targetLang).also { usedName = t.name }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("No translator configured")
    }
}

/** Translations saved per song, engine and language so a song is only translated once. */
class TranslationCache(private val dir: String) {
    init { Files.mkdirs(dir) }
    private fun path(songId: String, engine: String, target: String) = joinPath(dir, "$songId.${engine.filter { it.isLetterOrDigit() }}.$target.json")

    fun get(songId: String, engine: String, target: String): List<String>? = runCatching {
        Files.readText(path(songId, engine, target))?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }
    }.getOrNull()

    fun put(songId: String, engine: String, target: String, lines: List<String>) {
        Files.writeText(path(songId, engine, target), json.encodeToString(ListSerializer(String.serializer()), lines))
    }

    fun clear(songId: String) {
        Files.list(dir).filter { it.startsWith("$songId.") }.forEach { Files.delete(joinPath(dir, it)) }
    }
}
