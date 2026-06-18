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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Short-lived foreground service that performs one announcement. Foreground so the
 * work survives Doze and a backgrounded app (e.g. when triggered from the watch).
 */
class AnnounceService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)?.trim()
        val volume = intent?.getIntExtra(EXTRA_VOLUME, Announcer.DEFAULT_VOLUME) ?: Announcer.DEFAULT_VOLUME

        startForeground(NOTIFICATION_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)

        if (text.isNullOrBlank()) {
            finish(startId)
            return START_NOT_STICKY
        }
        if (!busy.compareAndSet(false, true)) {
            toast("Already announcing…")
            // Don't stop the in-flight announcement; just drop this duplicate request.
            return START_NOT_STICKY
        }

        thread(name = "announce-worker") {
            val result = runCatching { Announcer(applicationContext).announce(text, volume) }
                .getOrElse { Announcer.Result(0, "Announce failed") }
            Log.i(TAG, "Announce result: ${result.message}")
            toast(result.message)
            busy.set(false)
            finish(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Foreground short-service timed out")
        finish(startId)
    }

    private fun finish(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun toast(message: String) {
        mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun buildNotification(text: String?): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Announcements", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Announcing to Sonos")
            .setContentText(text?.takeIf { it.isNotBlank() } ?: "Sending…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "SonosWidget"
        private const val CHANNEL_ID = "announcements"
        private const val NOTIFICATION_ID = 42
        const val EXTRA_TEXT = "text"
        const val EXTRA_VOLUME = "volume"

        fun start(context: Context, text: String, volume: Int = Announcer.DEFAULT_VOLUME) {
            val intent = Intent(context, AnnounceService::class.java)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_VOLUME, volume)
            context.startForegroundService(intent)
        }
    }
}
