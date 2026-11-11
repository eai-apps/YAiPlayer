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
        val plan = selectiveSyncPlan(action, source)
        dispatchSelectiveSync(plan, catalog = { contentTypes ->
            val counts = importFinalizer.contentCounts(source.id)
            val base = (if (contentTypes.live) counts.channels else 0) +
                (if (contentTypes.movies) counts.movies else 0) + (if (contentTypes.series) counts.series else 0)
            catalogSyncScheduler.enqueueSync(source.id, reason = "manual_resync", contentTypes = contentTypes,
                baseItemCount = base, policy = ExistingWorkPolicy.APPEND_OR_REPLACE, includeGuide = false,
                afterEpgSourceIds = if (plan.epg) selectedGuideSources(source).map { it.id } else emptyList())
        }, epg = { if (plan.catalog?.hasAny != true) refreshEpgOnly(source) })
    }

    private suspend fun refreshEpgOnly(source: SourceEntity) {
        selectedGuideSources(source).forEach { src ->
            val base = epgDao.countForSources(listOf(src.id))
            epgSyncScheduler.enqueueSync(src.id, if (src.builtin) "manual_builtin" else "manual_resync", base, ExistingWorkPolicy.KEEP)
        }
    }

    private suspend fun selectedGuideSources(source: SourceEntity): List<tv.own.owntv.core.epg.EpgSource> {
        val allEpg = epgSourceStore.getAll()
        val urls = epgRepository.guideUrls(source).toSet()
        val stalkerUrl = EpgRepository.stalkerGuideUrl(source.id)
        val matched = allEpg.filter { it.url in urls || (source.type == SourceType.STALKER && it.url == stalkerUrl) }
        return matched.ifEmpty { allEpg.firstOrNull { it.builtin }?.let { listOf(it) }.orEmpty() }
    }
}
