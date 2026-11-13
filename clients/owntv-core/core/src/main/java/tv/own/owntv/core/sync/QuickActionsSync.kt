// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
        // Owner 12.11: pressing «Обновить» showed nothing. Say at once that it started (TV and phone, every menu).
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            runCatching {
                val context = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
                android.widget.Toast.makeText(context, tv.own.owntv.core.R.string.sync_started, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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

    /**
     * Owner 13.11: entering a profile (or an account) loads the guide the playlist itself names, when it has none
     * yet or it is older than [GUIDE_STALE_MS]. Only the playlist's own guide: the additional sources stay manual.
     * Asked once per guide while a profile stays active; a source that arrives later (an account's playlist after
     * its first load) is picked up when the profile's list changes.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    fun startGuideOnEnter(
        appScope: kotlinx.coroutines.CoroutineScope,
        activeProfileId: kotlinx.coroutines.flow.Flow<Long>,
        sourcesFor: (Long) -> kotlinx.coroutines.flow.Flow<List<SourceEntity>>,
    ) {
        if (guideOnEnterStarted) return
        guideOnEnterStarted = true
        val asked = HashSet<Long>()
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            activeProfileId.distinctUntilChanged()
                .flatMapLatest { id ->
                    asked.clear()
                    if (id < 0) kotlinx.coroutines.flow.flowOf(emptyList()) else sourcesFor(id)
                }
                .debounce(GUIDE_ON_ENTER_DELAY_MS)
                .collect { sources ->
                    runCatching {
                        val now = System.currentTimeMillis()
                        // A playlist not loaded yet runs its guide with its first load; it is looked at again once loaded.
                        sources.filter { it.lastSyncAt != null }.forEach { source ->
                            val own = selectedGuideSources(source).filter { !it.builtin }
                            own.forEach { src ->
                                val fresh = src.lastSyncAt?.let { now - it < GUIDE_STALE_MS } == true
                                if (!fresh && asked.add(src.id)) {
                                    val base = epgDao.countForSources(listOf(src.id))
                                    epgSyncScheduler.enqueueSync(src.id, "profile_enter", base, ExistingWorkPolicy.KEEP)
                                }
                            }
                        }
                    }.onFailure { android.util.Log.w(TAG, "guide on enter failed", it) }
                }
        }
    }

    @Volatile private var guideOnEnterStarted = false

    private suspend fun selectedGuideSources(source: SourceEntity): List<tv.own.owntv.core.epg.EpgSource> {
        val allEpg = epgSourceStore.getAll()
        val urls = epgRepository.guideUrls(source).toSet()
        val stalkerUrl = EpgRepository.stalkerGuideUrl(source.id)
        val matched = allEpg.filter { it.url in urls || (source.type == SourceType.STALKER && it.url == stalkerUrl) }
        return matched.ifEmpty { allEpg.firstOrNull { it.builtin }?.let { listOf(it) }.orEmpty() }
    }

    private companion object {
        const val TAG = "QuickActionsSync"
        const val GUIDE_STALE_MS = 12 * 60 * 60 * 1000L
        /** After the profile settles (and after a new account playlist's own first load has begun). */
        const val GUIDE_ON_ENTER_DELAY_MS = 8_000L
    }
}
