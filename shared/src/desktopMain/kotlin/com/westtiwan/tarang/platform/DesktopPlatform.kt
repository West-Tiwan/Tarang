package com.westtiwan.tarang.platform

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

actual object Platform {
    actual val name: String = "Desktop"

    private val base: File by lazy {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            os.contains("mac") -> File(home, "Library/Application Support/Tarang")
            os.contains("win") -> File(System.getenv("APPDATA") ?: home, "Tarang")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "tarang")
        }
        dir.apply { mkdirs() }
    }

    actual fun dataDir(): String = base.absolutePath
    actual fun cacheDir(): String = File(base, "cache").apply { mkdirs() }.absolutePath
    actual fun isUnmeteredNetwork(): Boolean = true
    actual fun isOnline(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 1500) }
        true
    }.getOrDefault(true)
}

actual fun anyToLatin(text: String): String =
    runCatching { com.ibm.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(text) }.getOrDefault(text)
