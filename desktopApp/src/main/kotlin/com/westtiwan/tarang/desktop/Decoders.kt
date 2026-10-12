package com.westtiwan.tarang.desktop

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import org.jflac.FLACDecoder
import org.jflac.util.ByteData
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

data class PcmFormat(val sampleRate: Int, val channels: Int, val bitDepth: Int, val codec: String)

/** Pulls interleaved float PCM in the range -1..1 out of a compressed stream. */
interface PcmDecoder : AutoCloseable {
    val format: PcmFormat
    /** Fills [out] and returns the number of samples written, or -1 at the end. */
    fun read(out: FloatArray): Int
    /** Skips forward by decoding and discarding until [frames] sample frames have passed. */
    fun skipFrames(frames: Long) {
        val buf = FloatArray(8192)
        var left = frames * format.channels
        while (left > 0) {
            val n = read(buf)
            if (n < 0) return
            left -= n
        }
    }
}

object Decoders {
    val nativeFormats = setOf("flac", "mp3", "wav", "aif", "aiff")

    fun open(input: InputStream, suffix: String): PcmDecoder = when (suffix.lowercase()) {
        "flac" -> FlacDecoder(input)
        "mp3" -> Mp3Decoder(input)
        else -> JavaSoundDecoder(input, suffix)
    }

    /** Sniffs the container from its magic bytes when the suffix is unknown or wrong. */
    fun sniff(file: File): String? = runCatching {
        val head = ByteArray(12)
        file.inputStream().use { it.read(head) }
        when {
            head.copyOfRange(0, 4).contentEquals("fLaC".toByteArray()) -> "flac"
            head.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) || (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            head.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) -> "wav"
            head.copyOfRange(0, 4).contentEquals("FORM".toByteArray()) -> "aiff"
            else -> null
        }
    }.getOrNull()
}

private class FlacDecoder(input: InputStream) : PcmDecoder {
    private val decoder = FLACDecoder(BufferedInputStream(input, 1 shl 16))
    override val format: PcmFormat
    private var pcm: ByteData? = null
    private var pending = FloatArray(0)
    private var pendingPos = 0
    private var pendingLen = 0

    init {
        decoder.readMetadata()
        val info = decoder.streamInfo ?: error("Not a FLAC stream")
        format = PcmFormat(info.sampleRate, info.channels, info.bitsPerSample, "flac")
    }

    override fun read(out: FloatArray): Int {
        if (pendingPos >= pendingLen && !decodeFrame()) return -1
        val n = minOf(out.size, pendingLen - pendingPos)
        System.arraycopy(pending, pendingPos, out, 0, n)
        pendingPos += n
        return n
    }

    private fun decodeFrame(): Boolean {
        val frame = decoder.readNextFrame() ?: return false
        val data = decoder.decodeFrame(frame, pcm)
        pcm = data
        val bytes = data.data
        val len = data.len
        val bps = (format.bitDepth + 7) / 8
        val samples = len / bps
        if (pending.size < samples) pending = FloatArray(samples)
        val scale = 1f / (1L shl (format.bitDepth - 1)).toFloat()
        var i = 0
        var o = 0
        while (o < samples) {
            val v = when (bps) {
                1 -> (bytes[i].toInt() and 0xff) - 128
                2 -> (bytes[i].toInt() and 0xff) or (bytes[i + 1].toInt() shl 8)
                3 -> (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8) or (bytes[i + 2].toInt() shl 16)
                else -> (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8) or ((bytes[i + 2].toInt() and 0xff) shl 16) or (bytes[i + 3].toInt() shl 24)
            }
            pending[o++] = v * scale
            i += bps
        }
        pendingPos = 0
        pendingLen = samples
        return true
    }

    override fun close() {}
}

private class Mp3Decoder(input: InputStream) : PcmDecoder {
    private val stream = BufferedInputStream(input, 1 shl 16)
    private val bitstream = Bitstream(stream)
    private val decoder = Decoder()
    override val format: PcmFormat
    private var pending = FloatArray(0)
    private var pendingPos = 0
    private var pendingLen = 0

    init {
        // Decode the first frame to learn the format.
        if (!decodeFrame()) error("Empty MP3 stream")
        format = PcmFormat(decoder.outputFrequency, decoder.outputChannels, 16, "mp3")
    }

    override fun read(out: FloatArray): Int {
        if (pendingPos >= pendingLen && !decodeFrame()) return -1
        val n = minOf(out.size, pendingLen - pendingPos)
        System.arraycopy(pending, pendingPos, out, 0, n)
        pendingPos += n
        return n
    }

    private fun decodeFrame(): Boolean {
        while (true) {
            val header = bitstream.readFrame() ?: return false
            try {
                val buf = decoder.decodeFrame(header, bitstream) as SampleBuffer
                val len = buf.bufferLength
                if (pending.size < len) pending = FloatArray(len)
                val src = buf.buffer
                for (i in 0 until len) pending[i] = src[i] / 32768f
                pendingPos = 0
                pendingLen = len
                return true
            } catch (_: Exception) {
                // Corrupt frame: skip it.
            } finally {
                bitstream.closeFrame()
            }
        }
    }

    override fun skipFrames(frames: Long) {
        // Skipping whole MP3 frames without decoding them is much faster.
        var left = frames
        val rate = runCatching { format.sampleRate }.getOrDefault(44100)
        while (left > 0) {
            val header = bitstream.readFrame() ?: return
            val perFrame = (header.ms_per_frame() * rate / 1000).toLong().coerceAtLeast(1)
            bitstream.closeFrame()
            left -= perFrame
        }
        pendingPos = 0
        pendingLen = 0
    }

    override fun close() { runCatching { bitstream.close() } }
}

private class JavaSoundDecoder(input: InputStream, suffix: String) : PcmDecoder {
    private val ais = AudioSystem.getAudioInputStream(BufferedInputStream(input, 1 shl 16))
    private val src: AudioFormat = ais.format
    override val format = PcmFormat(src.sampleRate.toInt(), src.channels, src.sampleSizeInBits, suffix.lowercase())
    private val bytes = ByteArray(16384)

    override fun read(out: FloatArray): Int {
        val bps = (src.sampleSizeInBits + 7) / 8
        val want = minOf(bytes.size / bps, out.size) * bps
        val n = ais.read(bytes, 0, want)
        if (n <= 0) return -1
        val big = src.isBigEndian
        val samples = n / bps
        val scale = 1f / (1L shl (src.sampleSizeInBits - 1)).toFloat()
        val unsigned = src.encoding == AudioFormat.Encoding.PCM_UNSIGNED
        for (s in 0 until samples) {
            var v = 0
            for (b in 0 until bps) {
                val byte = bytes[s * bps + (if (big) b else bps - 1 - b)].toInt()
                v = if (b == 0) byte else (v shl 8) or (byte and 0xff)
            }
            if (unsigned) v -= 1 shl (src.sampleSizeInBits - 1)
            out[s] = v * scale
        }
        return samples
    }

    override fun close() = ais.close()
}
