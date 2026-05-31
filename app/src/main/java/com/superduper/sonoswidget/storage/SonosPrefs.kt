package com.superduper.sonoswidget.storage

import android.content.Context
import com.superduper.sonoswidget.sonos.SonosPlayer
import com.superduper.sonoswidget.sonos.SonosServices
import com.superduper.sonoswidget.widget.WidgetState
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

    var cachedWidgetState: WidgetState?
        get() {
            val raw = prefs.getString(KEY_CACHED_WIDGET_STATE, null) ?: return null
            return runCatching {
                val json = JSONObject(raw)
                WidgetState(
                    room = json.getString("room"),
                    title = json.getString("title"),
                    artist = json.getString("artist"),
                    artworkUrl = json.nullableString("artworkUrl"),
                    isPlaying = json.getBoolean("isPlaying"),
                    previousEnabled = json.getBoolean("previousEnabled"),
                    nextEnabled = json.getBoolean("nextEnabled"),
                    controlsEnabled = json.getBoolean("controlsEnabled"),
                    isPending = json.optBoolean("isPending", false)
                )
            }.getOrNull()
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_CACHED_WIDGET_STATE)
            } else {
                val json = JSONObject()
                    .put("room", value.room)
                    .put("title", value.title)
                    .put("artist", value.artist)
                    .put("artworkUrl", value.artworkUrl)
                    .put("isPlaying", value.isPlaying)
                    .put("previousEnabled", value.previousEnabled)
                    .put("nextEnabled", value.nextEnabled)
                    .put("controlsEnabled", value.controlsEnabled)
                    .put("isPending", value.isPending)
                editor.putString(KEY_CACHED_WIDGET_STATE, json.toString())
            }
            editor.apply()
        }

    private fun JSONObject.nullableString(name: String): String? {
        return if (isNull(name)) null else getString(name)
    }

    companion object {
        private const val KEY_SELECTED_ROOM = "selected_room"
        private const val KEY_CACHED_COORDINATOR = "cached_coordinator"
        private const val KEY_CACHED_WIDGET_STATE = "cached_widget_state"
    }
}
