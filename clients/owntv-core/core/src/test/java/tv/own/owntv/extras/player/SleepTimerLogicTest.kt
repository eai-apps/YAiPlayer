// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.player.SleepTimer

class SleepTimerLogicTest {

    @Test
    fun `CHOICES_MINUTES contains all required presets`() {
        val expected = listOf(15, 30, 45, 60, 90)
        assertEquals(expected, SleepTimer.CHOICES_MINUTES)
    }

    @Test
    fun `minutesLeft rounds up remaining milliseconds correctly`() {
        assertEquals(1, SleepTimer.minutesLeft(1L))
        assertEquals(1, SleepTimer.minutesLeft(60_000L))
        assertEquals(2, SleepTimer.minutesLeft(60_001L))
        assertEquals(15, SleepTimer.minutesLeft(15 * 60_000L))
        assertEquals(90, SleepTimer.minutesLeft(90 * 60_000L))
    }
}
