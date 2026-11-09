// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.catchup

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.R

class YesterdayOnAirTest {

    private val fixedZone = TimeZone.getTimeZone("UTC")

    @Test
    fun `dayBounds computes exact 24 hour window for calendar day`() {
        val cal = Calendar.getInstance(fixedZone).apply {
            set(2026, Calendar.NOVEMBER, 9, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = cal.timeInMillis

        // Yesterday (1 day ago): Nov 8 00:00:00 to Nov 9 00:00:00 UTC
        val (startMs, stopMs) = YesterdayOnAir.dayBounds(1, nowMs, fixedZone)

        assertEquals(24 * 60 * 60 * 1000L, stopMs - startMs)

        val checkCal = Calendar.getInstance(fixedZone).apply { timeInMillis = startMs }
        assertEquals(8, checkCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, checkCal.get(Calendar.MONTH))
        assertEquals(0, checkCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, checkCal.get(Calendar.MINUTE))
    }

    @Test
    fun `availableDays returns correct number of days bounded by max`() {
        val days3 = YesterdayOnAir.availableDays(3, zone = fixedZone)
        assertEquals(3, days3.size)
        assertEquals(1, days3[0].daysAgo)
        assertEquals(R.string.yesterday_day_yesterday, days3[0].labelRes)
        assertEquals(2, days3[1].daysAgo)
        assertEquals(R.string.yesterday_day_2_days, days3[1].labelRes)
        assertEquals(3, days3[2].daysAgo)
        assertEquals(R.string.yesterday_day_ago, days3[2].labelRes)

        val daysOverMax = YesterdayOnAir.availableDays(30, zone = fixedZone)
        assertEquals(YesterdayOnAir.MAX_CATCHUP_DAYS, daysOverMax.size)

        val daysZero = YesterdayOnAir.availableDays(0, zone = fixedZone)
        assertEquals(YesterdayOnAir.DEFAULT_CATCHUP_DAYS, daysZero.size)
    }

    @Test
    fun `isSupported checks catchup flag or days`() {
        assertTrue(YesterdayOnAir.isSupported(true, 0))
        assertTrue(YesterdayOnAir.isSupported(false, 3))
        assertFalse(YesterdayOnAir.isSupported(false, 0))
    }
}
