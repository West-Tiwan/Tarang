package com.westtiwan.tarang.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.westtiwan.tarang.App
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.ui.Navigator
import com.westtiwan.tarang.eq.OutputDevice
import com.westtiwan.tarang.ui.PlatformUi
import com.westtiwan.tarang.ui.screens.SettingSwitch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class DesktopUi(private val app: AppContainer, private val player: DesktopPlayer) : PlatformUi() {
    override val isDesktop = true

    override val audioSettings: (@Composable () -> Unit) = {
        val s by app.settings.collectAsState()
        val st by player.state.collectAsState()
        val current by player.currentOutput.collectAsState()
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingSwitch(
                "Bit-perfect USB DAC output",
                when {
                    !s.bitPerfect -> "Off: macOS mixes Tarang with other apps and may resample it"
                    st.audio.bitPerfect -> "Active: ${st.audio.output} receives untouched audio at ${st.audio.outputRate?.let { "${it / 1000.0} kHz" } ?: "the track's rate"}"
                    current?.kind == OutputDevice.Kind.USB -> "On: plays exclusively to ${current?.name} at the track's sample rate once something is playing, with Tarang volume at 100%"
                    else -> "On: takes effect when a USB DAC is the output"
                } + ". While playing, other apps can't use the DAC and the Mac volume keys can't change it, so use Tarang's volume, which sets the DAC's own volume. The EQ and replay gain are bypassed.",
                s.bitPerfect,
            ) { v -> app.updateSettings { it.copy(bitPerfect = v) } }
        }
    }
    override val supportsImagePicking = true

    override suspend fun pickImage(): ByteArray? = suspendCancellableCoroutine { cont ->
        javax.swing.SwingUtilities.invokeLater {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose an image", java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
            dialog.isVisible = true
            val file = dialog.files.firstOrNull()
            cont.resume(file?.readBytes())
        }
    }
}

fun main() {
    System.setProperty("apple.awt.application.name", "Tarang")
    val version = System.getProperty("tarang.version") ?: "dev"
    // Updates only make sense for the packaged app, not for `gradle run`.
    val packaged = System.getProperty("jpackage.app-path") != null
    val app = AppContainer(appVersion = if (packaged) version else "dev", updateInstaller = if (packaged) DmgInstaller() else null)
    val player = DesktopPlayer(app)
    app.attachPlayer(player)
    app.start()
    val ui = DesktopUi(app, player)
    application {
        val nav = remember { Navigator() }
        val state = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        Window(
            onCloseRequest = {
                app.settingsStore.flush()
                app.eqStore.flush()
                exitApplication()
            },
            title = "Tarang",
            state = state,
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    e.key == Key.Spacebar && e.isMetaPressed -> { player.toggle(); true }
                    e.key == Key.DirectionRight && e.isMetaPressed -> { player.next(); true }
                    e.key == Key.DirectionLeft && e.isMetaPressed -> { player.previous(); true }
                    e.key == Key.Escape -> nav.back()
                    else -> false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(420, 640)
            App(app, nav, ui)
        }
    }
}
