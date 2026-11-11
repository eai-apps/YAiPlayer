// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.live

import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** Stored/user-priority programmes win; provider rows fill gaps within the next 24 hours. */
internal fun previewGuideWindow(
    stored: List<EpgProgrammeEntity>,
    provider: List<EpgProgrammeEntity>,
    from: Long,
    until: Long,
): List<EpgProgrammeEntity> {
    val saved = stored.filter { it.stopMs > from && it.startMs < until }
    return (saved + provider.filter { candidate ->
        candidate.stopMs > from && candidate.startMs < until &&
            saved.none { it.startMs < candidate.stopMs && it.stopMs > candidate.startMs }
    }).distinctBy { it.startMs }.sortedBy { it.startMs }
}
