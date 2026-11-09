// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import tv.own.owntv.core.database.entity.SourceEntity

internal enum class QuickSyncAction { PLAYLIST, CHANNELS, EPG, CHANNELS_AND_EPG, ALL, VOD }
internal data class SelectiveSyncPlan(val catalog: SyncContentTypes?, val epg: Boolean)

internal fun selectiveSyncPlan(action: QuickSyncAction, source: SourceEntity): SelectiveSyncPlan = when (action) {
    QuickSyncAction.PLAYLIST -> SelectiveSyncPlan(SyncContentTypes.enabledOf(source), false)
    QuickSyncAction.CHANNELS -> SelectiveSyncPlan(SyncContentTypes(true, false, false), false)
    QuickSyncAction.EPG -> SelectiveSyncPlan(null, true)
    QuickSyncAction.CHANNELS_AND_EPG -> SelectiveSyncPlan(SyncContentTypes(true, false, false), true)
    QuickSyncAction.ALL -> SelectiveSyncPlan(SyncContentTypes.enabledOf(source), true)
    QuickSyncAction.VOD -> SelectiveSyncPlan(SyncContentTypes(false, source.syncMovies, source.syncSeries), false)
}

internal suspend fun dispatchSelectiveSync(
    plan: SelectiveSyncPlan,
    catalog: suspend (SyncContentTypes) -> Unit,
    epg: suspend () -> Unit,
) {
    plan.catalog?.takeIf { it.hasAny }?.let { catalog(it) }
    if (plan.epg) epg()
}
