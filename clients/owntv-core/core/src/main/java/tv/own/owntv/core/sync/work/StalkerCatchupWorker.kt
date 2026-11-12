// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.epg.EpgGapFill
import tv.own.owntv.core.epg.EpgSourceStore

/** Local device time; WorkManager may defer a slot for network/battery/Doze. */
internal fun nextStalkerCatchup(now: ZonedDateTime): ZonedDateTime =
    (0L..7L).flatMap { days ->
        val date = now.toLocalDate().plusDays(days)
        val hours = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> listOf(9, 15) // owner 12.11: 09:00 and 15:00 only
            DayOfWeek.TUESDAY -> listOf(9)
            else -> emptyList()
        }
        hours.map { date.atTime(it, 0).atZone(now.zone) }
    }.first { it.isAfter(now) }

object StalkerCatchupScheduler {
    private const val TAG = "stalker-epg-catchup"
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWorkByTag(TAG) }
    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        val next = nextStalkerCatchup(now)
        val request = OneTimeWorkRequestBuilder<StalkerCatchupWorker>()
            .setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                // A phone: only on the charger (a background guide download drained one, owner 11.11).
                .setRequiresCharging(tv.own.owntv.core.CoreBuildInfo.batteryPowered).build())
            .addTag(TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG-${next.toEpochSecond()}", ExistingWorkPolicy.KEEP, request)
    }
}

class StalkerCatchupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val store = koin.get<EpgSourceStore>()
        if (!store.stalkerCatchupEnabled.first() || !store.builtinEnabled.first()) return Result.success()
        // Schedule next before downloading; a process death cannot break the weekly chain.
        StalkerCatchupScheduler.schedule(applicationContext)
        return try {
            EpgGapFill(koin.get(), store, koin.get(), koin.get()).run(stalkerOnly = true)
            Result.success()
        } catch (c: CancellationException) { throw c }
        catch (_: Exception) { Result.failure() }
    }
}
