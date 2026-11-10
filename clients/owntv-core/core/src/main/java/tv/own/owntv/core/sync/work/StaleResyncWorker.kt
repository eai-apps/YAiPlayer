// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.core.sync.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.AutoRefresh
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.isStale

/**
 * Periodic WorkManager task that checks all profiles and EPG sources for staleness.
 *
 * Runs every 6 hours under connected network and non-low battery constraints.
 * If data is older than [SettingsRepository.backgroundResyncHours], sync is enqueued
 * using existing schedulers with [AutoRefresh.REASON] and [ExistingWorkPolicy.KEEP].
 */
class StaleResyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val sourceRepository: SourceRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val importFinalizer: ImportFinalizer,
    private val epgSourceStore: EpgSourceStore,
    private val epgDao: EpgDao,
    private val epgSyncScheduler: EpgSyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settings.backgroundResyncEnabled.first()) {
            Log.d(TAG, "Background resync is disabled, skipping")
            return Result.success()
        }
        val thresholdHours = settings.backgroundResyncHours.first()
        val nowMs = System.currentTimeMillis()

        // 1. Playlist / Portal sources across ALL profiles
        val profiles = profileDao.getAllOnce()
        val seenSourceIds = mutableSetOf<Long>()
        for (profile in profiles) {
            val sources = sourceRepository.observeSources(profile.id).first()
            for (source in sources) {
                if (!seenSourceIds.add(source.id)) continue
                if (isStale(source.lastSyncAt, nowMs, thresholdHours)) {
                    val counts = importFinalizer.contentCounts(source.id)
                    Log.i(TAG, "stale playlist sourceId=${source.id} — enqueuing")
                    catalogSyncScheduler.enqueueSync(
                        source.id,
                        reason = AutoRefresh.REASON,
                        contentTypes = SyncContentTypes.enabledOf(source),
                        baseItemCount = counts.channels + counts.movies + counts.series,
                        policy = ExistingWorkPolicy.KEEP,
                    )
                }
            }
        }

        // 2. EPG sources (first builtin in chain + external)
        val allEpg = epgSourceStore.getAll()
        val firstBuiltinId = allEpg.firstOrNull { it.builtin }?.id
        allEpg.forEach { src ->
            if (src.builtin && src.id != firstBuiltinId) return@forEach
            if (isStale(src.lastSyncAt, nowMs, thresholdHours)) {
                val base = epgDao.countForSources(listOf(src.id))
                Log.i(TAG, "stale epg sourceId=${src.id} — enqueuing")
                epgSyncScheduler.enqueueSync(
                    src.id,
                    reason = AutoRefresh.REASON,
                    baseProgrammes = base,
                    policy = ExistingWorkPolicy.KEEP,
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "StaleResync"
    }
}

object StaleResyncScheduler {
    const val UNIQUE_WORK_NAME = "stale_resync_periodic"
    private const val INTERVAL_HOURS = 6L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<StaleResyncWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    suspend fun scheduleIfEnabled(context: Context, settings: SettingsRepository) {
        if (settings.backgroundResyncEnabled.first()) {
            schedule(context)
        }
    }
}
