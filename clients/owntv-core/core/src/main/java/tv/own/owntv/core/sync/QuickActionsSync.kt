// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import androidx.work.ExistingWorkPolicy
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler

class QuickActionsSync(
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val epgSourceStore: EpgSourceStore,
    private val epgDao: EpgDao,
    private val epgRepository: EpgRepository,
    private val importFinalizer: ImportFinalizer,
) {
    suspend fun refreshPlaylist(source: SourceEntity) = refresh(source, QuickSyncAction.PLAYLIST)
    suspend fun refreshChannelsOnly(source: SourceEntity) = refresh(source, QuickSyncAction.CHANNELS)
    suspend fun refreshChannelsAndEpg(source: SourceEntity) = refresh(source, QuickSyncAction.CHANNELS_AND_EPG)
    suspend fun refreshAll(source: SourceEntity) = refresh(source, QuickSyncAction.ALL)
    suspend fun refreshVod(source: SourceEntity) = refresh(source, QuickSyncAction.VOD)
    suspend fun refreshEpg(source: SourceEntity) = refresh(source, QuickSyncAction.EPG)

    private suspend fun refresh(source: SourceEntity, action: QuickSyncAction) {
        dispatchSelectiveSync(selectiveSyncPlan(action, source), catalog = { contentTypes ->
            val counts = importFinalizer.contentCounts(source.id)
            val base = (if (contentTypes.live) counts.channels else 0) +
                (if (contentTypes.movies) counts.movies else 0) + (if (contentTypes.series) counts.series else 0)
            catalogSyncScheduler.enqueueSync(source.id, reason = "manual_resync", contentTypes = contentTypes,
                baseItemCount = base, policy = ExistingWorkPolicy.APPEND_OR_REPLACE, includeGuide = false)
        }, epg = { refreshEpgOnly(source) })
    }

    private suspend fun refreshEpgOnly(source: SourceEntity) {
        val allEpg = epgSourceStore.getAll()
        val urls = epgRepository.guideUrls(source).toSet()
        val stalkerUrl = EpgRepository.stalkerGuideUrl(source.id)
        val matched = allEpg.filter { it.url in urls || (source.type == SourceType.STALKER && it.url == stalkerUrl) }
        if (matched.isNotEmpty()) {
            matched.forEach { src ->
                val base = epgDao.countForSources(listOf(src.id))
                epgSyncScheduler.enqueueSync(src.id, "manual_resync", base, ExistingWorkPolicy.KEEP)
            }
        } else {
            // No guide of its own: only the built-in one. The EPG sources are global (every profile's),
            // and a shortcut on one playlist must not re-download the others'.
            allEpg.firstOrNull { it.builtin }?.let {
                epgSyncScheduler.enqueueSync(it.id, "manual_builtin", policy = ExistingWorkPolicy.KEEP)
            }
        }
    }
}
