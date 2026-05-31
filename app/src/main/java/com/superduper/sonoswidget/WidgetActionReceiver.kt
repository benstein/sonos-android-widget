package com.superduper.sonoswidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        WidgetUpdater.handleActionAsync(context, intent.action) {
            pending.finish()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.superduper.sonoswidget.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.superduper.sonoswidget.action.NEXT"
        const val ACTION_PREVIOUS = "com.superduper.sonoswidget.action.PREVIOUS"
    }
}
