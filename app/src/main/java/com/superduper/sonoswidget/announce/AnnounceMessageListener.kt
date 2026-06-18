package com.superduper.sonoswidget.announce

import android.os.PowerManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives announcement text sent from the watch over the Data Layer and runs the
 * announce on the phone. onMessageReceived is dispatched on a background thread, so
 * the work is done synchronously here (under a wakelock) rather than via a foreground
 * service — this path is reached while the app is in the background, where starting a
 * foreground service is restricted.
 */
class AnnounceMessageListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != ANNOUNCE_PATH) return
        val text = String(event.data, Charsets.UTF_8).trim()
        if (text.isEmpty()) return
        Log.i(TAG, "Announcement from watch: \"$text\"")

        val powerManager = getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
        wakeLock.acquire(WAKE_TIMEOUT_MS)
        try {
            val result = Announcer(applicationContext).announce(text, Announcer.DEFAULT_VOLUME)
            Log.i(TAG, "Watch announce result: ${result.message}")
        } catch (error: Exception) {
            Log.w(TAG, "Watch announce failed", error)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "SonosWidget"
        private const val ANNOUNCE_PATH = "/announce"
        private const val WAKE_TAG = "sonos:announce"
        private const val WAKE_TIMEOUT_MS = 30_000L
    }
}
