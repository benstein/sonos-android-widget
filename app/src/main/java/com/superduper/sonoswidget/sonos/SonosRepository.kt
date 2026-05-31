package com.superduper.sonoswidget.sonos

sealed class SonosResult {
    data class Available(val playback: SonosPlayback) : SonosResult()
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
    fun rooms(): List<String> = gateway.discoverPlayers()
        .map { it.roomName }
        .distinct()
        .sorted()

    fun currentPlayback(roomName: String?): SonosResult {
        val selected = selectedPlayer(roomName) ?: return SonosResult.Unavailable("Choose room")
        val coordinator = coordinatorFor(selected)
        return try {
            SonosResult.Available(gateway.playback(coordinator).copy(roomName = selected.roomName))
        } catch (_: Exception) {
            SonosResult.Unavailable("Sonos unavailable")
        }
    }

    fun togglePlayPause(roomName: String?) {
        val coordinator = coordinatorFor(selectedPlayer(roomName) ?: return)
        val playback = gateway.playback(coordinator)
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
        return gateway.discoverPlayers().firstOrNull { it.roomName == roomName }
    }

    private fun coordinatorFor(player: SonosPlayer): SonosPlayer {
        val members = runCatching { gateway.zoneGroupMembers(player) }.getOrDefault(emptyList())
        val coordinatorUuid = members.firstOrNull { it.uuid == player.uuid }?.coordinatorUuid ?: player.uuid
        return gateway.discoverPlayers().firstOrNull { it.uuid == coordinatorUuid } ?: player
    }
}
