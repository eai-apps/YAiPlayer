// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

@Composable
fun PortalResyncSheet(
    source: SourceEntity?,
    onSelectOption: (PortalResyncOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = PortalResyncOption.availableFor(source)
    val title = stringResource(R.string.portal_resync_submenu_title)
    val subtitle = source?.name ?: stringResource(R.string.content_all_playlists)

    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmClear) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val epgRepo: tv.own.owntv.core.repository.EpgRepository = org.koin.compose.koinInject()
        tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet(
            title = stringResource(tv.own.owntv.core.R.string.guide_clear_all),
            text = stringResource(tv.own.owntv.core.R.string.guide_clear_all_body),
            confirm = stringResource(R.string.common_delete),
            confirmIcon = MobileIcons.Delete,
            danger = true,
            onConfirm = { epgRepo.clearAllGuidesInBackground(ctx); confirmClear = false; onDismiss() },
            onDismiss = { confirmClear = false },
        )
        return
    }
    var guideMenu by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (guideMenu) {
        val gTitle = stringResource(tv.own.owntv.core.R.string.guide_choice_title)
        MobileBottomSheet(onDismissRequest = onDismiss, title = gTitle) {
            StageSheetHeader(title = gTitle, subtitle = subtitle)
            GuideChoice.entries.forEach { c ->
                val label = when (c) {
                    GuideChoice.OWN -> stringResource(c.ownLabel(source))
                    GuideChoice.FEED_1, GuideChoice.FEED_2, GuideChoice.FEED_3 -> stringResource(c.labelRes, c.feed)
                    GuideChoice.ALL -> stringResource(c.labelRes)
                }
                StageMenuItem(
                    label = label,
                    onClick = {
                        when (c) {
                            GuideChoice.OWN -> onSelectOption(PortalResyncOption.EPG_ONLY)
                            GuideChoice.ALL -> { onSelectOption(PortalResyncOption.EPG_ONLY); GuideChoice.runFeeds(source, listOf(1, 2, 3)) }
                            else -> GuideChoice.runFeeds(source, listOf(c.feed))
                        }
                        onDismiss()
                    },
                    icon = MobileIcons.Guide,
                )
            }
            StageMenuItem(
                label = stringResource(tv.own.owntv.core.R.string.guide_clear_all),
                onClick = { guideMenu = false; confirmClear = true },
                icon = MobileIcons.Delete,
            )
        }
        return
    }
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = subtitle)
        options.forEach { option ->
            val icon = when (option) {
                PortalResyncOption.ALL -> MobileIcons.Refresh
                PortalResyncOption.CHANNELS_ONLY -> MobileIcons.LiveTv
                PortalResyncOption.EPG_ONLY -> MobileIcons.Guide
                PortalResyncOption.CHANNELS_AND_EPG -> MobileIcons.Refresh
                PortalResyncOption.VOD_ONLY -> MobileIcons.Movie
                PortalResyncOption.CLEAR_GUIDE -> MobileIcons.Delete
            }
            StageMenuItem(
                label = stringResource(option.labelRes),
                onClick = {
                    // «Только телепрограмму» → which guide (owner 13.11)
                    if (option == PortalResyncOption.EPG_ONLY) { guideMenu = true; return@StageMenuItem }
                    if (option == PortalResyncOption.CLEAR_GUIDE) { confirmClear = true; return@StageMenuItem }
                    onSelectOption(option)
                    onDismiss()
                },
                icon = icon,
            )
        }
    }
}
