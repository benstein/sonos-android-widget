package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosAnnouncerTest {

    private fun player(room: String, uuid: String) = SonosPlayer(
        roomName = room,
        uuid = uuid,
        baseUrl = "http://192.168.1.20:1400",
        services = SonosServices(
            avTransportControlUrl = "/MediaRenderer/AVTransport/Control",
            zoneGroupTopologyControlUrl = "/ZoneGroupTopology/Control",
            renderingControlControlUrl = "/MediaRenderer/RenderingControl/Control"
        )
    )

    @Test
    fun snapshotsAllThenAppliesAllThenRestoresAll() {
        val living = player("Living Room", "uuid:RINCON_LIVING")
        val kitchen = player("Kitchen", "uuid:RINCON_KITCHEN")
        val transport = RecordingTransport(listOf(living, kitchen))
        var sleptMs = -1L

        val outcome = SonosAnnouncer(transport, sleeper = { sleptMs = it })
            .announce(clipUrl = "http://192.168.1.5:8080/clip.wav", volume = 40, clipDurationMs = 1_500)

        assertEquals(2, outcome.speakersReached)
        assertEquals(
            listOf(
                "snapshot:Living Room",
                "snapshot:Kitchen",
                "apply:Living Room",
                "apply:Kitchen",
                "restore:Living Room",
                "restore:Kitchen"
            ),
            transport.events
        )
    }

    @Test
    fun waitsForLeadInPlusClipPlusTail() {
        val transport = RecordingTransport(listOf(player("Living Room", "uuid:RINCON_LIVING")))
        var sleptMs = -1L

        SonosAnnouncer(transport, sleeper = { sleptMs = it })
            .announce("http://host/clip.wav", volume = 40, clipDurationMs = 2_000)

        assertEquals(SonosAnnouncer.LEAD_IN_MS + 2_000 + SonosAnnouncer.TAIL_MS, sleptMs)
    }

    @Test
    fun doesNothingWhenNoCoordinators() {
        val transport = RecordingTransport(emptyList())
        var slept = false

        val outcome = SonosAnnouncer(transport, sleeper = { slept = true })
            .announce("http://host/clip.wav", volume = 40, clipDurationMs = 1_000)

        assertEquals(0, outcome.speakersReached)
        assertTrue(transport.events.isEmpty())
        assertTrue("should not wait when there is nothing to announce", !slept)
    }

    @Test
    fun restoresOtherCoordinatorsEvenIfOneSnapshotFails() {
        val good = player("Kitchen", "uuid:RINCON_KITCHEN")
        val bad = player("Garage", "uuid:RINCON_GARAGE")
        val transport = RecordingTransport(listOf(bad, good), failSnapshotFor = setOf("Garage"))

        SonosAnnouncer(transport, sleeper = {}).announce("http://host/clip.wav", 40, 1_000)

        // Garage snapshot failed, so it is never restored, but Kitchen still is.
        assertTrue("restore:Kitchen" in transport.events)
        assertTrue("restore:Garage" !in transport.events)
    }

    @Test
    fun networkTransportReducesGroupedPlayersToCoordinators() {
        val dining = player("Dining Room", "uuid:RINCON_DINING")
        val kitchen = player("Kitchen", "uuid:RINCON_KITCHEN")
        val office = player("Office", "uuid:RINCON_OFFICE")
        val gateway = FakeGateway(
            players = listOf(dining, kitchen, office),
            members = listOf(
                // Dining + Kitchen grouped under Kitchen; Office on its own.
                ZoneGroupMember("RINCON_DINING", "Dining Room", "RINCON_KITCHEN"),
                ZoneGroupMember("RINCON_KITCHEN", "Kitchen", "RINCON_KITCHEN"),
                ZoneGroupMember("RINCON_OFFICE", "Office", "RINCON_OFFICE")
            )
        )

        val coordinators = NetworkAnnounceTransport(gateway).coordinators().map { it.roomName }.toSet()

        assertEquals(setOf("Kitchen", "Office"), coordinators)
    }

    @Test
    fun networkTransportFallsBackToAllPlayersWithoutTopology() {
        val a = player("A", "uuid:RINCON_A")
        val b = player("B", "uuid:RINCON_B")
        val gateway = FakeGateway(players = listOf(a, b), members = emptyList())

        val coordinators = NetworkAnnounceTransport(gateway).coordinators().map { it.roomName }.toSet()

        assertEquals(setOf("A", "B"), coordinators)
    }

    private class RecordingTransport(
        private val coords: List<SonosPlayer>,
        private val failSnapshotFor: Set<String> = emptySet()
    ) : AnnounceTransport {
        val events = mutableListOf<String>()

        override fun coordinators(): List<SonosPlayer> = coords

        override fun snapshot(player: SonosPlayer): TransportSnapshot {
            if (player.roomName in failSnapshotFor) error("snapshot boom")
            events += "snapshot:${player.roomName}"
            return TransportSnapshot(PlaybackState.PLAYING, 30, "x-rincon-queue:RINCON#0", "", 1, "0:00:10")
        }

        override fun applyAnnouncement(player: SonosPlayer, clipUrl: String, volume: Int) {
            events += "apply:${player.roomName}"
        }

        override fun restore(player: SonosPlayer, snapshot: TransportSnapshot) {
            events += "restore:${player.roomName}"
        }
    }

    private class FakeGateway(
        private val players: List<SonosPlayer>,
        private val members: List<ZoneGroupMember>
    ) : SonosGateway {
        override fun discoverPlayers(): List<SonosPlayer> = players
        override fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember> = members
        override fun playback(player: SonosPlayer): SonosPlayback = error("unused")
        override fun play(player: SonosPlayer) = error("unused")
        override fun pause(player: SonosPlayer) = error("unused")
        override fun next(player: SonosPlayer) = error("unused")
        override fun previous(player: SonosPlayer) = error("unused")
    }
}
