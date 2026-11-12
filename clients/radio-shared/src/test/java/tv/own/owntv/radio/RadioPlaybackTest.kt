// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The on-screen play / pause button (owner 12.11: «пауза не работает»). */
class RadioPlaybackTest {
    @Test fun pauseWhilePlayingOrRebuffering() {
        assertEquals(RadioPlayback.Action.PAUSE, RadioPlayback.toggle(false, Player.STATE_READY, true))
        // A live stream re-buffers: isPlaying is false, but the user still hears it and wants a pause.
        assertEquals(RadioPlayback.Action.PAUSE, RadioPlayback.toggle(false, Player.STATE_BUFFERING, true))
    }

    @Test fun resumeFromPauseKeepsTheStream() {
        assertEquals(RadioPlayback.Action.RESUME, RadioPlayback.toggle(false, Player.STATE_READY, false))
        assertEquals(RadioPlayback.Action.RESUME, RadioPlayback.toggle(false, Player.STATE_BUFFERING, false))
    }

    @Test fun restartAfterErrorOrStop() {
        assertEquals(RadioPlayback.Action.RESTART, RadioPlayback.toggle(true, Player.STATE_IDLE, false))
        assertEquals(RadioPlayback.Action.RESTART, RadioPlayback.toggle(false, Player.STATE_IDLE, true))
    }

    @Test fun iconFollowsTheWishNotTheBuffer() {
        val playing = RadioPlayback.status(false, Player.STATE_READY, true)
        val buffering = RadioPlayback.status(false, Player.STATE_BUFFERING, true)
        val paused = RadioPlayback.status(false, Player.STATE_READY, false)
        val pausedWhileBuffering = RadioPlayback.status(false, Player.STATE_BUFFERING, false)
        assertEquals(RadioPlayer.Status.PLAYING, playing)
        assertEquals(RadioPlayer.Status.BUFFERING, buffering)
        assertEquals(RadioPlayer.Status.PAUSED, paused)
        assertEquals(RadioPlayer.Status.PAUSED, pausedWhileBuffering)
        assertTrue(RadioPlayback.showsPause(playing))
        assertTrue(RadioPlayback.showsPause(buffering))
        assertFalse(RadioPlayback.showsPause(paused))
        assertEquals(RadioPlayer.Status.ERROR, RadioPlayback.status(true, Player.STATE_IDLE, false))
    }

    /** Pressing the button twice brings back the state it started from. */
    @Test fun toggleTwiceIsANoOp() {
        var wish = true
        repeat(2) {
            wish = when (RadioPlayback.toggle(false, Player.STATE_READY, wish)) {
                RadioPlayback.Action.PAUSE -> false
                RadioPlayback.Action.RESUME -> true
                RadioPlayback.Action.RESTART -> true
            }
        }
        assertTrue(wish)
    }
}
