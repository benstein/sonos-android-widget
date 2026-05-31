package com.superduper.sonoswidget.widget

import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.superduper.sonoswidget.R
import com.superduper.sonoswidget.sonos.JavaNetSonosHttpClient

object WidgetRenderer {
    fun render(
        context: Context,
        compact: Boolean,
        state: WidgetState,
        previousIntent: PendingIntent,
        playPauseIntent: PendingIntent,
        nextIntent: PendingIntent,
        rootIntent: PendingIntent
    ): RemoteViews {
        val layout = layoutFor(compact)
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.room, state.displayRoom)
            setTextViewText(R.id.title, state.title)
            setTextViewText(R.id.artist, state.artist)
            setImageViewResource(R.id.play_pause, if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            setInt(R.id.previous, "setAlpha", if (state.previousEnabled) 255 else 90)
            setInt(R.id.next, "setAlpha", if (state.nextEnabled) 255 else 90)
            setInt(R.id.play_pause, "setAlpha", playPauseAlpha(state))
            setOnClickPendingIntent(R.id.widget_root, rootIntent)
            setOnClickPendingIntent(R.id.previous, previousIntent)
            setOnClickPendingIntent(R.id.play_pause, playPauseIntent)
            setOnClickPendingIntent(R.id.next, nextIntent)

            if (!compact && state.artworkUrl != null) {
                runCatching {
                    val bytes = JavaNetSonosHttpClient(
                        connectTimeoutMs = 1_500,
                        readTimeoutMs = 2_500
                    ).getBytes(state.artworkUrl)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()?.let { bitmap ->
                    setImageViewBitmap(R.id.artwork, bitmap)
                    setViewVisibility(R.id.artwork_fallback, View.GONE)
                }
            }
        }
    }

    fun renderPlayPauseOnly(context: Context, compact: Boolean, state: WidgetState): RemoteViews {
        return RemoteViews(context.packageName, layoutFor(compact)).apply {
            setImageViewResource(R.id.play_pause, if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            setInt(R.id.play_pause, "setAlpha", playPauseAlpha(state))
        }
    }

    private fun layoutFor(compact: Boolean): Int {
        return if (compact) R.layout.widget_sonos_compact else R.layout.widget_sonos_wide
    }

    private fun playPauseAlpha(state: WidgetState): Int {
        return when {
            state.isPending -> 170
            state.controlsEnabled -> 255
            else -> 90
        }
    }
}
