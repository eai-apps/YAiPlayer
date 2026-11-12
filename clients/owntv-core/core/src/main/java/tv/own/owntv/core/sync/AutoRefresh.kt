package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The playlist and guide "Auto refresh" settings, for both apps. It used to live in the TV app's
 * shell, so the phone stored the setting and never acted on it.
 *
 * - `includeStartup = true` (cold start): runs once per process, refreshes STARTUP sources
 *   unconditionally and interval sources whose data is at least as old as their threshold. Never
 *   time-throttled, so STARTUP always fires on a real cold start.
 * - `includeStartup = false` (app back in the foreground): skips STARTUP sources and refreshes
 *   interval sources past their threshold. Throttled to once per [RESUME_THROTTLE_MS].
 *
 * Enqueues use [ExistingWorkPolicy.KEEP], so a source already syncing or queued is left alone.
 * Manual re-syncs use REPLACE at their own call sites. A job enqueued here carries
 * [REASON], and the workers hold such a job back while something is playing ([tv.own.owntv.core.live.WatchSession]).
 *
 * A singleton, so the once-per-process rule holds even when the Activity is rebuilt.
 */
class AutoRefresh(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val profileDao: ProfileDao,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val epgSourceStore: EpgSourceStore,
    private val epgDao: EpgDao,
    private val importFinalizer: ImportFinalizer,
    private val vodResort: M3uVodResort,
    /** Starts [tv.own.owntv.core.sync.work.RefreshQueueWorker] with these jobs. */
    private val startQueue: (List<RefreshJob>) -> Unit,
    private val sourceDaoAll: suspend () -> List<tv.own.owntv.core.database.entity.SourceEntity>,
) {
    private var coldStartCheckDone = false
    /** The queue was stopped because the app left the screen (phone); the next return finishes it. */
    @Volatile private var pausedForBackground = false
    private var lastResumeCheckAtElapsed = 0L

    suspend fun check(includeStartup: Boolean) {
        epgSourceStore.updateCatchupSchedule()
        synchronized(this) {
            if (includeStartup) {
                if (coldStartCheckDone) return
                coldStartCheckDone = true
            } else if (!pausedForBackground) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastResumeCheckAtElapsed < RESUME_THROTTLE_MS) return
                lastResumeCheckAtElapsed = now
            }
        }
        val resuming = pausedForBackground && !includeStartup
        pausedForBackground = false
        if (includeStartup && !settings.autoRefreshOnStartup.first()) return
        val nowMs = System.currentTimeMillis()
        val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return
        // The startup catch-up is part of background resync: switched off there, off here too.
        // Back after a pause: the same catch-up again; what finished before the pause counts as done today.
        val staleCatchUp = (includeStartup || resuming) && settings.backgroundResyncEnabled.first()
        // Everything due goes into ONE queue, run one after another (RefreshQueuePlanner): first this profile's
        // playlists, then their guides, then unclaimed feeds, then the built-in guide. Other profiles wait until
        // they are opened or until the background refresh.
        val playlistModes = settings.playlistAutoRefresh.first()
        val epgRepository = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.repository.EpgRepository>()
        val active = sourceRepository.observeSources(pid).first()
        // A profile with no playlist or portal has nothing to refresh — and must not pull the built-in guide (owner, 12.11).
        if (active.isEmpty()) return
        val activeIds = active.map { it.id }.toSet()
        val others = runCatching { sourceDaoAll() }.getOrDefault(emptyList()).filter { it.id !in activeIds }
        val playlists = active.map { source ->
            val mode = playlistModes[source.id] ?: PlaylistRefresh.OFF
            val due = shouldRefresh(mode, source.lastSyncAt, nowMs, includeStartup) ||
                (staleCatchUp && WeeklySchedule.isDue(source.lastSyncAt, nowMs))
            val counts = if (due) importFinalizer.contentCounts(source.id) else null
            PlaylistInfo(source.id, true, due, counts?.let { it.channels + it.movies + it.series } ?: 0, epgRepository.guideUrls(source))
        } + others.map { PlaylistInfo(it.id, false, false, 0, epgRepository.guideUrls(it)) }
        val epgModes = settings.epgAutoRefresh.first()
        val all = epgSourceStore.getAll()
        // The built-in guide is a chain: its first feed asks for the next, so only the first is due here.
        val firstBuiltinId = all.firstOrNull { it.builtin }?.id
        val feeds = all.filter { !it.builtin || it.id == firstBuiltinId }.map { src ->
            val mode = epgModes[src.id] ?: EpgRefresh.OFF
            val due = shouldRefreshEpg(mode, src.lastSyncAt, nowMs, includeStartup) ||
                (staleCatchUp && WeeklySchedule.isDue(src.lastSyncAt, nowMs))
            GuideFeedInfo(src.id, src.url, src.builtin, due, if (due) epgDao.countForSources(listOf(src.id)) else 0)
        }
        val jobs = RefreshQueuePlanner.plan(playlists, feeds)
        if (jobs.isNotEmpty()) {
            Log.d(TAG, "checkAutoRefresh queue=${jobs.size}")
            startQueue(jobs)
        }
        if (includeStartup) {
            refillGuideEmptiedByMigration()
            vodResort.runOnce()
        }
    }

    /**
     * The app left the screen. On a phone ([tv.own.owntv.core.CoreBuildInfo.batteryPowered]) the automatic
     * queue stops here — downloading guides with the screen off drained a phone to empty (owner, 11.11) —
     * and [check] on the next return starts it again for what is still due. Manual refreshes are not touched.
     */
    fun onAppBackground(context: android.content.Context) {
        if (!tv.own.owntv.core.CoreBuildInfo.batteryPowered) return
        val tracker = org.koin.core.context.GlobalContext.get().get<RefreshQueueTracker>()
        if (tv.own.owntv.core.sync.work.RefreshQueueScheduler.pause(context, tracker)) {
            pausedForBackground = true
            Log.i(TAG, "refresh queue paused: app in background")
        }
    }

    /**
     * Refill a guide that `MIGRATION_8_9` emptied.
     *
     * That migration deletes every `epg_programmes` row and nothing schedules a re-fetch, so an
     * upgrading user's Guide is simply blank until they think to re-sync EPG by hand. Runs **once per
     * install** (so it also catches users who passed through 8→9 in an earlier version) and only for
     * sources that had previously synced successfully but now hold zero programmes — that is the
     * exact signature of the wipe.
     *
     * EPG is opt-in by design, and this respects that: adding an EPG source *is* the opt-in, and a
     * source the user has never synced is left alone rather than silently downloaded. The enqueue is
     * an ordinary [EpgSyncScheduler] job, so it shows the standard EPG-syncing pill.
     *
     * The one-shot flag is read first and the DB is touched only when it is unset, so this adds no
     * work to a normal cold start.
     */
    private suspend fun refillGuideEmptiedByMigration() {
        if (settings.epgRefillChecked.first()) return
        runCatching {
            val sources = epgSourceStore.getAll().filter { (it.lastSyncAt ?: 0L) > 0L }
            for (src in sources) {
                if (epgDao.countForSources(listOf(src.id)) > 0) continue
                Log.i(TAG, "epgRefill sourceId=${src.id} — synced before but guide is empty, re-fetching")
                epgSyncScheduler.enqueueSync(
                    src.id,
                    reason = "migration_refill",
                    baseProgrammes = 0,
                    policy = ExistingWorkPolicy.KEEP,
                )
            }
        }.onFailure { Log.w(TAG, "epgRefill check failed", it) }
        // Marked regardless: a failed check must not retry on every launch forever, and a failed
        // *sync* is already retried by the scheduler's own policy.
        settings.markEpgRefillChecked()
    }

    /**
     * Whether a playlist source should auto-refresh now. OFF never; STARTUP only on cold start
     * ([includeStartup]); interval modes when `now - lastSyncAt >= threshold` (a null lastSyncAt — never
     * successfully synced — counts as infinitely stale so recovery happens).
     */
    private fun shouldRefresh(
        refresh: PlaylistRefresh,
        lastSyncAt: Long?,
        now: Long,
        includeStartup: Boolean,
    ): Boolean = when (refresh.mode) {
        PlaylistAutoRefresh.OFF -> false
        PlaylistAutoRefresh.STARTUP -> includeStartup
        else -> (now - (lastSyncAt ?: 0L)) >= (refresh.thresholdMs ?: Long.MAX_VALUE)
    }

    /** EPG equivalent of [shouldRefresh]. */
    private fun shouldRefreshEpg(
        refresh: EpgRefresh,
        lastSyncAt: Long?,
        now: Long,
        includeStartup: Boolean,
    ): Boolean = when (refresh.mode) {
        EpgAutoRefresh.OFF -> false
        EpgAutoRefresh.STARTUP -> includeStartup
        // MANUAL's threshold is its day count; every other mode carries its own. Identical shape to
        // [shouldRefresh], which is the point of the parity.
        else -> (now - (lastSyncAt ?: 0L)) >= (refresh.thresholdMs ?: Long.MAX_VALUE)
    }

    companion object {
        private const val TAG = "OwnTVHome"
        /** The work reason the sync workers recognise as automatic, and hold back during playback. */
        const val REASON = "auto_refresh"
        /** Minimum gap between resume-triggered checks (rotation, rapid background/foreground). */
        private const val RESUME_THROTTLE_MS = 60_000L
    }
}
