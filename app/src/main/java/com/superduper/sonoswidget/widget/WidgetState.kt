package com.superduper.sonoswidget.widget

import com.superduper.sonoswidget.sonos.PlaybackState
import com.superduper.sonoswidget.sonos.SonosPlayback

data class WidgetState(
    val room: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val controlsEnabled: Boolean
) {
    companion object {
        fun chooseRoom() = WidgetState(
            room = "Sonos",
            title = "Choose room",
            artist = "Open app",
            artworkUrl = null,
            isPlaying = false,
            previousEnabled = false,
            nextEnabled = false,
            controlsEnabled = false
        )

        fun unavailable(room: String?, message: String) = WidgetState(
            room = room ?: "Sonos",
            title = message,
            artist = "Tap to retry",
            artworkUrl = null,
            isPlaying = false,
            previousEnabled = false,
            nextEnabled = false,
            controlsEnabled = false
        )

        fun fromPlayback(playback: SonosPlayback) = WidgetState(
            room = playback.roomName,
            title = playback.track.title ?: "Nothing playing",
            artist = playback.track.artist.orEmpty(),
            artworkUrl = playback.track.artworkUrl,
            isPlaying = playback.state == PlaybackState.PLAYING,
            previousEnabled = playback.actions.canPrevious,
            nextEnabled = playback.actions.canNext,
            controlsEnabled = playback.actions.canPlay || playback.actions.canPause
        )
    }
}
