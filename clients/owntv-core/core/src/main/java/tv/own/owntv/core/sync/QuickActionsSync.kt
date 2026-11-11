// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.launch
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
    /*
     * Fire-and-forget on the app's own scope. The callers are dialogs that close in the same click
     * (TV side rail «Синхронизация», phone playlist sheet): a sync launched in the dialog's
     * rememberCoroutineScope was cancelled the moment the dialog left the composition, before the
     * first DB read — the owner's «choose any item, nothing happens» (П1, 10.11.2026).
     */
    fun refreshPlaylist(source: SourceEntity) = launch(source, QuickSyncAction.PLAYLIST)
    fun refreshChannelsOnly(source: SourceEntity) = launch(source, QuickSyncAction.CHANNELS)
    fun refreshChannelsAndEpg(source: SourceEntity) = launch(source, QuickSyncAction.CHANNELS_AND_EPG)
    fun refreshAll(source: SourceEntity) = launch(source, QuickSyncAction.ALL)
    fun refreshVod(source: SourceEntity) = launch(source, QuickSyncAction.VOD)
    fun refreshEpg(source: SourceEntity) = launch(source, QuickSyncAction.EPG)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun launch(source: SourceEntity, action: QuickSyncAction) {
        scope.launch {
            try {
                refresh(source, action)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                android.util.Log.w(TAG, "quick sync failed sourceId=${source.id} action=$action", e)
            }
        }
    }

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

    private companion object {
        const val TAG = "QuickActionsSync"
    }
}
