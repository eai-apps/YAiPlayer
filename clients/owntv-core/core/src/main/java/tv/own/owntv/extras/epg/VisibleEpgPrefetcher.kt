// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.epg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.parser.XtEpgEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Prefetches short-EPG programme data strictly for the visible channel rows (+ 3 above and below).
 *
 * Rules:
 * - Max 3 concurrent requests (bounded by [Semaphore]).
 * - In-flight jobs are cancelled if the row scrolls out of the visible buffer.
 * - Channels that yielded empty/no programme from provider are backoff-cached for 10 min.
 */
class VisibleEpgPrefetcher(
    private val maxConcurrency: Int = 3,
) {
    private val semaphore = Semaphore(maxConcurrency)
    private val inFlight = ConcurrentHashMap<Long, Job>()

    /**
     * Updates the visible window of channels and schedules prefetching for those missing programmes.
     */
    fun onVisibleRangeChanged(
        scope: CoroutineScope,
        channels: List<ChannelEntity>,
        hasProgramme: (Long) -> Boolean,
        epgReader: LiveEpgReader,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
        onResolved: (Long, XtEpgEntry) -> Unit,
    ) {
        val targetIds = channels.map { it.id }.toSet()

        // 1. Cancel jobs that are no longer within the visible window (+ buffer)
        val iterator = inFlight.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in targetIds) {
                entry.value.cancel()
                iterator.remove()
            }
        }

        // 2. Schedule fetch for channels without a programme
        for (channel in channels) {
            val channelId = channel.id
            if (hasProgramme(channelId)) continue
            if (epgReader.isKnownEmptyProvider(channelId)) continue
            if (inFlight.containsKey(channelId)) continue

            val job = scope.launch {
                try {
                    semaphore.withPermit {
                        val entry = epgReader.prefetchVisibleProgramme(channel, cust, globalShiftMinutes)
                        if (entry != null) {
                            onResolved(channelId, entry)
                        }
                    }
                } finally {
                    inFlight.remove(channelId)
                }
            }
            inFlight[channelId] = job
        }
    }

    /**
     * Cancel all pending prefetch jobs.
     */
    fun cancelAll() {
        for ((_, job) in inFlight) {
            job.cancel()
        }
        inFlight.clear()
    }

    companion object {
        /**
         * Computes the buffered index window around visible items (visible +/- [buffer]).
         */
        fun visibleWindow(
            visibleIndices: List<Int>,
            totalCount: Int,
            buffer: Int = 3,
        ): IntRange {
            if (visibleIndices.isEmpty() || totalCount <= 0) return IntRange.EMPTY
            val minIdx = (visibleIndices.minOrNull() ?: 0) - buffer
            val maxIdx = (visibleIndices.maxOrNull() ?: 0) + buffer
            return minIdx.coerceAtLeast(0)..maxIdx.coerceAtMost(totalCount - 1)
        }
    }
}
