// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.core.sync.work

import tv.own.owntv.core.database.dao.resolveExistingProfileId

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
        val nowMs = System.currentTimeMillis()

        // One queue (RefreshQueuePlanner), one source at a time, and only the profile in use: other profiles
        // are refreshed when they are opened (owner, 11.11 — every profile at once drained a phone).
        val epgRepository = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.repository.EpgRepository>()
        val activeId = profileDao.resolveExistingProfileId(settings.activeProfileId.first())
        val profiles = profileDao.getAllOnce().sortedBy { if (it.id == activeId) 0 else 1 }
        val seenSourceIds = mutableSetOf<Long>()
        val playlists = ArrayList<tv.own.owntv.core.sync.PlaylistInfo>()
        for (profile in profiles) {
            for (source in sourceRepository.observeSources(profile.id).first()) {
                if (!seenSourceIds.add(source.id)) continue
                val due = tv.own.owntv.core.sync.WeeklySchedule.isDue(source.lastSyncAt, nowMs)
                val counts = if (due) importFinalizer.contentCounts(source.id) else null
                playlists += tv.own.owntv.core.sync.PlaylistInfo(
                    source.id, profile.id == activeId, due,
                    counts?.let { it.channels + it.movies + it.series } ?: 0, epgRepository.guideUrls(source),
                )
            }
        }
        val allEpg = epgSourceStore.getAll()
        val firstBuiltinId = allEpg.firstOrNull { it.builtin }?.id
        val feeds = allEpg.filter { !it.builtin || it.id == firstBuiltinId }.map { src ->
            val due = tv.own.owntv.core.sync.WeeklySchedule.isDue(src.lastSyncAt, nowMs)
            tv.own.owntv.core.sync.GuideFeedInfo(src.id, src.url, src.builtin, due, if (due) epgDao.countForSources(listOf(src.id)) else 0)
        }
        val jobs = tv.own.owntv.core.sync.RefreshQueuePlanner.plan(playlists, feeds, includeOtherProfiles = false)
        Log.i(TAG, "stale resync queue=${jobs.size}")
        RefreshQueueScheduler.start(applicationContext, jobs)

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
            // A phone: only while it charges, so a forgotten app never empties the battery.
            .setRequiresCharging(tv.own.owntv.core.CoreBuildInfo.batteryPowered)
            .build()
        val request = PeriodicWorkRequestBuilder<StaleResyncWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            // UPDATE: an install that scheduled this without the charging rule gets it now (the period is kept).
            ExistingPeriodicWorkPolicy.UPDATE,
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
