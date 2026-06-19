package com.superduper.sonoswidget.announce

import android.content.Context
import android.util.Log
import com.superduper.sonoswidget.sonos.NetworkAnnounceTransport
import com.superduper.sonoswidget.sonos.SonosAnnouncer
import com.superduper.sonoswidget.sonos.SonosDiscovery

/**
 * Ties the phone-side pieces together: synthesize the text to a WAV, serve it on the
 * LAN, and have every Sonos group play it and then restore. Blocking; runs on a worker.
 */
class Announcer(private val context: Context) {

    data class Result(val speakersReached: Int, val message: String)

    fun announce(text: String, volume: Int, targetRooms: Set<String> = emptySet()): Result {
        val clip = WavTextToSpeech(context).synthesize(text)
            ?: return Result(0, "Couldn't create the announcement audio")

        val ip = LocalAddress.wifiIpv4()
            ?: return Result(0, "Phone isn't on Wi-Fi")

        val server = ClipServer(clip.file)
        return try {
            val port = server.start()
            val url = server.url(ip, port)
            Log.i(TAG, "Announcing \"$text\" via $url at volume $volume")

            val transport = NetworkAnnounceTransport(SonosDiscovery(context))
            val outcome = SonosAnnouncer(transport).announce(url, volume, clip.durationMs, targetRooms)

            if (outcome.speakersReached == 0) {
                Result(0, "No Sonos speakers found")
            } else {
                Result(outcome.speakersReached, "Announced on ${outcome.speakersReached} speaker(s)")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Announce failed", error)
            Result(0, "Announce failed")
        } finally {
            server.stop()
        }
    }

    companion object {
        private const val TAG = "SonosWidget"
        const val DEFAULT_VOLUME = 75
    }
}
