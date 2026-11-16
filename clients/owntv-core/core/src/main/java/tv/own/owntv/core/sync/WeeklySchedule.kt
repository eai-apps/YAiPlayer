package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.TimeZone

/**
 * When playlists, portals and guides are refreshed on their own (owner, 16.11 — replaces 12.11):
 *
 * - **Guides** (the portal's own guide, a playlist's url-tvg, the reserve): once, Monday 18:00. The operator
 *   loads next week's guide on Monday; one load a week is enough and spares the box and the portal. On Monday
 *   and Tuesday mornings, while this week's guide is not in yet, the app OFFERS it ([isGuideOfferTime]).
 * - **Catalogues** (channels, films, series): quietly every morning from 06:00, so channels the provider
 *   added are found the same day («Новые каналы», [NewChannels]).
 *
 * Device time. Nothing refreshes just because the app was opened; a box that was off at a slot catches up
 * once, at the first launch after it. Manual refresh is unaffected.
 */
object WeeklySchedule {
    private val GUIDE_SLOTS = listOf(0 to 18) // (days after Monday, hour)
    private val CATALOG_SLOTS = (0..6).map { it to 6 }

    /** A catalogue (playlist or portal) refresh is due now. */
    fun isCatalogDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        dueAt(CATALOG_SLOTS, lastSyncAt, nowMs, zone)

    /** A guide refresh is due now. */
    fun isGuideDue(lastSyncAt: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        dueAt(GUIDE_SLOTS, lastSyncAt, nowMs, zone)

    /**
     * Monday or Tuesday from 06:00, and this week's guide not loaded yet ([lastGuideSync] before Monday 00:00):
     * the «Загрузить новый телегид?» offer may show.
     */
    fun isGuideOfferTime(lastGuideSync: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        if (day > 1 || now.get(Calendar.HOUR_OF_DAY) < 6) return false
        val monday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -day)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (lastGuideSync ?: 0L) < monday
    }

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
