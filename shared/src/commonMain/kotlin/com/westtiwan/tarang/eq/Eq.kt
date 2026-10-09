package com.westtiwan.tarang.eq

import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class FilterType(val label: String) { PEAK("Peak"), LOW_SHELF("Low shelf"), HIGH_SHELF("High shelf"), LOW_PASS("Low pass"), HIGH_PASS("High pass") }

@Serializable
data class EqBand(
    val type: FilterType = FilterType.PEAK,
    val freq: Double,
    val gainDb: Double = 0.0,
    val q: Double = 1.41,
    val enabled: Boolean = true,
)

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val preampDb: Double = 0.0,
    val bands: List<EqBand>,
    val source: String = "user",
)

@Serializable
data class EqState(
    val enabled: Boolean = false,
    val current: EqPreset = EqPresets.flat,
    val presets: List<EqPreset> = emptyList(),
    /** Output device key to preset id, so each headphone or DAC gets its own curve. */
    val deviceProfiles: Map<String, String> = emptyMap(),
    val autoSwitch: Boolean = true,
    val autoEqLookup: Boolean = true,
    val updatedAt: Long = 0,
)

object EqPresets {
    val graphicFreqs = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    fun graphic(id: String, name: String, gains: List<Double>, preamp: Double = 0.0) = EqPreset(
        id, name, preamp, graphicFreqs.mapIndexed { i, f -> EqBand(FilterType.PEAK, f, gains.getOrElse(i) { 0.0 }, 1.41) }, "builtin",
    )

    val flat = graphic("flat", "Flat", List(10) { 0.0 })

    val builtins = listOf(
        flat,
        graphic("bass", "Bass boost", listOf(6.0, 5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), -6.0),
        graphic("subbass", "Sub bass", listOf(8.0, 6.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), -8.0),
        graphic("treble", "Treble boost", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 4.0, 5.0, 6.0), -6.0),
        graphic("vocal", "Vocal", listOf(-2.0, -2.0, -1.0, 1.0, 3.0, 4.0, 3.0, 1.0, 0.0, -1.0), -4.0),
        graphic("loudness", "Loudness", listOf(5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 0.0, 1.0, 3.0, 4.0), -5.0),
        graphic("rock", "Rock", listOf(4.0, 3.0, 2.0, 0.0, -1.0, -1.0, 1.0, 2.0, 3.0, 4.0), -4.0),
        graphic("pop", "Pop", listOf(-1.0, 1.0, 3.0, 4.0, 3.0, 0.0, -1.0, -1.0, 0.0, 1.0), -4.0),
        graphic("jazz", "Jazz", listOf(3.0, 2.0, 1.0, 2.0, -1.0, -1.0, 0.0, 1.0, 2.0, 3.0), -3.0),
        graphic("classical", "Classical", listOf(3.0, 2.0, 1.0, 0.0, 0.0, 0.0, -1.0, -1.0, 1.0, 2.0), -3.0),
        graphic("electronic", "Electronic", listOf(5.0, 4.0, 1.0, 0.0, -2.0, 1.0, 0.0, 1.0, 4.0, 5.0), -5.0),
        graphic("hiphop", "Hip hop", listOf(5.0, 4.0, 1.0, 2.0, -1.0, -1.0, 1.0, -1.0, 2.0, 3.0), -5.0),
        graphic("bollywood", "Bollywood", listOf(4.0, 3.0, 1.0, 0.0, 1.0, 3.0, 3.0, 2.0, 2.0, 3.0), -4.0),
        graphic("acoustic", "Acoustic", listOf(3.0, 2.0, 1.0, 1.0, 2.0, 2.0, 3.0, 3.0, 2.0, 1.0), -3.0),
        graphic("night", "Late night", listOf(-3.0, -2.0, 0.0, 1.0, 2.0, 2.0, 1.0, 0.0, -2.0, -3.0), -2.0),
    )

    fun isGraphic(p: EqPreset) = p.bands.size == 10 && p.bands.zip(graphicFreqs).all { (b, f) -> b.type == FilterType.PEAK && b.freq == f }
}

/** RBJ audio EQ cookbook biquad, transposed direct form II. */
class Biquad(band: EqBand, sampleRate: Double, private val channels: Int) {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    init {
        val f = band.freq.coerceIn(10.0, sampleRate * 0.49)
        val w0 = 2 * PI * f / sampleRate
        val cw = cos(w0)
        val sw = sin(w0)
        val q = band.q.coerceAtLeast(0.05)
        val alpha = sw / (2 * q)
        val a = 10.0.pow(band.gainDb / 40)
        var nb0: Double; var nb1: Double; var nb2: Double; var na0: Double; var na1: Double; var na2: Double
        when (band.type) {
            FilterType.PEAK -> {
                nb0 = 1 + alpha * a; nb1 = -2 * cw; nb2 = 1 - alpha * a
                na0 = 1 + alpha / a; na1 = -2 * cw; na2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                nb0 = a * ((a + 1) - (a - 1) * cw + s); nb1 = 2 * a * ((a - 1) - (a + 1) * cw); nb2 = a * ((a + 1) - (a - 1) * cw - s)
                na0 = (a + 1) + (a - 1) * cw + s; na1 = -2 * ((a - 1) + (a + 1) * cw); na2 = (a + 1) + (a - 1) * cw - s
            }
            FilterType.HIGH_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                nb0 = a * ((a + 1) + (a - 1) * cw + s); nb1 = -2 * a * ((a - 1) + (a + 1) * cw); nb2 = a * ((a + 1) + (a - 1) * cw - s)
                na0 = (a + 1) - (a - 1) * cw + s; na1 = 2 * ((a - 1) - (a + 1) * cw); na2 = (a + 1) - (a - 1) * cw - s
            }
            FilterType.LOW_PASS -> {
                nb0 = (1 - cw) / 2; nb1 = 1 - cw; nb2 = (1 - cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            FilterType.HIGH_PASS -> {
                nb0 = (1 + cw) / 2; nb1 = -(1 + cw); nb2 = (1 + cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
        }
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    /** Magnitude response in dB at [freq], used to draw the curve. */
    fun responseDb(freq: Double, sampleRate: Double): Double {
        val w = 2 * PI * freq / sampleRate
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2; val ni = -(b1 * sw + b2 * s2)
        val dr = 1 + a1 * cw + a2 * c2; val di = -(a1 * sw + a2 * s2)
        val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        return 20 * kotlin.math.log10(mag.coerceAtLeast(1e-9))
    }
}

/**
 * Processes interleaved float samples in place. Shared by the Android audio processor and anything
 * else that has PCM access. Includes preamp, replay gain and a soft limiter to avoid clipping.
 */
class EqEngine(private val sampleRate: Int, private val channels: Int) {
    @Volatile private var filters: List<Biquad> = emptyList()
    @Volatile private var gain: Double = 1.0
    @Volatile var active: Boolean = false
        private set

    fun configure(preset: EqPreset?, enabled: Boolean, extraGainDb: Double) {
        val bands = if (enabled && preset != null) preset.bands.filter { it.enabled && (it.gainDb != 0.0 || it.type == FilterType.LOW_PASS || it.type == FilterType.HIGH_PASS) } else emptyList()
        filters = bands.map { Biquad(it, sampleRate.toDouble(), channels) }
        val totalDb = (if (enabled && preset != null) preset.preampDb else 0.0) + extraGainDb
        gain = 10.0.pow(totalDb / 20)
        active = filters.isNotEmpty() || totalDb != 0.0
    }

    fun process(samples: FloatArray, count: Int) {
        val fs = filters
        val g = gain
        var i = 0
        while (i < count) {
            val ch = i % channels
            var x = samples[i].toDouble() * g
            for (f in fs) x = f.process(x, ch)
            // Soft clip above -0.5 dBFS.
            if (x > 0.944) x = 0.944 + (1 - 0.944) * kotlin.math.tanh((x - 0.944) / (1 - 0.944))
            else if (x < -0.944) x = -0.944 - (1 - 0.944) * kotlin.math.tanh((-x - 0.944) / (1 - 0.944))
            samples[i] = x.toFloat()
            i++
        }
    }

    companion object {
        fun responseCurve(preset: EqPreset, points: Int = 120, sampleRate: Double = 48000.0): List<Pair<Double, Double>> {
            val filters = preset.bands.filter { it.enabled }.map { it to Biquad(it, sampleRate, 1) }
            return (0 until points).map { i ->
                val f = 20.0 * 1000.0.pow(i / (points - 1.0))
                f to (preset.preampDb + filters.sumOf { it.second.responseDb(f, sampleRate) })
            }
        }
    }
}

object AutoEq {
    private val preampRe = Regex("""Preamp:\s*([+-]?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRe = Regex(
        """Filter\s*\d*:\s*(ON|OFF)\s+(PK|PEQ|LSC|LS|HSC|HS|LP|LPQ|HP|HPQ)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+([+-]?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )

    /** Parses the ParametricEQ.txt format used by AutoEq, Equalizer APO and Wavelet. */
    fun parse(id: String, name: String, text: String): EqPreset? {
        val bands = filterRe.findAll(text).map { m ->
            val type = when (m.groupValues[2].uppercase()) {
                "LSC", "LS" -> FilterType.LOW_SHELF
                "HSC", "HS" -> FilterType.HIGH_SHELF
                "LP", "LPQ" -> FilterType.LOW_PASS
                "HP", "HPQ" -> FilterType.HIGH_PASS
                else -> FilterType.PEAK
            }
            EqBand(type, m.groupValues[3].toDouble(), m.groupValues[4].toDoubleOrNull() ?: 0.0, m.groupValues[5].toDoubleOrNull() ?: 0.707, m.groupValues[1].uppercase() == "ON")
        }.toList()
        if (bands.isEmpty()) return null
        val pre = preampRe.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return EqPreset(id, name, pre, bands, "autoeq")
    }

    fun export(preset: EqPreset): String = buildString {
        appendLine("Preamp: ${preset.preampDb} dB")
        preset.bands.forEachIndexed { i, b ->
            val t = when (b.type) {
                FilterType.PEAK -> "PK"; FilterType.LOW_SHELF -> "LSC"; FilterType.HIGH_SHELF -> "HSC"
                FilterType.LOW_PASS -> "LPQ"; FilterType.HIGH_PASS -> "HPQ"
            }
            appendLine("Filter ${i + 1}: ${if (b.enabled) "ON" else "OFF"} $t Fc ${b.freq} Hz Gain ${b.gainDb} dB Q ${b.q}")
        }
    }
}
