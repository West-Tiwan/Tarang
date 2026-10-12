package com.westtiwan.tarang.desktop

import java.io.File
import java.io.RandomAccessFile
import java.time.LocalTime

/** Development log, only active when the tarang.debugLog system property points at a file (set by gradle run). */
object DevLog {
    private val file = System.getProperty("tarang.debugLog")?.let(::File)

    fun log(msg: String) {
        val f = file ?: return
        runCatching { f.appendText("${LocalTime.now().withNano(0)} $msg\n") }
    }
}

/** Reads the TITLE / ARTIST tags from the start of a FLAC or MP3 file, to check the server sent the right audio. */
object TagPeek {
    fun read(file: File): Map<String, String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(minOf(raf.length(), 512 * 1024).toInt())
            raf.readFully(head)
            when {
                head.size > 4 && String(head, 0, 4) == "fLaC" -> flac(head)
                head.size > 10 && String(head, 0, 3) == "ID3" -> id3(head)
                else -> emptyMap()
            }
        }
    }.getOrDefault(emptyMap())

    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun flac(b: ByteArray): Map<String, String> {
        var o = 4
        while (o + 4 <= b.size) {
            val hdr = b[o].toInt() and 0xff
            val type = hdr and 0x7f
            val len = ((b[o + 1].toInt() and 0xff) shl 16) or ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
            o += 4
            if (type == 4 && o + len <= b.size) {
                var p = o
                val vendor = le32(b, p); p += 4 + vendor
                val count = le32(b, p); p += 4
                val out = HashMap<String, String>()
                repeat(count) {
                    val l = le32(b, p); p += 4
                    val kv = String(b, p, l, Charsets.UTF_8); p += l
                    val k = kv.substringBefore('=').uppercase()
                    if (k == "TITLE" || k == "ARTIST" || k == "ALBUM") out.putIfAbsent(k, kv.substringAfter('='))
                }
                return out
            }
            if (hdr and 0x80 != 0) break
            o += len
        }
        return emptyMap()
    }

    private fun id3(b: ByteArray): Map<String, String> {
        val ver = b[3].toInt()
        val size = ((b[6].toInt() and 0x7f) shl 21) or ((b[7].toInt() and 0x7f) shl 14) or ((b[8].toInt() and 0x7f) shl 7) or (b[9].toInt() and 0x7f)
        var o = 10
        val out = HashMap<String, String>()
        while (o + 10 < minOf(b.size, size + 10)) {
            val id = String(b, o, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val fs = if (ver >= 4) ((b[o + 4].toInt() and 0x7f) shl 21) or ((b[o + 5].toInt() and 0x7f) shl 14) or ((b[o + 6].toInt() and 0x7f) shl 7) or (b[o + 7].toInt() and 0x7f)
            else ((b[o + 4].toInt() and 0xff) shl 24) or ((b[o + 5].toInt() and 0xff) shl 16) or ((b[o + 6].toInt() and 0xff) shl 8) or (b[o + 7].toInt() and 0xff)
            val data = o + 10
            if (fs <= 0 || data + fs > b.size) break
            val key = when (id) { "TIT2" -> "TITLE"; "TPE1" -> "ARTIST"; "TALB" -> "ALBUM"; else -> null }
            if (key != null) {
                val enc = b[data].toInt()
                val cs = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                out[key] = String(b, data + 1, fs - 1, cs).trim('\u0000', ' ')
            }
            o = data + fs
        }
        return out
    }
}
