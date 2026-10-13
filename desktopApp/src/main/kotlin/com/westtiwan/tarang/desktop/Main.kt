package com.westtiwan.tarang.desktop

import androidx.compose.runtime.remember
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
import com.westtiwan.tarang.ui.PlatformUi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private object DesktopUi : PlatformUi() {
    override val isDesktop = true
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
            App(app, nav, DesktopUi)
        }
    }
}
