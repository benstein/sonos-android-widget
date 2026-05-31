package com.superduper.sonoswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.superduper.sonoswidget.sonos.PlaybackState
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosGateway
import com.superduper.sonoswidget.sonos.SonosPlayer
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.sonos.SonosResult
import com.superduper.sonoswidget.storage.SonosPrefs
import com.superduper.sonoswidget.widget.WidgetRenderer
import com.superduper.sonoswidget.widget.WidgetState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WidgetUpdater {
    private val executor = Executors.newSingleThreadExecutor()
    private val settlingExecutor = Executors.newSingleThreadScheduledExecutor()

    fun refreshAsync(context: Context) {
        executor.execute {
            refresh(context.applicationContext)
        }
    }

    fun handleActionAsync(context: Context, action: String?, done: () -> Unit) {
        val appContext = context.applicationContext
        val prefs = SonosPrefs(appContext)
        runCatching {
            showOptimisticPlayPause(appContext, prefs, action)
        }.onFailure { error ->
            Log.w(TAG, "Optimistic play/pause update failed", error)
        }

        executor.execute {
            try {
                val startedAt = SystemClock.elapsedRealtime()
                Log.i(TAG, "Widget action received action=$action selectedRoom=${prefs.selectedRoom}")
                runCatching {
                    performAction(appContext, prefs, action)
                }.onFailure { error ->
                    Log.w(TAG, "Widget action failed action=$action", error)
                }.onSuccess {
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    Log.i(TAG, "Widget action finished action=$action elapsedMs=$elapsedMs")
                }
                refresh(appContext, preservePendingUnknown = true)
                scheduleSettlingRefresh(appContext, action)
            } finally {
                done()
            }
        }
    }

    fun refresh(context: Context) {
        refresh(context, preservePendingUnknown = false)
    }

    private fun refresh(context: Context, preservePendingUnknown: Boolean) {
        val prefs = SonosPrefs(context)
        val selectedRoom = prefs.selectedRoom
        Log.i(TAG, "Refreshing widgets. selectedRoom=$selectedRoom")
        val gateway = SonosDiscovery(context)
        val repository = SonosRepository(gateway)
        val result = cachedPlayback(gateway, prefs, selectedRoom)
            ?: repository.currentPlayback(selectedRoom)
        val state = when (result) {
            is SonosResult.Available -> {
                Log.i(TAG, "Playback available for ${result.playback.roomName}: ${result.playback.state} ${result.playback.track.title}")
                val previousState = if (preservePendingUnknown) prefs.cachedWidgetState else null
                val playbackState = WidgetState.fromPlayback(result.playback, previousState)
                prefs.cachedCoordinator = result.coordinator
                prefs.cachedWidgetState = playbackState
                playbackState
            }
            is SonosResult.Unavailable -> if (selectedRoom.isNullOrBlank()) {
                Log.w(TAG, "No selected room saved")
                prefs.cachedWidgetState = null
                WidgetState.chooseRoom()
            } else {
                Log.w(TAG, "Playback unavailable for $selectedRoom: ${result.message}")
                prefs.cachedWidgetState = null
                WidgetState.unavailable(selectedRoom, result.message)
            }
        }

        updateWidgets(context, state)
    }

    private fun showOptimisticPlayPause(context: Context, prefs: SonosPrefs, action: String?) {
        if (action != WidgetActionReceiver.ACTION_PLAY_PAUSE) return

        val currentState = prefs.cachedWidgetState ?: return
        if (!currentState.controlsEnabled) return

        val optimisticState = currentState.optimisticPlayPause()
        prefs.cachedWidgetState = optimisticState
        Log.i(TAG, "Optimistically updating play/pause isPlaying=${optimisticState.isPlaying}")
        updatePlayPauseOnly(context, optimisticState)
    }

    private fun scheduleSettlingRefresh(context: Context, action: String?) {
        if (!isControlAction(action)) return

        settlingExecutor.schedule(
            {
                Log.i(TAG, "Running settling refresh after action=$action")
                executor.execute { refresh(context) }
            },
            SETTLING_REFRESH_DELAY_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun isControlAction(action: String?): Boolean {
        return action == WidgetActionReceiver.ACTION_PLAY_PAUSE ||
            action == WidgetActionReceiver.ACTION_NEXT ||
            action == WidgetActionReceiver.ACTION_PREVIOUS
    }

    private fun performAction(context: Context, prefs: SonosPrefs, action: String?) {
        val cachedCoordinator = prefs.cachedCoordinator
        if (cachedCoordinator != null) {
            val gateway = SonosDiscovery(context)
            val handledByCache = runCatching {
                Log.i(TAG, "Trying cached coordinator room=${cachedCoordinator.roomName} url=${cachedCoordinator.baseUrl}")
                performActionWithCoordinator(gateway, cachedCoordinator, action)
            }.onFailure { error ->
                Log.w(TAG, "Cached coordinator failed; falling back to discovery", error)
                prefs.cachedCoordinator = null
            }.isSuccess

            if (handledByCache) return
        }

        val repository = SonosRepository(SonosDiscovery(context))
        when (action) {
            WidgetActionReceiver.ACTION_PLAY_PAUSE -> repository.togglePlayPause(prefs.selectedRoom)
            WidgetActionReceiver.ACTION_NEXT -> repository.next(prefs.selectedRoom)
            WidgetActionReceiver.ACTION_PREVIOUS -> repository.previous(prefs.selectedRoom)
            else -> Log.w(TAG, "Ignoring unknown widget action=$action")
        }
    }

    private fun performActionWithCoordinator(gateway: SonosGateway, coordinator: SonosPlayer, action: String?) {
        when (action) {
            WidgetActionReceiver.ACTION_PLAY_PAUSE -> {
                val playback = gateway.playback(coordinator)
                Log.i(TAG, "Toggle cached playback room=${coordinator.roomName} state=${playback.state}")
                if (playback.state == PlaybackState.PLAYING) {
                    gateway.pause(coordinator)
                } else {
                    gateway.play(coordinator)
                }
            }
            WidgetActionReceiver.ACTION_NEXT -> {
                val playback = gateway.playback(coordinator)
                if (playback.actions.canNext) {
                    gateway.next(coordinator)
                } else {
                    Log.i(TAG, "Skipping Next; transport action unavailable")
                }
            }
            WidgetActionReceiver.ACTION_PREVIOUS -> {
                val playback = gateway.playback(coordinator)
                if (playback.actions.canPrevious) {
                    gateway.previous(coordinator)
                } else {
                    Log.i(TAG, "Skipping Previous; transport action unavailable")
                }
            }
            else -> Log.w(TAG, "Ignoring unknown widget action=$action")
        }
    }

    private fun cachedPlayback(
        gateway: SonosGateway,
        prefs: SonosPrefs,
        selectedRoom: String?
    ): SonosResult.Available? {
        val cachedCoordinator = prefs.cachedCoordinator ?: return null
        if (selectedRoom.isNullOrBlank()) return null

        return runCatching {
            Log.i(TAG, "Trying cached playback room=${cachedCoordinator.roomName} url=${cachedCoordinator.baseUrl}")
            val playback = gateway.playback(cachedCoordinator).copy(roomName = selectedRoom)
            SonosResult.Available(
                playback = playback,
                selectedPlayer = null,
                coordinator = cachedCoordinator
            )
        }.onFailure { error ->
            Log.w(TAG, "Cached playback failed; falling back to discovery", error)
            prefs.cachedCoordinator = null
        }.getOrNull()
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

    private fun updatePlayPauseOnly(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SonosWidgetProvider::class.java))
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val compact = minWidth in 1..244
            val views = WidgetRenderer.renderPlayPauseOnly(
                context = context,
                compact = compact,
                state = state
            )
            manager.partiallyUpdateAppWidget(id, views)
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
    private const val SETTLING_REFRESH_DELAY_MS = 1_500L
}
