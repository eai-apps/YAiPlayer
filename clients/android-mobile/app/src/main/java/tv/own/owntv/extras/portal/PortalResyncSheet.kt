// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import androidx.compose.runtime.Composable
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

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = subtitle)
        options.forEach { option ->
            val icon = when (option) {
                PortalResyncOption.ALL -> MobileIcons.Refresh
                PortalResyncOption.CHANNELS_ONLY -> MobileIcons.LiveTv
                PortalResyncOption.EPG_ONLY -> MobileIcons.Guide
                PortalResyncOption.CHANNELS_AND_EPG -> MobileIcons.Refresh
                PortalResyncOption.VOD_ONLY -> MobileIcons.Movie
            }
            StageMenuItem(
                label = stringResource(option.labelRes),
                onClick = {
                    onSelectOption(option)
                    onDismiss()
                },
                icon = icon,
            )
        }
    }
}
