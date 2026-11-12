// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.core.sync.work.RefreshQueueScheduler

/**
 * What the refresh menus need to say "an update is already running, step N of M" and to stop it (owner, 12.11:
 * pressing «Обновить» showed nothing, and a 10-minute guide refresh could not be stopped). Shared by TV and phone.
 */
class RefreshControl(
    private val context: Context,
    private val queue: RefreshQueueTracker,
    private val catalog: SyncActivityTracker,
    private val epg: EpgActivityTracker,
    private val catalogScheduler: CatalogSyncScheduler,
    private val epgScheduler: EpgSyncScheduler,
) {
    /** A running update: [step] of [total] (1 of 1 outside the queue) and the name of the source on it now. */
    data class Running(val step: Int, val total: Int, val name: String, val guide: Boolean)

    val running: Flow<Running?> = combine(queue.progress, catalog.active, epg.active) { q, c, e ->
        describe(q, c.values.map { it.sourceName }, e.values.map { it.sourceName })
    }

    /** Stops the queue, every catalog sync and every guide sync now running. Rows already written stay. */
    fun cancelAll() {
        RefreshQueueScheduler.pause(context, queue)
        catalog.active.value.keys.forEach(catalogScheduler::cancelSync)
        epg.active.value.keys.forEach(epgScheduler::cancelSync)
    }

    companion object {
        /** Catalog first (it is what the guide waits for); null when nothing runs. */
        fun describe(queue: RefreshQueueTracker.Progress?, catalogNames: List<String>, guideNames: List<String>): Running? {
            val name = catalogNames.firstOrNull() ?: guideNames.firstOrNull()
            if (queue == null && name == null) return null
            val guide = catalogNames.isEmpty() && guideNames.isNotEmpty()
            return if (queue != null) Running(queue.current, queue.total, name.orEmpty(), guide)
            else Running(1, catalogNames.size + guideNames.size, name.orEmpty(), guide)
        }
    }
}
