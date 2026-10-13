package com.westtiwan.tarang

import android.app.Application
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.playback.AndroidPlayer
import com.westtiwan.tarang.translate.MlKitTranslator
import com.westtiwan.tarang.update.ApkInstaller

class TarangApp : Application() {
    lateinit var app: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Platform.init(this)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "dev"
        app = AppContainer(platformTranslator = MlKitTranslator(), appVersion = version, updateInstaller = ApkInstaller(this))
        app.attachPlayer(AndroidPlayer(this, app))
        app.start()
    }
}
