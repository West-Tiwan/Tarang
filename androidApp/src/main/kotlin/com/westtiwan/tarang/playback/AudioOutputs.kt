package com.westtiwan.tarang.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.westtiwan.tarang.eq.OutputDevice
import com.westtiwan.tarang.player.AudioInfo
import kotlinx.coroutines.flow.MutableStateFlow

/** In process bridge between the playback service and the UI side controller. */
object AudioBus {
    val outputs = MutableStateFlow<List<OutputDevice>>(emptyList())
    val current = MutableStateFlow<OutputDevice?>(null)
    val audioInfo = MutableStateFlow(AudioInfo())
    /** Whether the connected USB DAC offers a bit-perfect mixer (Android 14+). */
    val bitPerfectSupported = MutableStateFlow(false)
    @Volatile var onSelectOutput: ((String?) -> Unit)? = null
    @Volatile var onRefreshAudio: (() -> Unit)? = null
}

/**
 * Tracks audio outputs and manages the bit-perfect USB path. On Android 14+ a USB DAC that
 * supports it gets a dedicated mixer configured to the track's exact sample rate and encoding,
 * so samples pass to the DAC untouched (no resampling, no mixing, no effects).
 */
class AudioOutputs(private val context: Context, private val onChanged: () -> Unit) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var devices: List<AudioDeviceInfo> = emptyList()
    var preferred: AudioDeviceInfo? = null
        private set
    var bitPerfectActive = false
        private set
    var bitPerfectFormat: AudioFormat? = null
        private set

    private val mediaAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == preferred?.id }) preferred = null
            refresh()
        }
    }

    fun start() {
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        am.unregisterAudioDeviceCallback(callback)
        clearBitPerfect()
    }

    private fun kind(d: AudioDeviceInfo): OutputDevice.Kind = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputDevice.Kind.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputDevice.Kind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputDevice.Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputDevice.Kind.USB
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> OutputDevice.Kind.HDMI
        else -> OutputDevice.Kind.OTHER
    }

    private fun relevant(d: AudioDeviceInfo) = d.isSink && kind(d) != OutputDevice.Kind.OTHER && d.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE

    private fun name(d: AudioDeviceInfo): String {
        val product = d.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
        return when (kind(d)) {
            OutputDevice.Kind.SPEAKER -> "Phone speaker"
            OutputDevice.Kind.WIRED -> product ?: "Wired headphones"
            OutputDevice.Kind.BLUETOOTH -> product ?: "Bluetooth"
            OutputDevice.Kind.USB -> product ?: "USB DAC"
            OutputDevice.Kind.HDMI -> product ?: "HDMI"
            OutputDevice.Kind.OTHER -> product ?: "Output"
        }
    }

    fun toOutput(d: AudioDeviceInfo) = OutputDevice("${kind(d).name}:${name(d)}", name(d), kind(d))

    fun refresh() {
        devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter(::relevant).distinctBy { toOutput(it).key }
        AudioBus.outputs.value = devices.map(::toOutput)
        val cur = currentDevice()
        AudioBus.current.value = cur?.let(::toOutput)
        AudioBus.bitPerfectSupported.value = cur != null && kind(cur) == OutputDevice.Kind.USB && Build.VERSION.SDK_INT >= 34 && supportsBitPerfect(cur)
        onChanged()
    }

    /** The device media is routed to right now. */
    fun currentDevice(): AudioDeviceInfo? {
        preferred?.let { p -> if (devices.any { it.id == p.id }) return p }
        if (Build.VERSION.SDK_INT >= 33) {
            val routed = am.getAudioDevicesForAttributes(mediaAttrs).firstOrNull()
            if (routed != null) devices.firstOrNull { it.type == routed.type && it.address == routed.address }?.let { return it }
        }
        val order = listOf(OutputDevice.Kind.USB, OutputDevice.Kind.WIRED, OutputDevice.Kind.BLUETOOTH, OutputDevice.Kind.HDMI, OutputDevice.Kind.SPEAKER)
        return order.firstNotNullOfOrNull { k -> devices.firstOrNull { kind(it) == k } }
    }

    fun select(key: String?): AudioDeviceInfo? {
        preferred = devices.firstOrNull { toOutput(it).key == key }
        refresh()
        return preferred
    }

    @RequiresApi(34)
    private fun supportsBitPerfect(d: AudioDeviceInfo): Boolean = runCatching {
        am.getSupportedMixerAttributes(d).any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
    }.getOrDefault(false)

    /**
     * Configures the bit-perfect mixer for the current USB DAC to match the track. Returns true when
     * the output is now bit-perfect.
     */
    fun applyBitPerfect(enabled: Boolean, sampleRate: Int?, highRes: Boolean): Boolean {
        val dev = currentDevice()
        if (!enabled || dev == null || kind(dev) != OutputDevice.Kind.USB || Build.VERSION.SDK_INT < 34) {
            clearBitPerfect()
            return false
        }
        return setBitPerfect34(dev, sampleRate ?: 44100, highRes)
    }

    @RequiresApi(34)
    private fun setBitPerfect34(dev: AudioDeviceInfo, rate: Int, highRes: Boolean): Boolean {
        val options = runCatching { am.getSupportedMixerAttributes(dev) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT && it.format.sampleRate == rate }
        val wanted = if (highRes) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
        val pick = options.firstOrNull { it.format.encoding == wanted }
            ?: options.firstOrNull { highRes && (it.format.encoding == AudioFormat.ENCODING_PCM_32BIT || it.format.encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) }
            ?: run { clearBitPerfect(); return false }
        val current = am.getPreferredMixerAttributes(mediaAttrs, dev)
        if (current == pick) {
            bitPerfectActive = pick.format.encoding == wanted
            return bitPerfectActive
        }
        val ok = am.setPreferredMixerAttributes(mediaAttrs, dev, pick)
        bitPerfectActive = ok && pick.format.encoding == wanted
        bitPerfectFormat = if (ok) pick.format else null
        return bitPerfectActive
    }

    fun clearBitPerfect() {
        if (Build.VERSION.SDK_INT >= 34) {
            devices.filter { kind(it) == OutputDevice.Kind.USB }.forEach { d ->
                runCatching { if (am.getPreferredMixerAttributes(mediaAttrs, d) != null) am.clearPreferredMixerAttributes(mediaAttrs, d) }
            }
        }
        bitPerfectActive = false
        bitPerfectFormat = null
    }
}
