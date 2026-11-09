// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import tv.own.owntv.core.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

enum class PortalResyncOption(val labelRes: Int) {
    ALL(R.string.portal_resync_all),
    CHANNELS_ONLY(R.string.portal_resync_channels_only),
    EPG_ONLY(R.string.portal_resync_epg_only),
    CHANNELS_AND_EPG(R.string.portal_resync_channels_and_epg),
    VOD_ONLY(R.string.portal_resync_vod_only);

    companion object {
        fun availableFor(source: SourceEntity?): List<PortalResyncOption> {
            val base = listOf(ALL, CHANNELS_ONLY, EPG_ONLY, CHANNELS_AND_EPG)
            if (source == null) return base
            val hasVod = source.type != SourceType.M3U && (source.syncMovies || source.syncSeries)
            return if (hasVod) base + VOD_ONLY else base
        }
    }
}

object SessionFirstEntryState {
    @Volatile var isFirstLiveEntry: Boolean = true
    @Volatile var isFirstMoviesEntry: Boolean = true
    @Volatile var isFirstSeriesEntry: Boolean = true
}
