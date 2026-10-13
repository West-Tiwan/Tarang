package com.westtiwan.tarang

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.eq.OutputDevice
import com.westtiwan.tarang.playback.AudioBus
import com.westtiwan.tarang.ui.PlatformUi
import com.westtiwan.tarang.ui.screens.SettingSwitch

class AndroidPlatformUi(
    private val app: AppContainer,
    private val picker: (suspend () -> ByteArray?)? = null,
) : PlatformUi() {
    override val supportsImagePicking: Boolean get() = picker != null
    override suspend fun pickImage(): ByteArray? = picker?.invoke()

    override val audioSettings: (@Composable () -> Unit) = {
        val s by app.settings.collectAsState()
        val current by AudioBus.current.collectAsState()
        val supported by AudioBus.bitPerfectSupported.collectAsState()
        val info by AudioBus.audioInfo.collectAsState()
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingSwitch(
                "Bit-perfect USB DAC output",
                when {
                    Build.VERSION.SDK_INT < 34 -> "Needs Android 14 or later"
                    current?.kind != OutputDevice.Kind.USB -> "Connect a USB DAC to use it"
                    !supported -> "${current?.name} does not offer a bit-perfect mixer on this phone"
                    info.bitPerfect -> "Active: ${current?.name} receives untouched audio at the track's sample rate"
                    else -> "${current?.name} supports it"
                } + ". The EQ and replay gain are bypassed while active, and volume is controlled by the DAC.",
                s.bitPerfect,
            ) { v -> app.updateSettings { it.copy(bitPerfect = v) } }
            current?.let {
                Text("Current output: ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
