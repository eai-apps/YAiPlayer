// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.mpx

@Composable
fun PortalResyncDialog(
    source: SourceEntity?,
    onSelectOption: (PortalResyncOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    val options = PortalResyncOption.availableFor(source)

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.portal_resync_submenu_title),
        body = source?.name ?: stringResource(R.string.content_all_playlists),
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
        },
    ) {
        options.forEachIndexed { index, option ->
            val icon = when (option) {
                PortalResyncOption.ALL -> OwnTVIcon.REFRESH
                PortalResyncOption.CHANNELS_ONLY -> OwnTVIcon.LIVE_TV
                PortalResyncOption.EPG_ONLY -> OwnTVIcon.EPG
                PortalResyncOption.CHANNELS_AND_EPG -> OwnTVIcon.REFRESH
                PortalResyncOption.VOD_ONLY -> OwnTVIcon.MOVIES
            }
            StagePopupOption(
                title = stringResource(option.labelRes),
                onClick = { onSelectOption(option); onDismiss() },
                modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                leading = { StagePopupIcon(icon) },
            )
        }
    }
}
