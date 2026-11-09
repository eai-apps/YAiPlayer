package tv.own.owntv.core.home

import androidx.compose.runtime.Immutable
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.matchName
import tv.own.owntv.core.live.ChannelVersionRules
import tv.own.owntv.core.sports.ChannelFacets

/**
 * A programme currently on air on a visible live channel.
 */
@Immutable
data class OnAirItem(
    val channel: ChannelEntity,
    val programme: EpgProgrammeEntity,
    val genres: Set<OnAirGenre>,
    val progress: Float,
    val canWatchFromStart: Boolean,
    val startOffsetSec: Int,
) {
    companion object {
        /** Minimum elapsed time (2 minutes) required to offer "Watch from start". */
        const val MIN_FROM_START_MS = 120_000L

        /**
         * Calculates fractional progress [0f, 1f] of a programme running from [startMs] to [stopMs] at [nowMs].
         */
        fun calculateProgress(startMs: Long, stopMs: Long, nowMs: Long): Float {
            val total = (stopMs - startMs).coerceAtLeast(1L)
            val elapsed = (nowMs - startMs).coerceAtLeast(0L)
            return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

        /**
         * Checks whether "Watch from start" is eligible for [channel] and [programme] at [nowMs].
         * Requires catchup support, running for at least 2 minutes, and not yet finished.
         */
        fun canWatchFromStart(hasCatchup: Boolean, startMs: Long, stopMs: Long, nowMs: Long): Boolean {
            if (!hasCatchup) return false
            if (nowMs - startMs < MIN_FROM_START_MS) return false
            if (nowMs >= stopMs) return false
            return true
        }

        /**
         * Seconds behind live to the start of [programme] at [nowMs].
         */
        fun startOffsetSec(startMs: Long, nowMs: Long): Int {
            return ((nowMs - startMs).coerceAtLeast(0L) / 1000L).toInt()
        }

        /**
         * Collapses duplicate channels broadcasting on air (e.g. SD/HD/4K versions),
         * keeping at most one card per channel (best quality version).
         */
        fun collapseDuplicates(
            items: List<OnAirItem>,
            sourceOrder: List<Long> = emptyList(),
        ): List<OnAirItem> {
            if (items.size <= 1) return items

            val groups = ArrayList<MutableList<OnAirItem>>()

            for (item in items) {
                val itemFacets = ChannelFacets.of(item.channel.matchName)
                val itemEpgKey = item.programme.epgChannelId

                var matchedGroup: MutableList<OnAirItem>? = null
                for (group in groups) {
                    val rep = group[0]
                    val repFacets = ChannelFacets.of(rep.channel.matchName)
                    val repEpgKey = rep.programme.epgChannelId

                    val isSameChannel = rep.channel.id == item.channel.id ||
                        (repEpgKey.isNotEmpty() && repEpgKey == itemEpgKey && ChannelVersionRules.same(repFacets, repEpgKey, itemFacets, itemEpgKey)) ||
                        ChannelVersionRules.same(repFacets, repEpgKey, itemFacets, itemEpgKey)

                    if (isSameChannel) {
                        matchedGroup = group
                        break
                    }
                }

                if (matchedGroup != null) {
                    matchedGroup.add(item)
                } else {
                    groups.add(mutableListOf(item))
                }
            }

            return groups.map { group ->
                group.maxWithOrNull(
                    compareBy<OnAirItem> { ChannelFacets.of(it.channel.matchName).qualityRank }
                        .thenBy { if (it.channel.catchup) 1 else 0 }
                        .thenByDescending {
                            val idx = sourceOrder.indexOf(it.channel.sourceId)
                            if (idx >= 0) -idx else Int.MIN_VALUE
                        }
                        .thenByDescending { -it.channel.sortOrder },
                ) ?: group[0]
            }
        }
    }
}
