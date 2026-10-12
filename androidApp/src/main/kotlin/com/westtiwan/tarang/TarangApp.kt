package com.westtiwan.tarang

import android.app.Application
import com.westtiwan.tarang.platform.Platform
import com.westtiwan.tarang.playback.AndroidPlayer
import com.westtiwan.tarang.translate.MlKitTranslator

class TarangApp : Application() {
    lateinit var app: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Platform.init(this)
        app = AppContainer(platformTranslator = MlKitTranslator())
        app.attachPlayer(AndroidPlayer(this, app))
        app.start()
    }
}
