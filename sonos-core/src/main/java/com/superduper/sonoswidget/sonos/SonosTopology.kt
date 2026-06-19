package com.superduper.sonoswidget.sonos

/**
 * Pure topology resolution shared by the announce and favorite-play features:
 * maps a set of target room names to the players / group coordinators to act on.
 * An empty room set means "all speakers".
 */
object SonosTopology {

    /** Players for the given rooms (empty rooms = all players). */
    fun playersForRooms(players: List<SonosPlayer>, rooms: Set<String>): List<SonosPlayer> =
        if (rooms.isEmpty()) players else players.filter { it.roomName in rooms }

    /**
     * The distinct group coordinators serving the given rooms (empty rooms = every
     * coordinator). Playing on a coordinator plays its whole group. Falls back to the
     * rooms' own players when topology is unavailable.
     */
    fun coordinatorsForRooms(
        players: List<SonosPlayer>,
        members: List<ZoneGroupMember>,
        rooms: Set<String>
    ): List<SonosPlayer> {
        if (players.isEmpty()) return emptyList()
        if (members.isEmpty()) return playersForRooms(players, rooms)

        val coordinatorUuids = if (rooms.isEmpty()) {
            members.map { it.coordinatorUuid.normalizedUuid() }.toSet()
        } else {
            members.filter { it.roomName in rooms }.map { it.coordinatorUuid.normalizedUuid() }.toSet()
        }
        return players
            .filter { it.uuid.normalizedUuid() in coordinatorUuids }
            .ifEmpty { playersForRooms(players, rooms) }
    }

    private fun String.normalizedUuid(): String = removePrefix("uuid:").trim()
}
