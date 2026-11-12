// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.TimeZone
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class WeeklyScheduleTest {
    private val zone = TimeZone.getTimeZone("Europe/Berlin")

    /** day: 2026-11-09 is a Monday, 10 a Tuesday. */
    private fun t(day: Int, hour: Int, min: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.NOVEMBER, day, hour, min) }.timeInMillis

    private fun guide(last: Long?, now: Long) = WeeklySchedule.isGuideDue(last, now, zone)
    private fun catalog(last: Long?, now: Long) = WeeklySchedule.isCatalogDue(last, now, zone)

    // Guide: Monday 09:00 and 15:00, Tuesday 09:00.
    @Test fun guideMondayNine() = assertTrue(guide(t(5, 10), t(9, 9, 30)))
    @Test fun guideAfterMorningSyncWaitsForFifteen() = assertFalse(guide(t(9, 9, 5), t(9, 14)))
    @Test fun guideFifteenAgain() = assertTrue(guide(t(9, 9, 5), t(9, 15, 10)))
    @Test fun guideNoNineteen() = assertFalse(guide(t(9, 15, 5), t(9, 20)))
    @Test fun guideTuesdayEvenAfterMonday() = assertTrue(guide(t(9, 15, 5), t(10, 9, 10)))
    @Test fun guideQuietRestOfWeek() = assertFalse(guide(t(10, 9, 5), t(13, 20)))
    @Test fun guideCatchUpWhenBoxWasOff() = assertTrue(guide(t(2, 12), t(12, 20)))
    @Test fun guideMondayBeforeNineQuiet() = assertFalse(guide(t(3, 9, 5), t(9, 8)))

    // Catalogue: Tuesday 09:00 only, every week.
    @Test fun catalogNotOnMonday() = assertFalse(catalog(t(3, 9, 5), t(9, 15, 10)))
    @Test fun catalogTuesdayEvenIfUsedMonday() = assertTrue(catalog(t(9, 20), t(10, 9, 5)))
    @Test fun catalogOnceOnTuesday() = assertFalse(catalog(t(10, 9, 5), t(10, 18)))
    @Test fun catalogCatchUpLaterInWeek() = assertTrue(catalog(t(5, 12), t(12, 20)))
    @Test fun neverSyncedIsDue() = assertTrue(catalog(null, t(12, 8)) && guide(null, t(12, 8)))
}
