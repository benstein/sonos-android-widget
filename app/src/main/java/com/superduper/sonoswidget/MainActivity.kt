package com.superduper.sonoswidget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.storage.SonosPrefs
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: SonosPrefs
    private lateinit var status: TextView
    private lateinit var roomInput: EditText
    private lateinit var roomList: LinearLayout
    private lateinit var broadcastList: LinearLayout
    private lateinit var favoriteInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = SonosPrefs(this)
        maybeRequestLocalNetworkPermission()
        setContentView(buildContent())

        WidgetUpdater.refreshAsync(this)
        loadRooms()
    }

    private fun buildContent(): View {
        status = TextView(this).apply {
            text = selectedRoomText()
            textSize = 18f
            setTextColor(COLOR_TEXT)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }
        roomInput = EditText(this).apply {
            hint = "Dining Room"
            setSingleLine(true)
            setText(prefs.selectedRoom.orEmpty())
            textSize = 16f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedRect(COLOR_PANEL, dp(16), COLOR_BORDER, 1)
            minHeight = dp(54)
        }
        favoriteInput = EditText(this).apply {
            hint = SonosPrefs.DEFAULT_QUICK_PLAY_FAVORITE
            setSingleLine(true)
            setText(prefs.quickPlayFavorite)
            textSize = 16f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedRect(COLOR_PANEL, dp(16), COLOR_BORDER, 1)
            minHeight = dp(54)
        }
        roomList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        broadcastList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))

            addView(header(), matchWrap())

            addView(card {
                addView(label("Broadcast speakers"), matchWrap())
                addView(hint("Checked rooms play push-to-talk and the song. All checked = everywhere."),
                    topMargin(matchWrap(), 6))
                addView(broadcastList, topMargin(matchWrap(), 10))
                addView(secondaryButton("Refresh rooms") { loadRooms() }, topMargin(matchWrap(), 12))
            }, topMargin(matchWrap(), 22))

            addView(card {
                addView(label("David's Song"), matchWrap())
                addView(hint("The Sonos Favorite the one-tap button plays."), topMargin(matchWrap(), 6))
                addView(favoriteInput, topMargin(matchWrap(), 12))
                addView(primaryButton("Save song name") {
                    val name = favoriteInput.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) {
                        toast("Enter a favorite name")
                    } else {
                        prefs.quickPlayFavorite = name
                        toast("Saved “$name”")
                    }
                }, topMargin(matchWrap(), 12))
            }, topMargin(matchWrap(), 14))

            addView(card {
                addView(label("Widget room"), matchWrap())
                addView(status, topMargin(matchWrap(), 8))
                addView(roomInput, topMargin(matchWrap(), 18))
                addView(primaryButton("Save typed room") {
                    saveRoom(roomInput.text?.toString().orEmpty().trim())
                }, topMargin(matchWrap(), 12))
            }, topMargin(matchWrap(), 14))

            addView(card {
                addView(label("Setup"), matchWrap())
                addView(primaryButton("Open Sonos") {
                    if (!SonosAppLauncher.open(this@MainActivity)) {
                        toast("Sonos app not found")
                    }
                }, topMargin(matchWrap(), 14))
            }, topMargin(matchWrap(), 14))

            addView(roomList, topMargin(matchWrap(), 14))
        }

        return ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            clipToPadding = false
            addView(content, matchWrap())
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
    }

    private fun header(): View {
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                background = roundedRect(COLOR_PANEL, dp(18), COLOR_BORDER, 1)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, ViewGroup.LayoutParams(dp(62), dp(62)))

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "Settings"
                    textSize = 28f
                    setTextColor(COLOR_TEXT)
                    setTypeface(typeface, Typeface.BOLD)
                    includeFontPadding = false
                }, matchWrap())
                addView(TextView(this@MainActivity).apply {
                    text = "Broadcast, song, and widget"
                    textSize = 14f
                    setTextColor(COLOR_MUTED)
                    includeFontPadding = false
                }, topMargin(matchWrap(), 6))
            }, leftMargin(matchWrap(), 14))
        }
    }

    private fun card(content: LinearLayout.() -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedRect(COLOR_CARD, dp(22), COLOR_BORDER, 1)
            content()
        }
    }

    private fun primaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(COLOR_BUTTON_TEXT)
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(52)
            background = roundedRect(COLOR_ACCENT, dp(18), null, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(COLOR_TEXT)
            minHeight = dp(52)
            background = roundedRect(COLOR_PANEL, dp(18), COLOR_BORDER_STRONG, 1)
            setOnClickListener { onClick() }
        }
    }

    private fun roomButton(room: String): Button {
        return secondaryButton(room) { saveRoom(room) }.apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 12f
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }
    }

    private fun hint(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(COLOR_MUTED)
            includeFontPadding = false
        }
    }

    private fun maybeRequestLocalNetworkPermission() {
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 100)
        }
    }

    private fun loadRooms() {
        broadcastList.removeAllViews()
        broadcastList.addView(hint("Finding rooms…"), matchWrap())
        roomList.removeAllViews()

        thread {
            val rooms = runCatching {
                SonosRepository(SonosDiscovery(this)).rooms()
            }.getOrDefault(emptyList())

            runOnUiThread {
                broadcastList.removeAllViews()
                if (rooms.isEmpty()) {
                    broadcastList.addView(hint("No rooms found. Make sure you're on home Wi-Fi."), matchWrap())
                    return@runOnUiThread
                }
                renderBroadcastCheckboxes(rooms)

                roomList.addView(card {
                    addView(label("Set widget room"), matchWrap())
                    rooms.forEachIndexed { index, room ->
                        addView(roomButton(room), topMargin(matchWrap(), if (index == 0) 14 else 10))
                    }
                }, matchWrap())
            }
        }
    }

    private fun renderBroadcastCheckboxes(rooms: List<String>) {
        val selected = prefs.broadcastRooms
        val boxes = mutableListOf<CheckBox>()
        rooms.forEachIndexed { index, room ->
            val box = CheckBox(this).apply {
                text = room
                textSize = 16f
                isChecked = selected.isEmpty() || room in selected
                setTextColor(COLOR_TEXT)
                buttonTintList = ColorStateList.valueOf(COLOR_ACCENT)
                minHeight = dp(44)
                setOnCheckedChangeListener { _, _ ->
                    val checked = boxes.filter { it.isChecked }.map { it.text.toString() }.toSet()
                    // All (or none) checked stores empty = "all speakers".
                    prefs.broadcastRooms = if (checked.size == rooms.size) emptySet() else checked
                }
            }
            boxes += box
            broadcastList.addView(box, topMargin(matchWrap(), if (index == 0) 0 else 4))
        }
    }

    private fun saveRoom(room: String) {
        if (room.isBlank()) {
            status.text = "Enter a room name"
            return
        }

        prefs.selectedRoom = room
        prefs.cachedCoordinator = null
        prefs.cachedWidgetState = null
        roomInput.setText(room)
        status.text = selectedRoomText()
        WidgetUpdater.refreshAsync(this)
    }

    private fun selectedRoomText(): String {
        return prefs.selectedRoom ?: "No room selected"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun roundedRect(color: Int, radius: Int, strokeColor: Int?, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(dp(strokeWidth), strokeColor)
            }
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun topMargin(params: LinearLayout.LayoutParams, margin: Int): LinearLayout.LayoutParams {
        return params.apply { topMargin = dp(margin) }
    }

    private fun leftMargin(params: LinearLayout.LayoutParams, margin: Int): LinearLayout.LayoutParams {
        return params.apply { leftMargin = dp(margin) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private val COLOR_BACKGROUND = Color.rgb(8, 13, 18)
        private val COLOR_CARD = Color.rgb(17, 25, 35)
        private val COLOR_PANEL = Color.rgb(38, 51, 64)
        private val COLOR_BORDER = Color.rgb(50, 68, 84)
        private val COLOR_BORDER_STRONG = Color.rgb(76, 99, 118)
        private val COLOR_TEXT = Color.rgb(248, 250, 252)
        private val COLOR_MUTED = Color.rgb(158, 174, 188)
        private val COLOR_ACCENT = Color.rgb(90, 224, 184)
        private val COLOR_BUTTON_TEXT = Color.rgb(8, 13, 18)
    }
}
