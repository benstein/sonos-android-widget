package com.superduper.sonoswidget.announce

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A synthesized announcement clip and how long it plays for. */
data class AnnouncementClip(val file: File, val durationMs: Long)

/**
 * Renders text to a WAV file using the on-device TTS engine. Blocking, so call it
 * from a background thread (never the main thread). Returns null if synthesis fails.
 */
class WavTextToSpeech(private val context: Context) {

    fun synthesize(text: String): AnnouncementClip? {
        val ready = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val tts = TextToSpeech(context.applicationContext) { status ->
            initStatus = status
            ready.countDown()
        }
        if (!ready.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) || initStatus != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed (status=$initStatus)")
            tts.shutdown()
            return null
        }

        runCatching { tts.language = Locale.getDefault() }

        val output = File(context.cacheDir, "announcement.wav")
        output.delete()

        val done = CountDownLatch(1)
        var success = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                success = true
                done.countDown()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = done.countDown()
            override fun onError(utteranceId: String?, errorCode: Int) = done.countDown()
        })

        val result = tts.synthesizeToFile(text, null, output, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "synthesizeToFile rejected (result=$result)")
            tts.shutdown()
            return null
        }

        val finished = done.await(SYNTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        tts.shutdown()
        if (!finished || !success || !output.exists() || output.length() <= WAV_HEADER_BYTES) {
            Log.w(TAG, "TTS synthesis did not produce audio (finished=$finished success=$success)")
            return null
        }

        val duration = WavInfo.durationMs(output) ?: estimateDurationMs(text)
        Log.i(TAG, "Synthesized ${output.length()} bytes, duration=${duration}ms for: \"$text\"")
        return AnnouncementClip(output, duration)
    }

    private fun estimateDurationMs(text: String): Long =
        (text.length * MS_PER_CHAR).coerceAtLeast(MIN_DURATION_MS)

    companion object {
        private const val TAG = "SonosWidget"
        private const val UTTERANCE_ID = "announcement"
        private const val WAV_HEADER_BYTES = 44L
        private const val INIT_TIMEOUT_MS = 5_000L
        private const val SYNTH_TIMEOUT_MS = 15_000L
        private const val MS_PER_CHAR = 70L
        private const val MIN_DURATION_MS = 1_200L
    }
}

/** Reads playback duration from a PCM WAV header. */
object WavInfo {
    fun durationMs(file: File): Long? {
        return runCatching {
            val header = ByteArray(44)
            file.inputStream().use { it.read(header) }
            val channels = le16(header, 22)
            val sampleRate = le32(header, 24)
            val bitsPerSample = le16(header, 34)
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            if (byteRate <= 0) return null
            val dataBytes = file.length() - 44
            dataBytes * 1000 / byteRate
        }.getOrNull()
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
