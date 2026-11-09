// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class EpgTimeShiftTest {

    @Test
    fun `clampShiftMinutes enforces bounds -720 to 720`() {
        assertEquals(0, EpgTimeShift.clampShiftMinutes(0))
        assertEquals(720, EpgTimeShift.clampShiftMinutes(720))
        assertEquals(-720, EpgTimeShift.clampShiftMinutes(-720))
        assertEquals(720, EpgTimeShift.clampShiftMinutes(1000))
        assertEquals(-720, EpgTimeShift.clampShiftMinutes(-800))
    }

    @Test
    fun `stepShiftMinutes steps in 30 minute increments`() {
        assertEquals(30, EpgTimeShift.stepShiftMinutes(0, 1))
        assertEquals(-30, EpgTimeShift.stepShiftMinutes(0, -1))
        assertEquals(60, EpgTimeShift.stepShiftMinutes(30, 1))
        assertEquals(720, EpgTimeShift.stepShiftMinutes(710, 1))
        assertEquals(-720, EpgTimeShift.stepShiftMinutes(-710, -1))
    }

    @Test
    fun `applyEpgShift moves timestamps forward or backward`() {
        val baseMs = 1_700_000_000_000L
        // +60 minutes -> +3_600_000 ms
        assertEquals(baseMs + 3_600_000L, EpgTimeShift.applyEpgShift(baseMs, 60))
        // -30 minutes -> -1_800_000 ms
        assertEquals(baseMs - 1_800_000L, EpgTimeShift.applyEpgShift(baseMs, -30))
        // 0 minutes -> unchanged
        assertEquals(baseMs, EpgTimeShift.applyEpgShift(baseMs, 0))
    }

    @Test
    fun `toStoredTime reverts display time to stored time`() {
        val displayMs = 1_700_003_600_000L
        assertEquals(1_700_000_000_000L, EpgTimeShift.toStoredTime(displayMs, 60))
        assertEquals(displayMs, EpgTimeShift.toStoredTime(displayMs, 0))
    }

    @Test
    fun `applyToProgramme modifies startMs and stopMs`() {
        val p = EpgProgrammeEntity(
            sourceId = 1L,
            epgChannelId = "ch1",
            title = "News",
            startMs = 10_000L,
            stopMs = 20_000L,
        )
        val shifted = EpgTimeShift.applyToProgramme(p, 30)
        val expectedDelta = 30 * 60_000L
        assertEquals(10_000L + expectedDelta, shifted.startMs)
        assertEquals(20_000L + expectedDelta, shifted.stopMs)
        assertEquals("News", shifted.title)

        // 0 shift returns same instance
        val unshifted = EpgTimeShift.applyToProgramme(p, 0)
        assertEquals(p, unshifted)
    }

    @Test
    fun `effectiveShift prioritizes channel override over playlist over global`() {
        // Channel override wins
        assertEquals(90, EpgTimeShift.effectiveShift(channelOverride = 90, playlistShift = 60, globalShift = 30))
        assertEquals(-60, EpgTimeShift.effectiveShift(channelOverride = -60, playlistShift = null, globalShift = 0))

        // When no channel override, playlist shift wins
        assertEquals(60, EpgTimeShift.effectiveShift(channelOverride = null, playlistShift = 60, globalShift = 30))
        assertEquals(-120, EpgTimeShift.effectiveShift(channelOverride = null, playlistShift = -120, globalShift = 0))

        // When neither, global shift is used
        assertEquals(30, EpgTimeShift.effectiveShift(channelOverride = null, playlistShift = null, globalShift = 30))
        assertEquals(0, EpgTimeShift.effectiveShift(channelOverride = null, playlistShift = null, globalShift = 0))
    }

    @Test
    fun `formatShiftMinutes formats properly`() {
        assertEquals("0", EpgTimeShift.formatShiftMinutes(0))
        assertEquals("+1h", EpgTimeShift.formatShiftMinutes(60))
        assertEquals("+1:30", EpgTimeShift.formatShiftMinutes(90))
        assertEquals("-2h", EpgTimeShift.formatShiftMinutes(-120))
        assertEquals("-2:30", EpgTimeShift.formatShiftMinutes(-150))
    }
}
