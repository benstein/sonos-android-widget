package com.superduper.sonoswidget.announce

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.superduper.sonoswidget.sonos.FavoritePlayResult
import com.superduper.sonoswidget.sonos.NetworkFavoritePlayer
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosFavoritePlayer
import com.superduper.sonoswidget.storage.SonosPrefs
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Short foreground service that plays a Sonos Favorite (the configured one-tap song)
 * across the configured broadcast speakers. Foreground so it survives a backgrounded
 * app (and a future QR/deep-link trigger).
 */
class QuickPlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = SonosPrefs(applicationContext)
        val favoriteName = intent?.getStringExtra(EXTRA_FAVORITE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: prefs.quickPlayFavorite

        startForeground(NOTIFICATION_ID, buildNotification(favoriteName), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)

        if (!busy.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }

        thread(name = "quick-play-worker") {
            val rooms = prefs.broadcastRooms
            val result = runCatching {
                val transport = NetworkFavoritePlayer(SonosDiscovery(applicationContext))
                SonosFavoritePlayer(transport).play(favoriteName, rooms)
            }.getOrElse { error ->
                Log.w(TAG, "Quick play failed", error)
                null
            }
            toast(message(result, favoriteName))
            busy.set(false)
            finish(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Quick-play short-service timed out")
        finish(startId)
    }

    private fun message(result: FavoritePlayResult?, favoriteName: String): String = when (result) {
        is FavoritePlayResult.Played -> "Playing ${result.favoriteTitle} on ${result.speakers} speaker(s)"
        is FavoritePlayResult.FavoriteNotFound -> "Couldn't find favorite '${result.name}'"
        FavoritePlayResult.NoSpeakers -> "No Sonos speakers found"
        null -> "Couldn't play $favoriteName"
    }

    private fun finish(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun toast(text: String) {
        mainHandler.post { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    private fun buildNotification(favoriteName: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Quick play", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Playing on Sonos")
            .setContentText(favoriteName)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "SonosWidget"
        private const val CHANNEL_ID = "quick_play"
        private const val NOTIFICATION_ID = 43
        const val EXTRA_FAVORITE = "favorite"

        fun start(context: Context, favorite: String? = null) {
            val intent = Intent(context, QuickPlayService::class.java)
            if (favorite != null) intent.putExtra(EXTRA_FAVORITE, favorite)
            context.startForegroundService(intent)
        }
    }
}
