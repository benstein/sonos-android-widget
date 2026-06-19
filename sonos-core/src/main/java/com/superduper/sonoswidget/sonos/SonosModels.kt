package com.superduper.sonoswidget.sonos

data class SonosServices(
    val avTransportControlUrl: String,
    val zoneGroupTopologyControlUrl: String?,
    val renderingControlControlUrl: String? = null,
    val contentDirectoryControlUrl: String? = null
)

/**
 * A Sonos Favorite (from the FV:2 container). [uri] and [metadata] are the exact
 * playable values stored by the favorite, so they carry the correct music-service
 * account info — play them with SetAVTransportURI without reconstructing anything.
 */
data class SonosFavorite(
    val title: String,
    val uri: String,
    val metadata: String
)

/**
 * A snapshot of a coordinator's playback so an announcement can interrupt it and
 * then put everything back. Restoring streaming-service position is best-effort.
 */
data class TransportSnapshot(
    val state: PlaybackState,
    val volume: Int?,
    val currentUri: String?,
    val currentUriMetaData: String?,
    val trackNumber: Int?,
    val relTime: String?
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
