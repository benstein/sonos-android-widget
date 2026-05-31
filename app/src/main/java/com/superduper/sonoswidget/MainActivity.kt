package com.superduper.sonoswidget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.storage.SonosPrefs
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: SonosPrefs
    private lateinit var status: TextView
    private lateinit var roomInput: EditText
    private lateinit var roomList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = SonosPrefs(this)
        maybeRequestLocalNetworkPermission()

        status = TextView(this).apply {
            textSize = 18f
            text = selectedRoomText()
        }
        roomInput = EditText(this).apply {
            hint = "Dining Room"
            setSingleLine(true)
            setText(prefs.selectedRoom.orEmpty())
        }
        roomList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val saveTypedRoom = Button(this).apply {
            text = "Save typed room"
            setOnClickListener {
                saveRoom(roomInput.text?.toString().orEmpty().trim())
            }
        }
        val findRooms = Button(this).apply {
            text = "Find Sonos rooms"
            setOnClickListener { loadRooms() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(roomInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(saveTypedRoom, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(findRooms, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(roomList, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        WidgetUpdater.refreshAsync(this)
    }

    private fun maybeRequestLocalNetworkPermission() {
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 100)
        }
    }

    private fun loadRooms() {
        status.text = "Finding Sonos rooms..."
        roomList.removeAllViews()

        thread {
            val rooms = runCatching {
                SonosRepository(SonosDiscovery(this)).rooms()
            }.getOrDefault(emptyList())

            runOnUiThread {
                if (rooms.isEmpty()) {
                    status.text = "No rooms found. Type the room name above."
                    return@runOnUiThread
                }

                status.text = selectedRoomText()
                rooms.forEach { room ->
                    roomList.addView(Button(this).apply {
                        text = room
                        setOnClickListener { saveRoom(room) }
                    })
                }
            }
        }
    }

    private fun saveRoom(room: String) {
        if (room.isBlank()) {
            status.text = "Enter a room name"
            return
        }

        prefs.selectedRoom = room
        roomInput.setText(room)
        status.text = selectedRoomText()
        WidgetUpdater.refreshAsync(this)
    }

    private fun selectedRoomText(): String {
        return "Selected room: ${prefs.selectedRoom ?: "none"}"
    }
}
