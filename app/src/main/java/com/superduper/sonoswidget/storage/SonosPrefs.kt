package com.superduper.sonoswidget.storage

import android.content.Context

class SonosPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sonos-widget", Context.MODE_PRIVATE)

    var selectedRoom: String?
        get() = prefs.getString(KEY_SELECTED_ROOM, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED_ROOM, value).apply()
        }

    companion object {
        private const val KEY_SELECTED_ROOM = "selected_room"
    }
}
