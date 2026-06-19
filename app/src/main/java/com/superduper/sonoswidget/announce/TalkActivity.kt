package com.superduper.sonoswidget.announce

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.view.Gravity
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
 */
class TalkActivity : Activity() {

    private lateinit var prefs: SonosPrefs
    private lateinit var status: TextView
    private lateinit var talkButton: Button
    private lateinit var cancelButton: Button
    private lateinit var volumeLabel: TextView
    private lateinit var volumeBar: SeekBar
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SonosPrefs(this)
        requestPermissionsIfNeeded()
        setContentView(buildContent())
        showIdle()

        // Deep-link / automation entry point: announce a fixed phrase without the mic.
        intent?.getStringExtra(EXTRA_ANNOUNCE_NOW)?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            AnnounceService.start(this, text, prefs.announceVolume)
            status.text = "Announcing “$text”…"
        }
    }

    private fun buildContent(): View {
        status = TextView(this).apply {
            textSize = 18f
            setTextColor(COLOR_TEXT)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        talkButton = primaryButton("Tap to talk") { startListening() }.apply { minHeight = dp(120) }
        cancelButton = secondaryButton("Cancel") { cancelCountdown() }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))

            addView(TextView(this@TalkActivity).apply {
                text = "Talk to Sonos"
                textSize = 30f
                setTextColor(COLOR_TEXT)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, matchWrap())
            addView(TextView(this@TalkActivity).apply {
                text = "Speak, and it plays on every speaker"
                textSize = 14f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, topMargin(matchWrap(), 6))
            addView(status, topMargin(matchWrap(), 36))
            addView(talkButton, topMargin(matchWrap(), 36))
            addView(cancelButton, topMargin(matchWrap(), 14))
            addView(buildVolumeControl(), topMargin(matchWrap(), 32))
        }

        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(settingsIcon(), FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            })
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
    }

    private fun buildVolumeControl(): View {
        volumeLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        volumeBar = SeekBar(this).apply {
            max = 100
            progress = prefs.announceVolume
            progressTintList = ColorStateList.valueOf(COLOR_ACCENT)
            thumbTintList = ColorStateList.valueOf(COLOR_ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    volumeLabel.text = "Announcement volume: $value"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    prefs.announceVolume = seekBar.progress
                }
            })
        }
        volumeLabel.text = "Announcement volume: ${volumeBar.progress}"

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(volumeLabel, matchWrap())
            addView(volumeBar, topMargin(matchWrap(), 6))
        }
    }

    private fun settingsIcon(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.ic_settings)
        setColorFilter(COLOR_MUTED)
        contentDescription = "Widget setup"
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { startActivity(Intent(this@TalkActivity, MainActivity::class.java)) }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your announcement")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (_: ActivityNotFoundException) {
            status.text = "No speech recognizer available on this device"
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
        talkButton.visibility = View.GONE
        cancelButton.visibility = View.VISIBLE
        countdown?.cancel()
        countdown = object : CountDownTimer(CANCEL_WINDOW_MS, 1_000) {
            override fun onTick(msUntilFinished: Long) {
                val seconds = (msUntilFinished / 1000) + 1
                status.text = "Sending in $seconds…\n\n“$text”"
            }

            override fun onFinish() {
                AnnounceService.start(this@TalkActivity, text, prefs.announceVolume)
                showIdle()
                status.text = "Sent “$text”"
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        showIdle()
        status.text = "Cancelled"
    }

    private fun showIdle() {
        talkButton.visibility = View.VISIBLE
        cancelButton.visibility = View.GONE
        if (!status.text.startsWith("Sent") && !status.text.startsWith("Cancelled")) {
            status.text = "Ready"
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

    private fun primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 18f
        setTextColor(COLOR_BUTTON_TEXT)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(56)
        background = roundedRect(COLOR_ACCENT, dp(20))
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(COLOR_TEXT)
        minHeight = dp(52)
        background = roundedRect(COLOR_PANEL, dp(18))
        setOnClickListener { onClick() }
    }

    private fun roundedRect(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun topMargin(params: LinearLayout.LayoutParams, margin: Int) =
        params.apply { topMargin = dp(margin) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SPEECH = 1
        private const val CANCEL_WINDOW_MS = 3_000L
        const val EXTRA_ANNOUNCE_NOW = "announce_now"

        private val COLOR_BACKGROUND = Color.rgb(8, 13, 18)
        private val COLOR_PANEL = Color.rgb(38, 51, 64)
        private val COLOR_TEXT = Color.rgb(248, 250, 252)
        private val COLOR_MUTED = Color.rgb(158, 174, 188)
        private val COLOR_ACCENT = Color.rgb(90, 224, 184)
        private val COLOR_BUTTON_TEXT = Color.rgb(8, 13, 18)
    }
}
