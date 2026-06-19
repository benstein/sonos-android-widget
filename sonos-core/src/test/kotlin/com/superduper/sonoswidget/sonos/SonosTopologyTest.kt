package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Test

class SonosTopologyTest {

    private fun player(room: String, uuid: String) = SonosPlayer(
        roomName = room,
        uuid = uuid,
        baseUrl = "http://192.168.1.20:1400",
        services = SonosServices("/AVT", "/ZGT")
    )

    private val dining = player("Dining Room", "uuid:RINCON_DINING")
    private val kitchen = player("Kitchen", "uuid:RINCON_KITCHEN")
    private val office = player("Office", "uuid:RINCON_OFFICE")
    private val all = listOf(dining, kitchen, office)

    // Dining + Kitchen grouped under Kitchen; Office on its own.
    private val members = listOf(
        ZoneGroupMember("RINCON_DINING", "Dining Room", "RINCON_KITCHEN"),
        ZoneGroupMember("RINCON_KITCHEN", "Kitchen", "RINCON_KITCHEN"),
        ZoneGroupMember("RINCON_OFFICE", "Office", "RINCON_OFFICE")
    )

    @Test
    fun playersForRoomsEmptyMeansAll() {
        assertEquals(all, SonosTopology.playersForRooms(all, emptySet()))
    }

    @Test
    fun playersForRoomsFiltersBySelection() {
        assertEquals(
            setOf("Dining Room", "Office"),
            SonosTopology.playersForRooms(all, setOf("Dining Room", "Office")).map { it.roomName }.toSet()
        )
    }

    @Test
    fun coordinatorsEmptyRoomsMeansEveryCoordinator() {
        assertEquals(
            setOf("Kitchen", "Office"),
            SonosTopology.coordinatorsForRooms(all, members, emptySet()).map { it.roomName }.toSet()
        )
    }

    @Test
    fun coordinatorsForSelectedRoomsResolveThroughGroups() {
        // Selecting the Dining Room resolves to its coordinator, Kitchen.
        assertEquals(
            setOf("Kitchen"),
            SonosTopology.coordinatorsForRooms(all, members, setOf("Dining Room")).map { it.roomName }.toSet()
        )
    }

    @Test
    fun coordinatorsFallBackToPlayersWithoutTopology() {
        assertEquals(
            setOf("Office"),
            SonosTopology.coordinatorsForRooms(all, emptyList(), setOf("Office")).map { it.roomName }.toSet()
        )
    }
}
