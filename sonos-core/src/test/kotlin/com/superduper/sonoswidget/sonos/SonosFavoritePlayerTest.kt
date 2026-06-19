package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosFavoritePlayerTest {

    private fun player(room: String, uuid: String) = SonosPlayer(
        roomName = room,
        uuid = uuid,
        baseUrl = "http://192.168.1.20:1400",
        services = SonosServices("/AVT", "/ZGT")
    )

    private val davids = SonosFavorite("David's Song", "x-sonos-spotify:track", "<DIDL/>")

    @Test
    fun groupsAllTargetsUnderAlphabeticalLeadAndPlays() {
        val transport = FakeTransport(
            players = listOf(player("Kitchen", "uuid:K"), player("Dining Room", "uuid:D"), player("Office", "uuid:O")),
            favorites = listOf(davids)
        )

        val result = SonosFavoritePlayer(transport).play("David's Song", emptySet())

        assertTrue(result is FavoritePlayResult.Played)
        assertEquals(3, (result as FavoritePlayResult.Played).speakers)
        // Lead is the alphabetically-first room; the rest are grouped under it.
        assertEquals("Dining Room", transport.playedOnRoom)
        assertEquals(setOf("Dining Room<-Kitchen", "Dining Room<-Office"), transport.grouped.toSet())
        assertEquals("David's Song", transport.playedFavorite)
    }

    @Test
    fun matchesFavoriteNameCaseInsensitivelyAndTrimmed() {
        val transport = FakeTransport(listOf(player("Den", "uuid:DEN")), listOf(davids))

        val result = SonosFavoritePlayer(transport).play("  david's song ", emptySet())

        assertTrue(result is FavoritePlayResult.Played)
        assertEquals("David's Song", transport.playedFavorite)
    }

    @Test
    fun onlyTargetsSelectedRooms() {
        val transport = FakeTransport(
            players = listOf(player("Kitchen", "uuid:K"), player("Dining Room", "uuid:D"), player("Grill", "uuid:G")),
            favorites = listOf(davids)
        )

        val result = SonosFavoritePlayer(transport).play("David's Song", setOf("Dining Room", "Kitchen"))

        assertEquals(2, (result as FavoritePlayResult.Played).speakers)
        assertEquals("Dining Room", transport.playedOnRoom)
        assertEquals(setOf("Dining Room<-Kitchen"), transport.grouped.toSet())
    }

    @Test
    fun returnsFavoriteNotFoundWhenMissing() {
        val transport = FakeTransport(listOf(player("Den", "uuid:DEN")), listOf(davids))

        val result = SonosFavoritePlayer(transport).play("Nonexistent", emptySet())

        assertTrue(result is FavoritePlayResult.FavoriteNotFound)
        assertEquals(null, transport.playedFavorite)
    }

    @Test
    fun returnsNoSpeakersWhenNoTargets() {
        val transport = FakeTransport(emptyList(), listOf(davids))

        val result = SonosFavoritePlayer(transport).play("David's Song", emptySet())

        assertTrue(result is FavoritePlayResult.NoSpeakers)
    }

    private class FakeTransport(
        private val players: List<SonosPlayer>,
        private val favorites: List<SonosFavorite>
    ) : FavoritePlayTransport {
        val grouped = mutableListOf<String>()
        var playedOnRoom: String? = null
        var playedFavorite: String? = null

        override fun targetPlayers(rooms: Set<String>): List<SonosPlayer> =
            if (rooms.isEmpty()) players else players.filter { it.roomName in rooms }

        override fun favorites(): List<SonosFavorite> = favorites

        override fun group(lead: SonosPlayer, members: List<SonosPlayer>) {
            members.forEach { grouped += "${lead.roomName}<-${it.roomName}" }
        }

        override fun play(lead: SonosPlayer, favorite: SonosFavorite) {
            playedOnRoom = lead.roomName
            playedFavorite = favorite.title
        }
    }
}
