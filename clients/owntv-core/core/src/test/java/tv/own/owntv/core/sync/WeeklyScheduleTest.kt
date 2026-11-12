// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.TimeZone
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class WeeklyScheduleTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")

    /** day: 2026-11-09 is a Monday. */
    private fun t(day: Int, hour: Int, min: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.NOVEMBER, day, hour, min) }.timeInMillis

    private fun due(last: Long?, now: Long) = WeeklySchedule.isDue(last, now, zone)

    @Test fun mondayAfterNineWithoutSyncIsDue() = assertTrue(due(t(2, 10), t(9, 9, 30)))
    @Test fun mondaySyncedAfterSlotIsNotDue() = assertFalse(due(t(9, 9, 5), t(9, 12)))
    @Test fun nextSlotMakesItDueAgain() = assertTrue(due(t(9, 9, 5), t(9, 15, 10)))
    @Test fun tuesdayAfterMondaySyncIsNotDue() = assertFalse(due(t(9, 19, 5), t(10, 10)))
    @Test fun wednesdayWithNoSyncThisWeekIsDue() = assertTrue(due(t(5, 12), t(11, 20)))
    @Test fun wednesdayAfterMondaySyncIsNotDue() = assertFalse(due(t(9, 15, 30), t(11, 20)))
    @Test fun mondayBeforeNineIsQuiet() = assertFalse(due(t(4, 12), t(9, 8)))
    @Test fun neverSyncedIsDue() = assertTrue(due(null, t(12, 8)))
}
