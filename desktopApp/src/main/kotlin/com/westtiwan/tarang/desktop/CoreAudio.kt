package com.westtiwan.tarang.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference

/**
 * Exclusive output on macOS through Core Audio. Takes hog mode on the DAC so no other app gets mixed
 * in, and switches the DAC to the track's sample rate with a physical format deep enough for it, so
 * Core Audio has nothing to resample. With no EQ and volume at 100% the DAC then gets the file's
 * exact samples (Core Audio's float stage holds 16 and 24-bit samples exactly).
 */
object CoreAudio {
    @Suppress("FunctionName")
    private interface CA : Library {
        fun AudioObjectGetPropertyDataSize(obj: Int, addr: Addr, qualifierSize: Int, qualifier: Pointer?, size: IntByReference): Int
        fun AudioObjectGetPropertyData(obj: Int, addr: Addr, qualifierSize: Int, qualifier: Pointer?, size: IntByReference, data: Pointer): Int
        fun AudioObjectSetPropertyData(obj: Int, addr: Addr, qualifierSize: Int, qualifier: Pointer?, size: Int, data: Pointer): Int
        fun AudioObjectIsPropertySettable(obj: Int, addr: Addr, settable: ByteByReference): Int
    }

    @Suppress("FunctionName")
    private interface CF : Library {
        fun CFStringGetCString(string: Pointer, buffer: ByteArray, size: Long, encoding: Int): Byte
        fun CFRelease(ref: Pointer)
    }

    private interface LibC : Library { fun getpid(): Int }

    @Structure.FieldOrder("mSelector", "mScope", "mElement")
    class Addr() : Structure() {
        @JvmField var mSelector = 0
        @JvmField var mScope = 0
        @JvmField var mElement = 0
        constructor(selector: Int, scope: Int, element: Int = 0) : this() { mSelector = selector; mScope = scope; mElement = element }
    }

    /** What exclusive mode managed to set up on a device. */
    data class Exclusive(val device: Int, val name: String?, val hog: Boolean, val rate: Int?, val bits: Int?, val usb: Boolean)

    val supported = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    private val ca by lazy { Native.load("CoreAudio", CA::class.java) }
    private val cf by lazy { Native.load("CoreFoundation", CF::class.java) }
    private val pid by lazy { Native.load("c", LibC::class.java).getpid() }

    private fun cc(s: String) = s.fold(0) { acc, c -> (acc shl 8) or c.code }
    private const val SYSTEM = 1
    private val GLOBAL = cc("glob")
    private val OUTPUT = cc("outp")
    private val DEVICES = cc("dev#")
    private val DEFAULT_OUTPUT = cc("dOut")
    private val DEFAULT_SYSTEM_OUTPUT = cc("sOut")
    private val NAME = cc("lnam")
    private val TRANSPORT = cc("tran")
    private val STREAMS = cc("stm#")
    private val NOMINAL_RATE = cc("nsrt")
    private val HOG = cc("oink")
    private val PHYSICAL = cc("pft ")
    private val AVAILABLE_PHYSICAL = cc("pfta")
    private val LPCM = cc("lpcm")
    private val USB = cc("usb ")
    private val VOLUME = cc("volm")
    private const val FLAG_FLOAT = 1
    private const val ASBD = 40
    private const val RANGED = 56

    private fun size(obj: Int, selector: Int, scope: Int): Int {
        val r = IntByReference()
        return if (ca.AudioObjectGetPropertyDataSize(obj, Addr(selector, scope), 0, null, r) == 0) r.value else 0
    }

    private fun get(obj: Int, selector: Int, scope: Int): Memory? {
        val n = size(obj, selector, scope)
        if (n <= 0) return null
        val m = Memory(n.toLong())
        val r = IntByReference(n)
        return if (ca.AudioObjectGetPropertyData(obj, Addr(selector, scope), 0, null, r, m) == 0) m else null
    }

    private fun set(obj: Int, selector: Int, scope: Int, m: Memory): Boolean =
        ca.AudioObjectSetPropertyData(obj, Addr(selector, scope), 0, null, m.size().toInt(), m) == 0

    private fun ints(m: Memory?) = if (m == null) emptyList() else (0 until (m.size() / 4).toInt()).map { m.getInt(it * 4L) }

    private fun name(device: Int): String? {
        val ref = get(device, NAME, GLOBAL)?.getPointer(0) ?: return null
        return try {
            val buf = ByteArray(512)
            if (cf.CFStringGetCString(ref, buf, buf.size.toLong(), 0x08000100) == 0.toByte()) null
            else String(buf, 0, buf.indexOf(0).let { if (it < 0) buf.size else it }, Charsets.UTF_8)
        } finally {
            cf.CFRelease(ref)
        }
    }

    private fun outputDevices() = ints(get(SYSTEM, DEVICES, GLOBAL)).filter { size(it, STREAMS, OUTPUT) > 0 }

    /** The Core Audio device behind a Java Sound mixer name, or the system default output. */
    fun deviceFor(mixerName: String?): Int? {
        if (!supported) return null
        return runCatching {
            val wanted = mixerName?.takeUnless { it.isBlank() || it.startsWith("Default Audio Device") }
            if (wanted == null) get(SYSTEM, DEFAULT_OUTPUT, GLOBAL)?.getInt(0)
            else {
                val all = outputDevices().associateWith { name(it) }
                all.entries.firstOrNull { it.value == wanted }?.key
                    ?: all.entries.firstOrNull { (_, n) -> n != null && (wanted.contains(n) || n.contains(wanted)) }?.key
            }
        }.getOrNull()
    }

    /**
     * macOS moves the system default output off a device while another process hogs it. Remember
     * the defaults so they can be put back when Tarang lets go.
     */
    private val savedDefaults = HashMap<Int, Pair<Int?, Int?>>()

    private fun defaults(): Pair<Int?, Int?> = get(SYSTEM, DEFAULT_OUTPUT, GLOBAL)?.getInt(0) to get(SYSTEM, DEFAULT_SYSTEM_OUTPUT, GLOBAL)?.getInt(0)

    private fun restoreDefaults(device: Int) {
        val (out, sys) = savedDefaults.remove(device) ?: return
        val (curOut, curSys) = defaults()
        if (out != null && out != curOut) set(SYSTEM, DEFAULT_OUTPUT, GLOBAL, Memory(4).apply { setInt(0, out) })
        if (sys != null && sys != curSys) set(SYSTEM, DEFAULT_SYSTEM_OUTPUT, GLOBAL, Memory(4).apply { setInt(0, sys) })
    }

    /** Elements that carry the DAC's own volume control: the main one, or each channel. */
    private fun volumeElements(device: Int): List<Int> {
        fun settable(e: Int): Boolean {
            val b = ByteByReference()
            return ca.AudioObjectIsPropertySettable(device, Addr(VOLUME, OUTPUT, e), b) == 0 && b.value != 0.toByte()
        }
        if (settable(0)) return listOf(0)
        return (1..2).filter(::settable)
    }

    private fun scalar(device: Int, element: Int): Float? {
        val m = Memory(4)
        val r = IntByReference(4)
        return if (ca.AudioObjectGetPropertyData(device, Addr(VOLUME, OUTPUT, element), 0, null, r, m) == 0) m.getFloat(0) else null
    }

    /** The DAC's hardware volume (0 to 1), or null when it has none. */
    fun hardwareVolume(device: Int): Float? = runCatching {
        volumeElements(device).mapNotNull { scalar(device, it) }.maxOrNull()
    }.getOrNull()

    /** Sets the DAC's own volume. The samples stay untouched, the DAC does the attenuation. */
    fun setHardwareVolume(device: Int, volume: Float): Boolean = runCatching {
        val els = volumeElements(device)
        els.isNotEmpty() && els.all { e ->
            ca.AudioObjectSetPropertyData(device, Addr(VOLUME, OUTPUT, e), 0, null, 4, Memory(4).apply { setFloat(0, volume.coerceIn(0f, 1f)) }) == 0
        }
    }.getOrDefault(false)

    fun hogOwner(device: Int): Int? = runCatching { get(device, HOG, GLOBAL)?.getInt(0) }.getOrNull()

    @Synchronized
    fun takeHog(device: Int): Boolean = runCatching {
        if (hogOwner(device) != pid) {
            if (device !in savedDefaults) savedDefaults[device] = defaults()
            set(device, HOG, GLOBAL, Memory(4).apply { setInt(0, pid) })
        }
        hogOwner(device) == pid
    }.getOrDefault(false)

    @Synchronized
    fun releaseHog(device: Int) {
        runCatching {
            if (hogOwner(device) == pid) set(device, HOG, GLOBAL, Memory(4).apply { setInt(0, -1) })
            restoreDefaults(device)
        }
    }

    private fun rate(device: Int): Int? = get(device, NOMINAL_RATE, GLOBAL)?.getDouble(0)?.toInt()

    /**
     * Puts the output stream in an integer (or float) PCM format at [rate] with at least [bits] bits.
     * Returns the bit depth now in use.
     */
    private fun setPhysicalFormat(device: Int, rate: Int, bits: Int, channels: Int): Int? {
        val stream = ints(get(device, STREAMS, OUTPUT)).firstOrNull() ?: return null
        val all = get(stream, AVAILABLE_PHYSICAL, GLOBAL) ?: return null
        data class Option(val offset: Long, val bits: Int, val float: Boolean, val channels: Int)
        val options = (0 until (all.size() / RANGED).toInt()).map { it * RANGED.toLong() }.filter { o ->
            all.getInt(o + 8) == LPCM && all.getDouble(o + 40) <= rate && all.getDouble(o + 48) >= rate
        }.map { o -> Option(o, all.getInt(o + 32), all.getInt(o + 12) and FLAG_FLOAT != 0, all.getInt(o + 28)) }
            .filter { it.bits >= bits && (!it.float || bits <= 24) }
        val pick = options.sortedWith(compareBy({ it.float }, { it.channels != channels }, { it.bits })).firstOrNull() ?: return null
        val asbd = Memory(ASBD.toLong())
        for (i in 0 until ASBD) asbd.setByte(i.toLong(), all.getByte(pick.offset + i))
        asbd.setDouble(0, rate.toDouble())
        set(stream, PHYSICAL, GLOBAL, asbd)
        return get(stream, PHYSICAL, GLOBAL)?.getInt(32)
    }

    /**
     * Takes the device for this process and matches it to the track. Safe to call for every track:
     * it only changes what differs.
     */
    fun makeExclusive(device: Int, rate: Int, bits: Int, channels: Int): Exclusive? {
        if (!supported) return null
        return runCatching {
            val usb = get(device, TRANSPORT, GLOBAL)?.getInt(0) == USB
            // Only DACs are worth taking over; the built-in speakers stay shared.
            if (!usb) return null
            val hog = takeHog(device)
            val physicalBits = runCatching { setPhysicalFormat(device, rate, bits, channels) }.getOrNull()
            if (rate(device) != rate) set(device, NOMINAL_RATE, GLOBAL, Memory(8).apply { setDouble(0, rate.toDouble()) })
            var waited = 0
            while (rate(device) != rate && waited < 20) { Thread.sleep(25); waited++ }
            Exclusive(device, name(device), hog, rate(device), physicalBits, usb)
        }.getOrNull()
    }
}
