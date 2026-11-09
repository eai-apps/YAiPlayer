// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.catchup

import java.util.Calendar
import java.util.TimeZone
import tv.own.owntv.core.R

/**
 * Data and calculations for browsing past days' EPG schedule on catchup-enabled channels ("Yesterday on air").
 */
object YesterdayOnAir {

    /** Maximum past days to offer, bounded by provider archive and reasonable horizon. */
    const val MAX_CATCHUP_DAYS = 14
    const val DEFAULT_CATCHUP_DAYS = 7

    data class DayOption(
        val daysAgo: Int,
        val labelRes: Int,
        val startMs: Long,
        val stopMs: Long,
    )

    /**
     * Resolves the start and end timestamp (millis) of a calendar day [daysAgo] in [zone].
     * 0 = today, 1 = yesterday, 2 = 2 days ago, etc.
     */
    fun dayBounds(daysAgo: Int, nowMs: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis
        return startOfDay to endOfDay
    }

    /**
     * Returns list of available past days (1..depth) for a channel with [catchupDays] depth.
     */
    fun availableDays(
        catchupDays: Int,
        nowMs: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): List<DayOption> {
        val depth = when {
            catchupDays <= 0 -> DEFAULT_CATCHUP_DAYS
            else -> catchupDays.coerceIn(1, MAX_CATCHUP_DAYS)
        }
        return (1..depth).map { daysAgo ->
            val (startMs, stopMs) = dayBounds(daysAgo, nowMs, zone)
            val labelRes = when (daysAgo) {
                1 -> R.string.yesterday_day_yesterday
                2 -> R.string.yesterday_day_2_days
                else -> R.string.yesterday_day_ago
            }
            DayOption(
                daysAgo = daysAgo,
                labelRes = labelRes,
                startMs = startMs,
                stopMs = stopMs,
            )
        }
    }

    /**
     * Checks if a channel qualifies for "Yesterday on air": must have catchup enabled.
     */
    fun isSupported(catchup: Boolean, catchupDays: Int): Boolean {
        return catchup || catchupDays > 0
    }
}
