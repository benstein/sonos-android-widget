package com.superduper.sonoswidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class SonosWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        WidgetUpdater.schedulePeriodicRefresh(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Re-arm the alarm here too, so it's restored after a reboot or app update.
        WidgetUpdater.schedulePeriodicRefresh(context)
        WidgetUpdater.refreshAsync(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelPeriodicRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetUpdater.refreshAsync(context)
    }
}
