package com.westtiwan.tarang.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.MediaButtonReceiver
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.westtiwan.tarang.AppContainer
import com.westtiwan.tarang.MainActivity
import com.westtiwan.tarang.R
import com.westtiwan.tarang.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, lastSong, lastPlaying, lastArt)
    }

    companion object {
        private var lastSong: Song? = null
        private var lastPlaying = false
        private var lastArt: Bitmap? = null
        private var lastCover: String? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun update(context: Context, song: Song?, playing: Boolean, app: AppContainer) {
            lastSong = song
            lastPlaying = playing
            render(context, song, playing, lastArt.takeIf { song?.coverArt == lastCover })
            if (song?.coverArt != lastCover) {
                lastCover = song?.coverArt
                val url = app.coverUrl(song?.coverArt, 300) ?: run { lastArt = null; return }
                scope.launch {
                    val req = ImageRequest.Builder(context).data(url).size(300).allowHardware(false).build()
                    val bmp = runCatching { SingletonImageLoader.get(context).execute(req).image?.toBitmap() }.getOrNull()
                    lastArt = bmp
                    render(context, lastSong, lastPlaying, bmp)
                }
            }
        }

        private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(context, MediaButtonReceiver::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun render(context: Context, song: Song?, playing: Boolean, art: Bitmap?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(R.id.widget_title, song?.title ?: "Tarang")
            views.setTextViewText(R.id.widget_artist, song?.artist ?: "Tap to open")
            if (art != null) views.setImageViewBitmap(R.id.widget_art, art) else views.setImageViewResource(R.id.widget_art, R.drawable.ic_music_note)
            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            views.setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("open_player", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
