// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.sync.AutoRefresh
import tv.own.owntv.core.sync.RefreshJob
import tv.own.owntv.core.sync.RefreshQueueTracker
import tv.own.owntv.core.sync.SyncContentTypes

/**
 * Runs the automatic refreshes one after another (see [tv.own.owntv.core.sync.RefreshQueuePlanner]). Each run
 * handles ONE job: it starts the source's own sync worker (same unique name as a manual sync, KEEP, so nothing
 * runs twice), waits for it to finish, then appends a run for the next job. One job per run keeps every run far
 * below WorkManager's ten-minute limit.
 */
class RefreshQueueWorker(
    context: Context,
    params: WorkerParameters,
    private val sourceDao: SourceDao,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val tracker: RefreshQueueTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobs = RefreshQueueScheduler.decode(inputData.getIntArray(KEY_KINDS), inputData.getLongArray(KEY_IDS), inputData.getIntArray(KEY_BASES))
        val index = inputData.getInt(KEY_INDEX, 0)
        val job = jobs.getOrNull(index) ?: run {
            tracker.finished()
            return Result.success()
        }
        tracker.update(index + 1, jobs.size)
        val name = when (job) {
            is RefreshJob.Catalog -> {
                val source = sourceDao.getById(job.id)
                if (source != null) {
                    catalogSyncScheduler.enqueueSync(
                        job.id,
                        reason = AutoRefresh.REASON,
                        contentTypes = SyncContentTypes.enabledOf(source),
                        baseItemCount = job.baseItemCount,
                        policy = ExistingWorkPolicy.KEEP,
                        // The queue runs the playlist's guides itself, right after.
                        includeGuide = false,
                    )
                    CatalogSyncScheduler.workName(job.id)
                } else null
            }
            is RefreshJob.Guide -> {
                epgSyncScheduler.enqueueSync(job.id, AutoRefresh.REASON, job.baseProgrammes, ExistingWorkPolicy.KEEP, inQueue = true)
                EpgSyncScheduler.workName(job.id)
            }
        }
        if (name != null) {
            tracker.currentWork = name
            runCatching {
                WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWorkFlow(name)
                    .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
            }.onFailure { Log.w(TAG, "waiting for $name failed", it) }
        }
        // Paused (the phone app left the screen): the wait above was cancelled, so no next step.
        if (isStopped) return Result.success()
        if (index + 1 >= jobs.size) {
            tracker.finished()
        } else {
            RefreshQueueScheduler.enqueueStep(applicationContext, inputData.keyValueMap, index + 1)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RefreshQueue"
        const val KEY_KINDS = "kinds"
        const val KEY_IDS = "ids"
        const val KEY_BASES = "bases"
        const val KEY_INDEX = "index"
    }
}

object RefreshQueueScheduler {
    const val UNIQUE_NAME = "auto-refresh-queue"

    /** Starts the queue unless one is already running (that one finishes first; the next check catches up). */
    fun start(context: Context, jobs: List<RefreshJob>) {
        if (jobs.isEmpty()) return
        val data = mapOf<String, Any?>(
            RefreshQueueWorker.KEY_KINDS to jobs.map { if (it is RefreshJob.Catalog) 0 else 1 }.toIntArray(),
            RefreshQueueWorker.KEY_IDS to jobs.map { it.id }.toLongArray(),
            RefreshQueueWorker.KEY_BASES to jobs.map { if (it is RefreshJob.Catalog) it.baseItemCount else (it as RefreshJob.Guide).baseProgrammes }.toIntArray(),
        )
        enqueue(context, data, 0, ExistingWorkPolicy.KEEP)
    }

    internal fun enqueueStep(context: Context, data: Map<String, Any?>, index: Int) =
        enqueue(context, data, index, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(context: Context, data: Map<String, Any?>, index: Int, policy: ExistingWorkPolicy) {
        val input = workDataOf(*(data + (RefreshQueueWorker.KEY_INDEX to index)).map { it.key to it.value }.toTypedArray())
        val request = OneTimeWorkRequestBuilder<RefreshQueueWorker>().setInputData(input).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    /** Stops the queue and the job it is on (the app left the screen on a phone); [AutoRefresh] restarts it. */
    fun pause(context: Context, tracker: RefreshQueueTracker): Boolean {
        if (tracker.progress.value == null) return false
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_NAME)
        tracker.currentWork?.let { wm.cancelUniqueWork(it) }
        tracker.finished()
        return true
    }

    fun decode(kinds: IntArray?, ids: LongArray?, bases: IntArray?): List<RefreshJob> {
        if (kinds == null || ids == null || bases == null || kinds.size != ids.size || ids.size != bases.size) return emptyList()
        return kinds.indices.map { i -> if (kinds[i] == 0) RefreshJob.Catalog(ids[i], bases[i]) else RefreshJob.Guide(ids[i], bases[i]) }
    }
}
