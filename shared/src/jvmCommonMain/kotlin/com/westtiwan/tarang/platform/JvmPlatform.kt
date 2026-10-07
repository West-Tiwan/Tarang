package com.westtiwan.tarang.platform

import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest

actual object Files {
    actual fun readText(path: String): String? = File(path).takeIf { it.exists() }?.readText()
    actual fun writeText(path: String, text: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun delete(path: String): Boolean = File(path).let { if (it.isDirectory) it.deleteRecursively() else it.delete() }
    actual fun size(path: String): Long = File(path).let { f ->
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    }
    actual fun mkdirs(path: String) { File(path).mkdirs() }
    actual fun list(path: String): List<String> = File(path).list()?.toList() ?: emptyList()
    actual fun rename(from: String, to: String): Boolean {
        val t = File(to)
        if (t.exists()) t.delete()
        return File(from).renameTo(t)
    }
    actual fun openWriter(path: String, append: Boolean): FileSink {
        File(path).parentFile?.mkdirs()
        return FileSink(FileOutputStream(path, append))
    }
}

actual class FileSink(private val out: FileOutputStream) {
    actual fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
    actual fun close() = out.close()
}

actual fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
