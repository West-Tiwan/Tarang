package com.westtiwan.tarang.playback

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.max

/**
 * Artwork loader for the media session. Every caller gets its own bitmap, already no larger than
 * the system's media metadata limit. Android 15 frees the artwork bitmap after scaling it for the
 * lock screen, so handing out a shared cached bitmap made the next metadata update crash.
 */
@UnstableApi
class SafeBitmapLoader(context: Context) : BitmapLoader {
    private companion object { const val MAX_PX = 320 }

    private val delegate = CacheBitmapLoader(DataSourceBitmapLoader(context))

    /**
     * Some ROMs report a larger limit than the session really uses, so stay under 320px, which
     * is the smallest limit seen in the wild and still sharp enough for notifications and Auto.
     */
    private val maxPx: Int = run {
        val res = Resources.getSystem()
        val id = res.getIdentifier("config_mediaMetadataBitmapMaxSize", "dimen", "android")
        val px = if (id != 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(MAX_PX) else MAX_PX
        px.coerceIn(128, MAX_PX)
    }

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        Futures.transform(delegate.decodeBitmap(data), ::freshCopy, MoreExecutors.directExecutor())

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.transform(delegate.loadBitmap(uri), ::freshCopy, MoreExecutors.directExecutor())

    private fun freshCopy(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest > maxPx) {
            val scale = maxPx.toFloat() / longest
            return Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        }
        return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
    }
}
