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
    suspend fun refreshPlaylist(source: SourceEntity) {
        val counts = importFinalizer.contentCounts(source.id)
        catalogSyncScheduler.enqueueSync(
            source.id,
            reason = "manual_resync",
            contentTypes = SyncContentTypes.enabledOf(source),
            baseItemCount = counts.channels + counts.movies + counts.series,
            policy = ExistingWorkPolicy.KEEP,
        )
    }

    suspend fun refreshVod(source: SourceEntity) {
        val counts = importFinalizer.contentCounts(source.id)
        catalogSyncScheduler.enqueueSync(
            source.id,
            reason = "manual_resync",
            contentTypes = SyncContentTypes(live = false, movies = source.syncMovies, series = source.syncSeries),
            baseItemCount = counts.movies + counts.series,
            policy = ExistingWorkPolicy.KEEP,
        )
    }

    suspend fun refreshEpg(source: SourceEntity) {
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
