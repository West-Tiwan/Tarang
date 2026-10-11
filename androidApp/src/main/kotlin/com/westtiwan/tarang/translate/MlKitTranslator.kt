package com.westtiwan.tarang.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.westtiwan.tarang.lyrics.Translator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** On device translation with Google ML Kit. Models (about 30 MB per language) download on first use. */
class MlKitTranslator : Translator {
    override val name = "ML Kit (on device)"

    override suspend fun translate(lines: List<String>, sourceLang: String?, targetLang: String): List<String> {
        val src = sourceLang?.let { TranslateLanguage.fromLanguageTag(it) } ?: run {
            val detected = LanguageIdentification.getClient().identifyLanguage(lines.joinToString("\n").take(2000)).await()
            if (detected == "und") error("Could not detect the lyrics language") else TranslateLanguage.fromLanguageTag(detected)
        } ?: error("Language not supported by on-device translation")
        val dst = TranslateLanguage.fromLanguageTag(targetLang) ?: error("Target language not supported")
        if (src == dst) return lines
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build())
        try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return lines.map { if (it.isBlank()) it else client.translate(it).await() }
        } finally {
            client.close()
        }
    }
}
