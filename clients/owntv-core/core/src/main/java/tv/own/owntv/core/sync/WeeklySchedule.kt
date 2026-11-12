package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.TimeZone

/**
 * When playlists, portals and guides are refreshed on their own (owner's decision, 12.11, evening):
 *
 * - **Guides** (the portal's own guide, a playlist's url-tvg, the reserve): Monday 09:00 and 15:00, and
 *   Tuesday 09:00. The operator loads next week's guide on Monday, so Monday morning tries, 15:00 tries
 *   again if the morning found nothing new, and Tuesday morning has it for certain.
 * - **Catalogues** (channels, films, series): Tuesday 09:00, every week, whether or not the app was used on
 *   Monday — then the guide right after it, one after the other in the refresh queue.
 *
 * Device time. Nothing refreshes just because the app was opened; a box that was off at a slot catches up
 * once, at the first launch after it. Manual refresh is unaffected.
 */
object WeeklySchedule {
    private val GUIDE_SLOTS = listOf(0 to 9, 0 to 15, 1 to 9) // (days after Monday, hour)
    private val CATALOG_SLOTS = listOf(1 to 9)

    /** A catalogue (playlist or portal) refresh is due now. */
    fun isCatalogDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        dueAt(CATALOG_SLOTS, lastSyncAt, nowMs, zone)

    /** A guide refresh is due now. */
    fun isGuideDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        dueAt(GUIDE_SLOTS, lastSyncAt, nowMs, zone)

    /** Kept for callers that do not tell the two apart: the catalogue rule. */
    fun isDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        isCatalogDue(lastSyncAt, nowMs, zone)

    /** Due when the latest slot already passed (this week's, or last week's) has no sync after it. */
    private fun dueAt(slots: List<Pair<Int, Int>>, lastSyncAt: Long?, nowMs: Long, zone: TimeZone): Boolean {
        val last = lastSyncAt ?: 0L
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val monday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7)) // Monday = 0
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        fun at(weekOffset: Int, day: Int, hour: Int): Long = (monday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, weekOffset * 7 + day); set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
        val latest = (slots.map { (d, h) -> at(0, d, h) } + slots.map { (d, h) -> at(-1, d, h) })
            .filter { it <= nowMs }.maxOrNull() ?: return last == 0L
        return last < latest
    }
}
