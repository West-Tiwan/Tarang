package com.westtiwan.tarang.platform

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

@SuppressLint("StaticFieldLeak")
actual object Platform {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    actual val name: String = "Android"
    actual fun dataDir(): String = context.filesDir.absolutePath
    actual fun cacheDir(): String = context.cacheDir.absolutePath

    private val cm get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    actual fun isUnmeteredNetwork(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    actual fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

actual fun anyToLatin(text: String): String =
    if (Build.VERSION.SDK_INT >= 29) {
        runCatching { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(text) }.getOrDefault(text)
    } else text
