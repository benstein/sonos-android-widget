package com.superduper.sonoswidget.wear

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

/**
 * Watch remote: tap, speak, and the phone announces it on every Sonos speaker.
 * The watch only captures speech and forwards the text over the Data Layer; all
 * the Sonos work happens on the phone.
 */
class WearTalkActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var talkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())

        // Test / automation hook: forward a fixed phrase without the mic.
        intent?.getStringExtra(EXTRA_SEND_NOW)?.trim()?.takeIf { it.isNotEmpty() }?.let { sendToPhone(it) }
    }

    private fun buildContent(): View {
        status = TextView(this).apply {
            text = "Tap to talk"
            textSize = 14f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        talkButton = Button(this).apply {
            text = "Talk"
            isAllCaps = false
            textSize = 18f
            setTextColor(COLOR_BUTTON_TEXT)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_ACCENT)
            }
            setOnClickListener { startListening() }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(COLOR_BACKGROUND)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(talkButton, LinearLayout.LayoutParams(dp(120), dp(120)))
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Announce")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (_: ActivityNotFoundException) {
            status.text = "No voice input"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (heard.isNullOrEmpty()) {
            status.text = "Didn't catch that"
            return
        }
        sendToPhone(heard)
    }

    private fun sendToPhone(text: String) {
        status.text = "Sending…"
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    status.text = "Phone not connected"
                    return@addOnSuccessListener
                }
                val messageClient = Wearable.getMessageClient(this)
                val payload = text.toByteArray(Charsets.UTF_8)
                nodes.forEach { node -> messageClient.sendMessage(node.id, ANNOUNCE_PATH, payload) }
                status.text = "Sent: $text"
            }
            .addOnFailureListener { status.text = "Couldn't reach phone" }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SPEECH = 1
        private const val ANNOUNCE_PATH = "/announce"
        const val EXTRA_SEND_NOW = "send_now"

        private val COLOR_BACKGROUND = Color.rgb(8, 13, 18)
        private val COLOR_MUTED = Color.rgb(158, 174, 188)
        private val COLOR_ACCENT = Color.rgb(90, 224, 184)
        private val COLOR_BUTTON_TEXT = Color.rgb(8, 13, 18)
    }
}
