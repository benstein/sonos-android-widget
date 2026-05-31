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

    @Test
    fun displayRoomPrefixesSelectedRoomWithSonos() {
        val state = WidgetState(
            room = "Dining Room",
            title = "Song",
            artist = "Artist",
            artworkUrl = null,
            isPlaying = true,
            previousEnabled = false,
            nextEnabled = true,
            controlsEnabled = true
        )

        assertEquals("SONOS: Dining Room", state.displayRoom)
    }

    @Test
    fun displayRoomDoesNotDuplicateGenericSonosLabel() {
        val state = WidgetState.chooseRoom()

        assertEquals("SONOS", state.displayRoom)
    }

    @Test
    fun optimisticPlayPauseTogglesPlayingStateAndMarksPending() {
        val state = WidgetState(
            room = "Dining Room",
            title = "Song",
            artist = "Artist",
            artworkUrl = "http://example.test/art.jpg",
            isPlaying = true,
            previousEnabled = true,
            nextEnabled = true,
            controlsEnabled = true
        )

        val optimistic = state.optimisticPlayPause()

        assertFalse(optimistic.isPlaying)
        assertTrue(optimistic.isPending)
        assertEquals("Song", optimistic.title)
        assertEquals("Artist", optimistic.artist)
    }

    @Test
    fun unknownPlaybackKeepsPendingOptimisticPlayingState() {
        val previous = WidgetState(
            room = "Dining Room",
            title = "Old Song",
            artist = "Artist",
            artworkUrl = null,
            isPlaying = true,
            previousEnabled = true,
            nextEnabled = true,
            controlsEnabled = true,
            isPending = true
        )
        val playback = SonosPlayback(
            roomName = "Dining Room",
            state = PlaybackState.UNKNOWN,
            track = SonosTrack("Song", "Artist", null),
            actions = SonosTransportActions(canPlay = true, canPause = true, canNext = true, canPrevious = true)
        )

        val state = WidgetState.fromPlayback(playback, previous)

        assertTrue(state.isPlaying)
        assertTrue(state.isPending)
    }
}
