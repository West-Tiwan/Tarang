package com.westtiwan.tarang.desktop

import com.westtiwan.tarang.update.UpdateInstaller
import java.io.File
import kotlin.system.exitProcess

/**
 * Updates the macOS app in place: once Tarang quits, a small script mounts the DMG, swaps the
 * .app bundle, clears the quarantine flag and opens the new version.
 */
class DmgInstaller : UpdateInstaller {
    override val assetSuffix = ".dmg"
    override val restartsApp = true

    override fun install(path: String) {
        val launcher = System.getProperty("jpackage.app-path") ?: error("Not running from an app bundle")
        val bundle = launcher.substringBefore(".app/") + ".app"
        if (!bundle.endsWith(".app") || !File(bundle).exists()) {
            java.awt.Desktop.getDesktop().open(File(path))
            return
        }
        val pid = ProcessHandle.current().pid()
        val script = File.createTempFile("tarang-update", ".sh")
        script.writeText(
            """
            #!/bin/bash
            while kill -0 $pid 2>/dev/null; do sleep 0.5; done
            MNT=${'$'}(hdiutil attach -nobrowse -noautoopen "$path" | tail -1 | awk -F'\t' '{print ${'$'}NF}')
            APP=${'$'}(ls -d "${'$'}MNT"/*.app | head -1)
            if [ -d "${'$'}APP" ]; then
              rm -rf "$bundle.old"
              mv "$bundle" "$bundle.old" && cp -R "${'$'}APP" "$bundle" && rm -rf "$bundle.old"
              xattr -cr "$bundle"
            fi
            hdiutil detach "${'$'}MNT" -quiet
            rm -f "$path"
            open "$bundle"
            rm -f "${'$'}0"
            """.trimIndent(),
        )
        script.setExecutable(true)
        ProcessBuilder("/bin/bash", script.absolutePath).inheritIO().start()
        exitProcess(0)
    }
}
