package com.westtiwan.tarang.platform

/** Small set of platform hooks. Everything else in the shared module is platform free. */
expect object Platform {
    val name: String
    fun dataDir(): String
    fun cacheDir(): String
    fun isUnmeteredNetwork(): Boolean
    fun isOnline(): Boolean
}

expect object Files {
    fun readText(path: String): String?
    fun writeText(path: String, text: String)
    fun exists(path: String): Boolean
    fun delete(path: String): Boolean
    fun size(path: String): Long
    fun mkdirs(path: String)
    fun list(path: String): List<String>
    fun rename(from: String, to: String): Boolean
    fun openWriter(path: String, append: Boolean = false): FileSink
}

expect class FileSink {
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun close()
}

expect fun md5Hex(input: String): String
expect fun nowMillis(): Long
expect fun urlEncode(value: String): String

/** ICU based romanization for scripts the built in Indic transliterator does not cover. */
expect fun anyToLatin(text: String): String

/** One morpheme of Japanese text with its katakana reading. */
data class JaToken(val surface: String, val reading: String?, val particle: Boolean, val attach: Boolean)

/** Splits Japanese into words with readings (Kuromoji on JVM). Null when unavailable. */
expect fun japaneseReadings(text: String): List<JaToken>?

fun joinPath(vararg parts: String): String = parts.joinToString("/") { it.trimEnd('/') }
