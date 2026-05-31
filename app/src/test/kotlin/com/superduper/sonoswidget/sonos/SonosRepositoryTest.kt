package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosRepositoryTest {
    @Test
    fun resolvesSelectedRoomToCoordinatorBeforePlayPause() {
        val dining = SonosPlayer(
            roomName = "Dining Room",
            uuid = "uuid:RINCON_DINING",
            baseUrl = "http://192.168.1.20:1400",
            services = SonosServices("/MediaRenderer/AVTransport/Control", "/ZoneGroupTopology/Control")
        )
        val kitchen = dining.copy(
            roomName = "Kitchen",
            uuid = "uuid:RINCON_KITCHEN",
            baseUrl = "http://192.168.1.21:1400"
        )
        val fake = FakeSonosGateway(
            players = listOf(dining, kitchen),
            zoneMembers = listOf(
                ZoneGroupMember("RINCON_DINING", "Dining Room", "RINCON_KITCHEN"),
                ZoneGroupMember("RINCON_KITCHEN", "Kitchen", "RINCON_KITCHEN")
            ),
            playbackByUuid = mapOf(
                "uuid:RINCON_KITCHEN" to SonosPlayback(
                    roomName = "Kitchen",
                    state = PlaybackState.PAUSED,
                    track = SonosTrack("Song", "Artist", null),
                    actions = SonosTransportActions(canPlay = true, canPause = false, canNext = true, canPrevious = true)
                )
            )
        )

        val repository = SonosRepository(fake)
        repository.togglePlayPause("Dining Room")

        assertEquals(listOf("Play:uuid:RINCON_KITCHEN"), fake.commands)
    }

    @Test
    fun ignoresNextWhenTransportActionIsUnavailable() {
        val player = SonosPlayer(
            roomName = "Dining Room",
            uuid = "uuid:RINCON_DINING",
            baseUrl = "http://192.168.1.20:1400",
            services = SonosServices("/MediaRenderer/AVTransport/Control", "/ZoneGroupTopology/Control")
        )
        val fake = FakeSonosGateway(
            players = listOf(player),
            playbackByUuid = mapOf(
                player.uuid to SonosPlayback(
                    roomName = "Dining Room",
                    state = PlaybackState.PLAYING,
                    track = SonosTrack("Song", "Artist", null),
                    actions = SonosTransportActions(canPlay = false, canPause = true, canNext = false, canPrevious = true)
                )
            )
        )

        SonosRepository(fake).next("Dining Room")

        assertEquals(emptyList<String>(), fake.commands)
    }

    @Test
    fun reportsUnavailableWhenRoomIsMissing() {
        val repository = SonosRepository(FakeSonosGateway(players = emptyList()))

        val result = repository.currentPlayback("Dining Room")

        assertTrue(result is SonosResult.Unavailable)
    }

    private class FakeSonosGateway(
        private val players: List<SonosPlayer>,
        private val zoneMembers: List<ZoneGroupMember> = emptyList(),
        private val playbackByUuid: Map<String, SonosPlayback> = emptyMap()
    ) : SonosGateway {
        val commands = mutableListOf<String>()

        override fun discoverPlayers(): List<SonosPlayer> = players

        override fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember> = zoneMembers

        override fun playback(player: SonosPlayer): SonosPlayback = playbackByUuid.getValue(player.uuid)

        override fun play(player: SonosPlayer) {
            commands += "Play:${player.uuid}"
        }

        override fun pause(player: SonosPlayer) {
            commands += "Pause:${player.uuid}"
        }

        override fun next(player: SonosPlayer) {
            commands += "Next:${player.uuid}"
        }

        override fun previous(player: SonosPlayer) {
            commands += "Previous:${player.uuid}"
        }
    }
}
