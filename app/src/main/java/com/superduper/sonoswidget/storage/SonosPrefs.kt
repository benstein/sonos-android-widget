package com.superduper.sonoswidget.storage

import android.content.Context
import com.superduper.sonoswidget.sonos.SonosPlayer
import com.superduper.sonoswidget.sonos.SonosServices
import org.json.JSONObject

class SonosPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sonos-widget", Context.MODE_PRIVATE)

    var selectedRoom: String?
        get() = prefs.getString(KEY_SELECTED_ROOM, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED_ROOM, value).apply()
        }

    var cachedCoordinator: SonosPlayer?
        get() {
            val raw = prefs.getString(KEY_CACHED_COORDINATOR, null) ?: return null
            return runCatching {
                val json = JSONObject(raw)
                SonosPlayer(
                    roomName = json.getString("roomName"),
                    uuid = json.getString("uuid"),
                    baseUrl = json.getString("baseUrl"),
                    services = SonosServices(
                        avTransportControlUrl = json.getString("avTransportControlUrl"),
                        zoneGroupTopologyControlUrl = if (json.isNull("zoneGroupTopologyControlUrl")) {
                            null
                        } else {
                            json.getString("zoneGroupTopologyControlUrl")
                        }
                    )
                )
            }.getOrNull()
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_CACHED_COORDINATOR)
            } else {
                val json = JSONObject()
                    .put("roomName", value.roomName)
                    .put("uuid", value.uuid)
                    .put("baseUrl", value.baseUrl)
                    .put("avTransportControlUrl", value.services.avTransportControlUrl)
                    .put("zoneGroupTopologyControlUrl", value.services.zoneGroupTopologyControlUrl)
                editor.putString(KEY_CACHED_COORDINATOR, json.toString())
            }
            editor.apply()
        }

    companion object {
        private const val KEY_SELECTED_ROOM = "selected_room"
        private const val KEY_CACHED_COORDINATOR = "cached_coordinator"
    }
}
