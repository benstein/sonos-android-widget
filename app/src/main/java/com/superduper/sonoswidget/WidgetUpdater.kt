package com.superduper.sonoswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.sonos.SonosResult
import com.superduper.sonoswidget.storage.SonosPrefs
import com.superduper.sonoswidget.widget.WidgetRenderer
import com.superduper.sonoswidget.widget.WidgetState
import java.util.concurrent.Executors

object WidgetUpdater {
    private val executor = Executors.newSingleThreadExecutor()

    fun refreshAsync(context: Context) {
        executor.execute {
            refresh(context.applicationContext)
        }
    }

    fun handleActionAsync(context: Context, action: String?, done: () -> Unit) {
        executor.execute {
            try {
                val appContext = context.applicationContext
                val prefs = SonosPrefs(appContext)
                val repository = SonosRepository(SonosDiscovery(appContext))
                runCatching {
                    when (action) {
                        WidgetActionReceiver.ACTION_PLAY_PAUSE -> repository.togglePlayPause(prefs.selectedRoom)
                        WidgetActionReceiver.ACTION_NEXT -> repository.next(prefs.selectedRoom)
                        WidgetActionReceiver.ACTION_PREVIOUS -> repository.previous(prefs.selectedRoom)
                    }
                }
                refresh(appContext)
            } finally {
                done()
            }
        }
    }

    fun refresh(context: Context) {
        val prefs = SonosPrefs(context)
        val selectedRoom = prefs.selectedRoom
        Log.i(TAG, "Refreshing widgets. selectedRoom=$selectedRoom")
        val repository = SonosRepository(SonosDiscovery(context))
        val state = when (val result = repository.currentPlayback(selectedRoom)) {
            is SonosResult.Available -> {
                Log.i(TAG, "Playback available for ${result.playback.roomName}: ${result.playback.state} ${result.playback.track.title}")
                WidgetState.fromPlayback(result.playback)
            }
            is SonosResult.Unavailable -> if (selectedRoom.isNullOrBlank()) {
                Log.w(TAG, "No selected room saved")
                WidgetState.chooseRoom()
            } else {
                Log.w(TAG, "Playback unavailable for $selectedRoom: ${result.message}")
                WidgetState.unavailable(selectedRoom, result.message)
            }
        }

        updateWidgets(context, state)
    }

    private fun updateWidgets(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SonosWidgetProvider::class.java))
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val compact = minWidth in 1..244
            Log.i(TAG, "Updating appWidgetId=$id minWidth=$minWidth compact=$compact title=${state.title}")
            val views = WidgetRenderer.render(
                context = context,
                compact = compact,
                state = state,
                previousIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, 1),
                playPauseIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, 2),
                nextIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_NEXT, 3),
                rootIntent = activityIntent(context)
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val TAG = "SonosWidget"
}
