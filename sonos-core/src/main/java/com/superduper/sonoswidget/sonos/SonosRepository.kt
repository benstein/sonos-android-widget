package com.superduper.sonoswidget.sonos

import android.util.Log

sealed class SonosResult {
    data class Available(
        val playback: SonosPlayback,
        val selectedPlayer: SonosPlayer?,
        val coordinator: SonosPlayer
    ) : SonosResult()
    data class Unavailable(val message: String) : SonosResult()
}

interface SonosGateway {
    fun discoverPlayers(): List<SonosPlayer>
    fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember>
    fun playback(player: SonosPlayer): SonosPlayback
    fun play(player: SonosPlayer)
    fun pause(player: SonosPlayer)
    fun next(player: SonosPlayer)
    fun previous(player: SonosPlayer)
}

class SonosRepository(
    private val gateway: SonosGateway
) {
    private var discoveredPlayers: List<SonosPlayer>? = null

    fun rooms(): List<String> = players()
        .map { it.roomName }
        .distinct()
        .sorted()

    fun currentPlayback(roomName: String?): SonosResult {
        val selected = selectedPlayer(roomName) ?: return SonosResult.Unavailable("Choose room")
        val coordinator = coordinatorFor(selected)
        Log.i(TAG, "Selected room=${selected.roomName} uuid=${selected.uuid}; coordinator=${coordinator.roomName} uuid=${coordinator.uuid}")
        return try {
            SonosResult.Available(
                playback = gateway.playback(coordinator).copy(roomName = selected.roomName),
                selectedPlayer = selected,
                coordinator = coordinator
            )
        } catch (error: Exception) {
            Log.w(TAG, "Playback unavailable for room=${selected.roomName} coordinator=${coordinator.roomName}", error)
            SonosResult.Unavailable("Sonos unavailable")
        }
    }

    fun togglePlayPause(roomName: String?) {
        val coordinator = coordinatorFor(selectedPlayer(roomName) ?: return)
        val playback = gateway.playback(coordinator)
        Log.i(TAG, "Toggle playback room=${coordinator.roomName} state=${playback.state}")
        if (playback.state == PlaybackState.PLAYING) {
            gateway.pause(coordinator)
        } else {
            gateway.play(coordinator)
        }
    }

    fun next(roomName: String?) {
        val coordinator = coordinatorFor(selectedPlayer(roomName) ?: return)
        if (gateway.playback(coordinator).actions.canNext) {
            gateway.next(coordinator)
        }
    }

    fun previous(roomName: String?) {
        val coordinator = coordinatorFor(selectedPlayer(roomName) ?: return)
        if (gateway.playback(coordinator).actions.canPrevious) {
            gateway.previous(coordinator)
        }
    }

    private fun selectedPlayer(roomName: String?): SonosPlayer? {
        if (roomName.isNullOrBlank()) return null
        val players = players()
        Log.i(TAG, "Looking for selectedRoom=$roomName in rooms=${players.map { it.roomName }}")
        return players.firstOrNull { it.roomName == roomName }
    }

    private fun coordinatorFor(player: SonosPlayer): SonosPlayer {
        val members = runCatching { gateway.zoneGroupMembers(player) }.getOrDefault(emptyList())
        val coordinatorUuid = members
            .firstOrNull { it.uuid.normalizedUuid() == player.uuid.normalizedUuid() }
            ?.coordinatorUuid
            ?: player.uuid
        return players()
            .firstOrNull { it.uuid.normalizedUuid() == coordinatorUuid.normalizedUuid() }
            ?: player
    }

    private fun players(): List<SonosPlayer> {
        val cached = discoveredPlayers
        if (cached != null) return cached

        return gateway.discoverPlayers().also { players ->
            discoveredPlayers = players
        }
    }

    private fun String.normalizedUuid(): String {
        return removePrefix("uuid:").trim()
    }

    companion object {
        private const val TAG = "SonosWidget"
    }
}
