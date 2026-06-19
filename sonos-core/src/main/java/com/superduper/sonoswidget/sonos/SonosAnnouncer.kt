package com.superduper.sonoswidget.sonos

import android.util.Log

/** Result of an announcement: how many group coordinators we played on. */
data class AnnounceOutcome(val speakersReached: Int)

/**
 * The Sonos-side operations an announcement needs, behind a seam so the
 * orchestration in [SonosAnnouncer] can be unit-tested without a network.
 */
interface AnnounceTransport {
    /** One coordinator per group serving [rooms] (empty = all groups). */
    fun coordinators(rooms: Set<String>): List<SonosPlayer>
    fun snapshot(player: SonosPlayer): TransportSnapshot
    fun applyAnnouncement(player: SonosPlayer, clipUrl: String, volume: Int)
    fun restore(player: SonosPlayer, snapshot: TransportSnapshot)
}

/**
 * Plays a short clip on every Sonos group, then restores what was playing.
 *
 * Each group coordinator is snapshotted, switched to the clip at [volume], then
 * put back. Per-coordinator rather than re-grouping the house, so the original
 * topology is never disturbed.
 */
class SonosAnnouncer(
    private val transport: AnnounceTransport,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun announce(
        clipUrl: String,
        volume: Int,
        clipDurationMs: Long,
        targetRooms: Set<String> = emptySet()
    ): AnnounceOutcome {
        val coordinators = transport.coordinators(targetRooms)
        if (coordinators.isEmpty()) {
            Log.w(TAG, "No Sonos coordinators found; nothing to announce on")
            return AnnounceOutcome(0)
        }
        Log.i(TAG, "Announcing on ${coordinators.size} coordinator(s): ${coordinators.map { it.roomName }}")

        val snapshots = coordinators.map { player ->
            player to runCatching { transport.snapshot(player) }
                .onFailure { Log.w(TAG, "Snapshot failed for ${player.roomName}", it) }
                .getOrNull()
        }

        coordinators.forEach { player ->
            runCatching { transport.applyAnnouncement(player, clipUrl, volume) }
                .onFailure { Log.w(TAG, "Announce failed for ${player.roomName}", it) }
        }

        sleeper(LEAD_IN_MS + clipDurationMs + TAIL_MS)

        snapshots.forEach { (player, snapshot) ->
            if (snapshot == null) return@forEach
            runCatching { transport.restore(player, snapshot) }
                .onFailure { Log.w(TAG, "Restore failed for ${player.roomName}", it) }
        }

        return AnnounceOutcome(coordinators.size)
    }

    companion object {
        private const val TAG = "SonosWidget"

        /** Sonos buffers a network URI before audio starts; wait it out before timing the tail. */
        const val LEAD_IN_MS = 1_800L
        const val TAIL_MS = 800L
    }
}

/**
 * Real [AnnounceTransport] over UPnP/SOAP. Reuses [SonosGateway] for discovery and
 * topology, and issues the announcement-specific AVTransport / RenderingControl calls.
 */
class NetworkAnnounceTransport(
    private val gateway: SonosGateway,
    private val httpClient: SonosHttpClient = JavaNetSonosHttpClient()
) : AnnounceTransport {

    override fun coordinators(rooms: Set<String>): List<SonosPlayer> {
        val players = gateway.discoverPlayers()
        if (players.isEmpty()) return emptyList()

        val topology = players.firstNotNullOfOrNull { player ->
            runCatching { gateway.zoneGroupMembers(player) }.getOrNull()?.takeIf { it.isNotEmpty() }
        } ?: emptyList()

        return SonosTopology.coordinatorsForRooms(players, topology, rooms)
    }

    override fun snapshot(player: SonosPlayer): TransportSnapshot {
        val transportState = SonosSoap.parsePlaybackState(av(player, "GetTransportInfo"))
        val media = av(player, "GetMediaInfo")
        val position = av(player, "GetPositionInfo")
        val volume = player.services.renderingControlControlUrl?.let {
            SonosSoap.parseVolume(renderingControl(player, "GetVolume"))
        }
        return TransportSnapshot(
            state = transportState,
            volume = volume,
            currentUri = SonosSoap.parseCurrentUri(media),
            currentUriMetaData = SonosSoap.parseCurrentUriMetaData(media),
            trackNumber = SonosSoap.parseTrackNumber(position),
            relTime = SonosSoap.parseRelTime(position)
        ).also { Log.i(TAG, "Snapshot ${player.roomName}: state=${it.state} vol=${it.volume} uri=${it.currentUri}") }
    }

    override fun applyAnnouncement(player: SonosPlayer, clipUrl: String, volume: Int) {
        setVolume(player, volume)
        avPost(player, "SetAVTransportURI", SonosSoap.setAvTransportUriEnvelope(clipUrl, ""))
        avPost(player, "Play", SonosSoap.avTransportEnvelope("Play", "<Speed>1</Speed>"))
        Log.i(TAG, "Playing clip on ${player.roomName} at volume $volume")
    }

    override fun restore(player: SonosPlayer, snapshot: TransportSnapshot) {
        snapshot.volume?.let { setVolume(player, it) }

        val uri = snapshot.currentUri
        if (uri.isNullOrBlank()) {
            Log.i(TAG, "Nothing was playing on ${player.roomName}; restored volume only")
            return
        }

        avPost(player, "SetAVTransportURI", SonosSoap.setAvTransportUriEnvelope(uri, snapshot.currentUriMetaData ?: ""))

        if (uri.startsWith("x-rincon-queue") && (snapshot.trackNumber ?: 0) > 0) {
            runCatching { avPost(player, "Seek", SonosSoap.seekEnvelope("TRACK_NR", snapshot.trackNumber.toString())) }
        }
        snapshot.relTime?.let { rel ->
            if (rel != "0:00:00" && rel != "00:00:00" && rel != "NOT_IMPLEMENTED") {
                runCatching { avPost(player, "Seek", SonosSoap.seekEnvelope("REL_TIME", rel)) }
            }
        }

        if (snapshot.state == PlaybackState.PLAYING) {
            avPost(player, "Play", SonosSoap.avTransportEnvelope("Play", "<Speed>1</Speed>"))
        }
        Log.i(TAG, "Restored ${player.roomName} to state=${snapshot.state}")
    }

    private fun setVolume(player: SonosPlayer, volume: Int) {
        val controlUrl = player.services.renderingControlControlUrl ?: return
        httpClient.soap(
            url = player.baseUrl + controlUrl,
            soapAction = SonosSoap.renderingControlSoapAction("SetVolume"),
            envelope = SonosSoap.setVolumeEnvelope(volume)
        )
    }

    /** Send an AVTransport action whose envelope is just the bare action (read calls). */
    private fun av(player: SonosPlayer, action: String): String =
        avPost(player, action, SonosSoap.avTransportEnvelope(action))

    private fun avPost(player: SonosPlayer, action: String, envelope: String): String =
        httpClient.soap(
            url = player.baseUrl + player.services.avTransportControlUrl,
            soapAction = SonosSoap.avTransportSoapAction(action),
            envelope = envelope
        )

    private fun renderingControl(player: SonosPlayer, action: String): String =
        httpClient.soap(
            url = player.baseUrl + player.services.renderingControlControlUrl,
            soapAction = SonosSoap.renderingControlSoapAction(action),
            envelope = SonosSoap.renderingControlEnvelope(action)
        )

    companion object {
        private const val TAG = "SonosWidget"
    }
}
