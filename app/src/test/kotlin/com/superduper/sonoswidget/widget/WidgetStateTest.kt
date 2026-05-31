package com.superduper.sonoswidget.widget

import com.superduper.sonoswidget.sonos.PlaybackState
import com.superduper.sonoswidget.sonos.SonosPlayback
import com.superduper.sonoswidget.sonos.SonosTrack
import com.superduper.sonoswidget.sonos.SonosTransportActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test
    fun mapsPlaybackToDisplayState() {
        val playback = SonosPlayback(
            roomName = "Dining Room",
            state = PlaybackState.PLAYING,
            track = SonosTrack("Song", "Artist", "http://example.test/art.jpg"),
            actions = SonosTransportActions(canPlay = false, canPause = true, canNext = true, canPrevious = false)
        )

        val state = WidgetState.fromPlayback(playback)

        assertEquals("Dining Room", state.room)
        assertEquals("Song", state.title)
        assertEquals("Artist", state.artist)
        assertEquals("http://example.test/art.jpg", state.artworkUrl)
        assertTrue(state.isPlaying)
        assertFalse(state.previousEnabled)
        assertTrue(state.nextEnabled)
        assertTrue(state.controlsEnabled)
    }
}
