package com.superduper.sonoswidget.sonos

data class SonosServices(
    val avTransportControlUrl: String,
    val zoneGroupTopologyControlUrl: String?
)

data class SonosPlayer(
    val roomName: String,
    val uuid: String,
    val baseUrl: String,
    val services: SonosServices
)

data class SonosTrack(
    val title: String?,
    val artist: String?,
    val artworkUrl: String?
)

data class SonosTransportActions(
    val canPlay: Boolean,
    val canPause: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean
) {
    companion object {
        val none = SonosTransportActions(
            canPlay = false,
            canPause = false,
            canNext = false,
            canPrevious = false
        )
    }
}

enum class PlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
    UNKNOWN
}

data class SonosPlayback(
    val roomName: String,
    val state: PlaybackState,
    val track: SonosTrack,
    val actions: SonosTransportActions
)

data class ZoneGroupMember(
    val uuid: String,
    val roomName: String,
    val coordinatorUuid: String
)
