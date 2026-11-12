package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.TimeZone

/**
 * When playlists, portals and guides are refreshed on their own (owner's decision, 12.11):
 * Mondays at 09:00, 15:00 and 19:00 and Tuesdays at 09:00 (device time). Nothing refreshes just because the
 * app was opened. A device that was off on those days catches up once, at the first launch after them —
 * "nothing synced yet this week" — and then stays quiet until the next Monday. Manual refresh is unaffected.
 */
object WeeklySchedule {
    private val MONDAY_HOURS = intArrayOf(9, 15, 19)
    private const val TUESDAY_HOUR = 9

    /** True when a refresh is due now for a source last synced at [lastSyncAt] (0 / null = never). */
    fun isDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val last = lastSyncAt ?: 0L
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val weekStart = (now.clone() as Calendar).apply {
            val back = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
            add(Calendar.DAY_OF_YEAR, -back)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        fun at(dayOffset: Int, hour: Int): Long = (weekStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset); set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
        val tuesday = at(1, TUESDAY_HOUR)
        // Tuesday 09:00 or later (also Wed…Sun, and Monday before 09:00 counts as "last week" below): due only if
        // nothing was synced since this Monday began.
        if (nowMs >= tuesday) return last < weekStart.timeInMillis
        // Monday: due when the latest slot already passed has no sync after it.
        val latestMonday = MONDAY_HOURS.map { at(0, it) }.filter { it <= nowMs }.maxOrNull()
        if (latestMonday != null) return last < latestMonday
        // Before Monday 09:00: nothing is scheduled yet; catch up only if the whole last week had no sync.
        return last < weekStart.timeInMillis - 7L * 24 * 3600 * 1000
    }
}
