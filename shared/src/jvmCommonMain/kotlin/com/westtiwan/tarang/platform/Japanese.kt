package com.westtiwan.tarang.platform

import com.atilika.kuromoji.ipadic.Tokenizer

private val tokenizer: Tokenizer? by lazy { runCatching { Tokenizer() }.getOrNull() }

actual fun japaneseReadings(text: String): List<JaToken>? {
    val t = tokenizer ?: return null
    return runCatching {
        t.tokenize(text).map { tok ->
            val pos = tok.partOfSpeechLevel1
            val reading = tok.reading?.takeIf { it != "*" }
            JaToken(
                surface = tok.surface,
                reading = reading,
                particle = pos == "助詞",
                // Auxiliary verbs and suffixes read better joined to the word before them.
                attach = pos == "助動詞" || tok.partOfSpeechLevel2 == "接尾" || tok.surface == "ー",
            )
        }
    }.getOrNull()
}
