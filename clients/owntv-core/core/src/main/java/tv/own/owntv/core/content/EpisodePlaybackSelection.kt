// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

import tv.own.owntv.core.database.entity.EpisodeEntity

/** A refresh can replace the selected row. Never turn a missing row id into episode one. */
fun episodePlaybackIndex(queue: List<EpisodeEntity>, selected: EpisodeEntity): Int {
    fun matches(row: EpisodeEntity) = row.seriesId == selected.seriesId &&
        row.seasonNumber == selected.seasonNumber && row.episodeNumber == selected.episodeNumber
    return queue.indexOfFirst { it.id == selected.id && matches(it) }.takeIf { it >= 0 }
        ?: queue.indexOfFirst {
            matches(it) && !selected.remoteId.isNullOrBlank() && it.remoteId == selected.remoteId
        }.takeIf { it >= 0 }
        ?: queue.indexOfFirst { matches(it) }.takeIf { it >= 0 }
        ?: -1
}
