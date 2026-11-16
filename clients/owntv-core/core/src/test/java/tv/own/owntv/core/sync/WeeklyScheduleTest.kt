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

    // Guide: Monday 18:00 only (owner 16.11).
    @Test fun guideNotMondayMorning() = assertFalse(guide(t(9, 0, 30), t(9, 9, 30)))
    @Test fun guideMondayEighteen() = assertTrue(guide(t(5, 10), t(9, 18, 10)))
    @Test fun guideOnceAfterEighteen() = assertFalse(guide(t(9, 18, 5), t(9, 22)))
    @Test fun guideQuietRestOfWeek() = assertFalse(guide(t(9, 18, 5), t(13, 20)))
    @Test fun guideCatchUpWhenBoxWasOff() = assertTrue(guide(t(2, 12), t(12, 20)))
    @Test fun guideMondayBeforeEighteenUsesLastWeek() = assertFalse(guide(t(2, 18, 5), t(9, 12)))

    // Offer: Monday/Tuesday from 06:00 while this week's guide is missing.
    @Test fun offerMondayMorning() = assertTrue(WeeklySchedule.isGuideOfferTime(t(2, 18, 5), t(9, 7), zone))
    @Test fun offerNotBeforeSix() = assertFalse(WeeklySchedule.isGuideOfferTime(t(2, 18, 5), t(9, 5), zone))
    @Test fun offerTuesday() = assertTrue(WeeklySchedule.isGuideOfferTime(t(2, 18, 5), t(10, 8), zone))
    @Test fun offerGoneOnceLoaded() = assertFalse(WeeklySchedule.isGuideOfferTime(t(9, 18, 5), t(10, 8), zone))
    @Test fun offerNotWednesday() = assertFalse(WeeklySchedule.isGuideOfferTime(t(2, 18, 5), t(11, 8), zone))

    // Catalogue: every morning from 06:00.
    @Test fun catalogEveryMorning() = assertTrue(catalog(t(9, 20), t(10, 6, 5)))
    @Test fun catalogOnceADay() = assertFalse(catalog(t(10, 6, 5), t(10, 18)))
    @Test fun catalogNotBeforeSix() = assertFalse(catalog(t(9, 20), t(10, 5)))
    @Test fun neverSyncedIsDue() = assertTrue(catalog(null, t(12, 8)) && guide(null, t(12, 8)))
}
