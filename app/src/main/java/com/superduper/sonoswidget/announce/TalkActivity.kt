package com.superduper.sonoswidget.announce

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.superduper.sonoswidget.MainActivity
import com.superduper.sonoswidget.R
import com.superduper.sonoswidget.storage.SonosPrefs

/**
 * Push-to-talk screen: speak an announcement, review the transcription during a short
 * cancel window, then broadcast it to every Sonos speaker.
 *
 * Visual direction: an "ambient audio console" — a deep teal-to-charcoal wash, a big
 * glowing mic button ringed by sound waves, and characterful type.
 */
class TalkActivity : Activity() {

    private lateinit var prefs: SonosPrefs
    private lateinit var status: TextView
    private lateinit var micButton: FrameLayout
    private lateinit var micStack: FrameLayout
    private lateinit var cancelButton: Button
    private lateinit var volumeLabel: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var entranceViews: List<View>
    private var countdown: CountDownTimer? = null

    private val fontDisplay by lazy { resources.getFont(R.font.bricolage_grotesque) }
    private val fontBody by lazy { resources.getFont(R.font.hanken_grotesk) }
    private val fontSemibold by lazy { resources.getFont(R.font.hanken_grotesk_semibold) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SonosPrefs(this)
        requestPermissionsIfNeeded()
        setContentView(buildContent())
        showIdle()
        playEntrance()

        // Deep-link / automation entry point: announce a fixed phrase without the mic.
        intent?.getStringExtra(EXTRA_ANNOUNCE_NOW)?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            AnnounceService.start(this, text, prefs.announceVolume)
            status.text = "Announcing “$text”…"
        }
    }

    private fun buildContent(): View {
        val title = TextView(this).apply {
            text = "Talk to Sonos"
            textSize = 34f
            setTextColor(COLOR_TEXT)
            typeface = fontDisplay
            letterSpacing = -0.01f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val subtitle = TextView(this).apply {
            text = "Speak, and it plays everywhere"
            textSize = 14.5f
            setTextColor(COLOR_MUTED)
            typeface = fontBody
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(COLOR_STATUS)
            typeface = fontSemibold
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        cancelButton = Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            textSize = 15f
            setTextColor(COLOR_TEXT)
            typeface = fontSemibold
            minHeight = dp(50)
            minWidth = dp(150)
            background = pill(COLOR_PANEL, dp(26))
            setOnClickListener { cancelCountdown() }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(20), dp(28), 0)
            addView(title, wrap())
            addView(subtitle, topMargin(wrap(), 8))
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(buildMicStack(), centeredWrap())
            addView(status, topMargin(centeredWrap(), 34))
            addView(cancelButton, topMargin(wrap(), 18))
        }

        val volume = buildVolumeControl()
        val song = buildSongPill()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, wrap())
            addView(center, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(volume, wrap())
            addView(song, wrap())
        }

        entranceViews = listOf(header, micStack, status, volume, song)

        return FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(COLOR_BG_TOP, COLOR_BG_MID, COLOR_BG_BOTTOM)
            )
            addView(column, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(settingsIcon(), FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(14)
            })
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
    }

    /** The hero: radial glow + two sound-wave rings + a gradient mic face. */
    private fun buildMicStack(): View {
        val glow = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(150).toFloat()
                colors = intArrayOf(COLOR_GLOW_IN, COLOR_GLOW_MID, COLOR_GLOW_OUT)
            }
        }
        val ringOuter = View(this).apply { background = ringDrawable(COLOR_RING_FAINT) }
        val ringInner = View(this).apply { background = ringDrawable(COLOR_RING) }

        val mic = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(COLOR_GLYPH)
        }
        micButton = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(COLOR_ACCENT_BRIGHT, COLOR_ACCENT_DEEP)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), COLOR_RIM)
            }
            addView(mic, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
            isClickable = true
            isFocusable = true
            setOnClickListener { startListening() }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN ->
                        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
                false
            }
        }

        micStack = FrameLayout(this).apply {
            addView(glow, centeredFrame(300))
            addView(ringOuter, centeredFrame(248))
            addView(ringInner, centeredFrame(206))
            addView(micButton, centeredFrame(168))
        }
        return micStack
    }

    private fun buildVolumeControl(): View {
        volumeLabel = TextView(this).apply {
            textSize = 11.5f
            setTextColor(COLOR_MUTED)
            typeface = fontSemibold
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        volumeBar = SeekBar(this).apply {
            max = 100
            progress = prefs.announceVolume
            progressTintList = ColorStateList.valueOf(COLOR_ACCENT)
            thumbTintList = ColorStateList.valueOf(COLOR_ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(COLOR_TRACK)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    volumeLabel.text = "ANNOUNCEMENT VOLUME  ·  $value"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    prefs.announceVolume = seekBar.progress
                }
            })
        }
        volumeLabel.text = "ANNOUNCEMENT VOLUME  ·  ${volumeBar.progress}"

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(20), dp(34), dp(12))
            addView(volumeLabel, wrap())
            addView(volumeBar, topMargin(wrap(), 12))
        }
    }

    /** A one-tap pill that plays the configured Sonos Favorite on the broadcast speakers. */
    private fun buildSongPill(): View {
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_music_note)
            setColorFilter(COLOR_ACCENT)
        }
        val label = TextView(this).apply {
            text = prefs.quickPlayFavorite
            textSize = 15f
            setTextColor(COLOR_TEXT)
            typeface = fontSemibold
            includeFontPadding = false
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pill(COLOR_PANEL, dp(27))
            setPadding(dp(20), dp(15), dp(20), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                QuickPlayService.start(this@TalkActivity)
                status.text = "Playing ${prefs.quickPlayFavorite}…"
            }
            addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) })
            addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), 0, dp(34), dp(28))
            addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun settingsIcon(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.ic_settings)
        setColorFilter(COLOR_MUTED)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(COLOR_GEAR_BG)
        }
        contentDescription = "Widget setup"
        setPadding(dp(11), dp(11), dp(11), dp(11))
        isClickable = true
        isFocusable = true
        setOnClickListener { startActivity(Intent(this@TalkActivity, MainActivity::class.java)) }
    }

    private fun playEntrance() {
        entranceViews.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(14).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(80L + index * 70L)
                .setDuration(460)
                .start()
        }
        micButton.scaleX = 0.86f
        micButton.scaleY = 0.86f
        micButton.animate().scaleX(1f).scaleY(1f).setStartDelay(150L).setDuration(520).start()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your announcement")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (_: ActivityNotFoundException) {
            status.text = "No speech recognizer available"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (heard.isNullOrEmpty()) {
            status.text = "Didn't catch that — try again"
            return
        }
        startCancelWindow(heard)
    }

    private fun startCancelWindow(text: String) {
        setListeningState(true)
        countdown?.cancel()
        countdown = object : CountDownTimer(CANCEL_WINDOW_MS, 1_000) {
            override fun onTick(msUntilFinished: Long) {
                val seconds = (msUntilFinished / 1000) + 1
                status.text = "Sending in $seconds…\n“$text”"
            }

            override fun onFinish() {
                AnnounceService.start(this@TalkActivity, text, prefs.announceVolume)
                setListeningState(false)
                status.text = "Sent “$text”"
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        setListeningState(false)
        status.text = "Cancelled"
    }

    private fun setListeningState(listening: Boolean) {
        micButton.isEnabled = !listening
        micButton.animate().alpha(if (listening) 0.35f else 1f).setDuration(200).start()
        cancelButton.visibility = if (listening) View.VISIBLE else View.GONE
    }

    private fun showIdle() {
        setListeningState(false)
        if (!status.text.startsWith("Sent") && !status.text.startsWith("Cancelled")) {
            status.text = "Tap to talk"
        }
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 200)
    }

    override fun onDestroy() {
        countdown?.cancel()
        super.onDestroy()
    }

    private fun ringDrawable(strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x00000000)
        setStroke(dp(1), strokeColor)
    }

    private fun pill(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun centeredFrame(sizeDp: Int) =
        FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER)

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun centeredWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun topMargin(params: LinearLayout.LayoutParams, margin: Int) =
        params.apply { topMargin = dp(margin) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SPEECH = 1
        private const val CANCEL_WINDOW_MS = 3_000L
        const val EXTRA_ANNOUNCE_NOW = "announce_now"

        private val COLOR_BG_TOP = 0xFF0E2026.toInt()
        private val COLOR_BG_MID = 0xFF0A161B.toInt()
        private val COLOR_BG_BOTTOM = 0xFF05080B.toInt()
        private val COLOR_TEXT = 0xFFF1F7F5.toInt()
        private val COLOR_STATUS = 0xFFB7CCC5.toInt()
        private val COLOR_MUTED = 0xFF85A099.toInt()
        private val COLOR_ACCENT = 0xFF5AE0B8.toInt()
        private val COLOR_ACCENT_BRIGHT = 0xFF85F2CF.toInt()
        private val COLOR_ACCENT_DEEP = 0xFF1FB392.toInt()
        private val COLOR_GLYPH = 0xFF06140F.toInt()
        private val COLOR_RIM = 0x40FFFFFF
        private val COLOR_RING = 0x4D5AE0B8
        private val COLOR_RING_FAINT = 0x265AE0B8
        private val COLOR_GLOW_IN = 0x665AE0B8
        private val COLOR_GLOW_MID = 0x265AE0B8
        private val COLOR_GLOW_OUT = 0x005AE0B8
        private val COLOR_PANEL = 0xFF1B2A30.toInt()
        private val COLOR_TRACK = 0xFF243239.toInt()
        private val COLOR_GEAR_BG = 0x16FFFFFF
    }
}
