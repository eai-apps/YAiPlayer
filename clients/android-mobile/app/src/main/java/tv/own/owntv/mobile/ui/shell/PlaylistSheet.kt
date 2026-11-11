package tv.own.owntv.mobile.ui.shell

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StagePlaylistMark
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Show playlist, the television's sheet: All playlists, then each playlist with its mark
 * and its channel count ("55K channels"; Settings › Layout › Navigation › Show counts). The current
 * one is filled with a ✓ and carries no count, as on the television. `All playlists` is id -1, the value Settings stores.
 */
@Composable
fun PlaylistSheet(
    playlists: List<SourceEntity>,
    activeId: Long,
    names: List<String>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings: SettingsRepository = koinInject()
    val activeProfileId by settings.activeProfileId.collectAsStateWithLifecycle(-1L)
    val quickStartFlow = remember(activeProfileId) {
        if (activeProfileId >= 0L) settings.quickStart(activeProfileId) else kotlinx.coroutines.flow.flowOf(false)
    }
    val quickStart by quickStartFlow.collectAsStateWithLifecycle(false)
    val channelDao: ChannelDao = koinInject()
    val showCounts by settings.sheetCounts.collectAsStateWithLifecycle(true)
    val counts by produceState(emptyMap<Long, Int>(), playlists) {
        value = playlists.associate { it.id to runCatching { channelDao.countForSourceOnce(it.id) }.getOrDefault(0) }
    }
    val locale = LocalConfiguration.current.locales[0]
    val compact = remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
    val title = stringResource(R.string.content_playlist_picker_title)
    val scheduler: tv.own.owntv.core.sync.work.CatalogSyncScheduler = koinInject()
    val finalizer: tv.own.owntv.core.sync.ImportFinalizer = koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var askResync by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showQuickStartSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showPreviewSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showCategoriesSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showCategoriesSheet) {
        val targets = playlists.filter { it.id == activeId }.ifEmpty { playlists }
        tv.own.owntv.extras.categories.CategoriesVisibilitySheet(
            profileId = activeProfileId,
            sourceIds = targets.map { it.id },
            onDismiss = { showCategoriesSheet = false },
        )
        return
    }
    if (showQuickStartSheet) {
        OnOffSheet(
            title = stringResource(R.string.quick_start_title),
            description = stringResource(R.string.quick_start_hint),
            enabled = quickStart,
            onSet = { on -> scope.launch { if (activeProfileId >= 0L) settings.setQuickStart(activeProfileId, on) } },
            onDismiss = { showQuickStartSheet = false },
        )
        return
    }
    if (showPreviewSheet) {
        val livePreviewNow by settings.livePreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
        OnOffSheet(
            title = stringResource(tv.own.owntv.core.R.string.channel_preview),
            description = stringResource(tv.own.owntv.core.R.string.channel_preview_description),
            enabled = livePreviewNow,
            onSet = { on -> scope.launch { settings.setLivePreviewEnabled(on) } },
            onDismiss = { showPreviewSheet = false },
        )
        return
    }
    if (askResync) {
        val targets = playlists.filter { it.id == activeId }.ifEmpty { playlists }
        val activeSource = playlists.firstOrNull { it.id == activeId } ?: playlists.firstOrNull()
        val quickActionsSync: tv.own.owntv.core.sync.QuickActionsSync = org.koin.compose.koinInject()
        tv.own.owntv.extras.portal.PortalResyncSheet(
            source = activeSource,
            onSelectOption = { option ->
                run {
                    targets.forEach { source ->
                        when (option) {
                            tv.own.owntv.extras.portal.PortalResyncOption.ALL -> quickActionsSync.refreshAll(source)
                            tv.own.owntv.extras.portal.PortalResyncOption.CHANNELS_ONLY -> quickActionsSync.refreshChannelsOnly(source)
                            tv.own.owntv.extras.portal.PortalResyncOption.EPG_ONLY -> quickActionsSync.refreshEpg(source)
                            tv.own.owntv.extras.portal.PortalResyncOption.CHANNELS_AND_EPG -> quickActionsSync.refreshChannelsAndEpg(source)
                            tv.own.owntv.extras.portal.PortalResyncOption.VOD_ONLY -> quickActionsSync.refreshVod(source)
                        }
                    }
                }
                askResync = false
                onDismiss()
            },
            onDismiss = { askResync = false },
        )
        return
    }

    fun pick(id: Long) {
        onSelect(id)
        onDismiss()
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.content_playlist_picker_subtitle))
        StageMenuItem(
            label = stringResource(R.string.content_all_playlists),
            onClick = { pick(-1L) },
            icon = MobileIcons.Layers,
            filled = activeId == -1L,
            checked = activeId == -1L,
        )
        playlists.forEachIndexed { i, source ->
            val current = source.id == activeId
            val count = counts[source.id]
            StageMenuItem(
                label = names[i],
                onClick = { pick(source.id) },
                leading = { StagePlaylistMark(PlaylistMark.of(names[i], i)) },
                filled = current,
                checked = current,
                count = if (showCounts && !current && count != null) {
                    pluralStringResource(R.plurals.sync_count_channels, count, compact.format(count))
                } else null,
            )
        }
        val activeSource = playlists.firstOrNull { it.id == activeId } ?: playlists.firstOrNull()
        val resyncLabel = stringResource(if (activeSource?.type == tv.own.owntv.core.model.SourceType.STALKER) R.string.quick_refresh_portal else R.string.quick_refresh_playlist)
        StageMenuItem(
            label = stringResource(if (quickStart) R.string.quick_start_on else R.string.quick_start_off),
            onClick = { showQuickStartSheet = true },
            icon = MobileIcons.PlayCircle,
            selected = quickStart,
        )
        StageMenuItem(
            label = resyncLabel,
            onClick = { askResync = true },
            icon = MobileIcons.Refresh,
        )
        val livePreview by settings.livePreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
        val lightModeOn by settings.lightModeEnabled.collectAsStateWithLifecycle(initialValue = false)
        // Light mode overrides the preview, so the row shows what actually happens.
        val previewShown = livePreview && !lightModeOn
        StageMenuItem(
            label = stringResource(tv.own.owntv.core.R.string.channel_preview),
            onClick = { showPreviewSheet = true },
            icon = MobileIcons.Tv,
            value = stringResource(if (previewShown) R.string.common_on else R.string.common_off),
            selected = previewShown,
        )
        StageMenuItem(
            label = stringResource(R.string.quick_categories),
            onClick = { showCategoriesSheet = true },
            icon = MobileIcons.Folder,
        )
        Text(
            stringResource(R.string.quick_start_hint),
            style = stageText(11.5f, 500),
            color = LocalStagePalette.current.dim,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Text(
            stringResource(R.string.content_playlist_marks_note),
            style = stageText(11.5f, 500),
            color = LocalStagePalette.current.dim,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
    }
}
