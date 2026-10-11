package com.westtiwan.tarang.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.westtiwan.tarang.eq.EqEngine
import com.westtiwan.tarang.eq.EqPreset
import java.nio.ByteBuffer

/**
 * Parametric EQ, preamp and replay gain applied inside ExoPlayer's audio sink, so it works for
 * every output (speaker, Bluetooth, USB) regardless of vendor audio effects.
 */
@UnstableApi
class EqAudioProcessor : BaseAudioProcessor() {
    @Volatile private var preset: EqPreset? = null
    @Volatile private var enabled = false
    @Volatile private var gainDb = 0.0
    @Volatile private var dirty = true
    private var engine: EqEngine? = null
    private var scratch = FloatArray(0)

    val isProcessing: Boolean get() = engine?.active == true

    fun update(preset: EqPreset?, enabled: Boolean, extraGainDb: Double) {
        this.preset = preset
        this.enabled = enabled
        this.gainDb = extraGainDb
        dirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        engine = EqEngine(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        dirty = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val eng = engine ?: return
        if (dirty) {
            eng.configure(preset, enabled, gainDb)
            dirty = false
        }
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes)
        if (!eng.active) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val samples = if (float) bytes / 4 else bytes / 2
        if (scratch.size < samples) scratch = FloatArray(samples)
        val buf = scratch
        if (float) {
            for (i in 0 until samples) buf[i] = inputBuffer.getFloat()
        } else {
            for (i in 0 until samples) buf[i] = inputBuffer.getShort() / 32768f
        }
        eng.process(buf, samples)
        if (float) {
            for (i in 0 until samples) out.putFloat(buf[i])
        } else {
            for (i in 0 until samples) {
                val v = (buf[i] * 32767f).toInt().coerceIn(-32768, 32767)
                out.putShort(v.toShort())
            }
        }
        out.flip()
    }

    override fun onReset() {
        engine = null
    }
}
