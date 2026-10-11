package com.westtiwan.tarang.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends cacheable server streams (those with a cache key) through the disk cache and everything
 * else (downloaded files, internet radio) straight to the upstream source.
 */
@UnstableApi
class RoutingDataSource(private val cached: DataSource, private val direct: DataSource) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme
        val useCache = dataSpec.key != null && (scheme == "http" || scheme == "https")
        val src = if (useCache) cached else direct
        active = src
        return src.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try { active?.close() } finally { active = null }
    }

    class Factory(private val cached: DataSource.Factory, private val direct: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(cached.createDataSource(), direct.createDataSource())
    }
}
