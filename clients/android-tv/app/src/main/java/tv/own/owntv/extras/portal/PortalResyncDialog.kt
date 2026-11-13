// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val control: tv.own.owntv.core.sync.RefreshControl = org.koin.compose.koinInject()
    val running by control.running.collectAsStateWithLifecycle(initialValue = null)
    // «Только телепрограмму» opens a second list: which guide (owner 13.11).
    var guideMenu by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (guideMenu) {
        val gFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { gFocus.requestFocus() } }
        StagePopup(
            onDismiss = { guideMenu = false },
            title = stringResource(tv.own.owntv.core.R.string.guide_choice_title),
            body = source?.name ?: stringResource(R.string.content_all_playlists),
            buttons = { StageButton(stringResource(R.string.common_cancel), onClick = { guideMenu = false }, height = 56.mpx, textSize = 19) },
        ) {
            GuideChoice.entries.forEachIndexed { i, c ->
                val label = when (c) {
                    GuideChoice.OWN -> stringResource(c.ownLabel(source))
                    GuideChoice.FEED_1, GuideChoice.FEED_2, GuideChoice.FEED_3 -> stringResource(c.labelRes, c.feed)
                    GuideChoice.ALL -> stringResource(c.labelRes)
                }
                StagePopupOption(
                    title = label,
                    onClick = {
                        when (c) {
                            GuideChoice.OWN -> onSelectOption(PortalResyncOption.EPG_ONLY)
                            GuideChoice.ALL -> { onSelectOption(PortalResyncOption.EPG_ONLY); GuideChoice.runFeeds(source, listOf(1, 2, 3)) }
                            else -> GuideChoice.runFeeds(source, listOf(c.feed))
                        }
                        guideMenu = false
                        onDismiss()
                    },
                    modifier = if (i == 0) Modifier.focusRequester(gFocus) else Modifier,
                    leading = { StagePopupIcon(OwnTVIcon.EPG) },
                )
            }
        }
        return
    }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.portal_resync_submenu_title),
        // Owner 12.11: say at once when an update is already running, and which step it is on.
        body = running?.let { stringResource(R.string.sync_already_running, it.step, it.total, it.name) }
            ?: source?.name ?: stringResource(R.string.content_all_playlists),
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
        },
    ) {
        if (running != null) {
            StagePopupOption(
                title = stringResource(R.string.sync_stop_running),
                onClick = { control.cancelAll(); onDismiss() },
                modifier = Modifier.focusRequester(focus),
                leading = { StagePopupIcon(OwnTVIcon.CLOSE) },
            )
        }
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
                onClick = {
                    if (option == PortalResyncOption.EPG_ONLY) { guideMenu = true; return@StagePopupOption }
                    onSelectOption(option)
                    onDismiss()
                },
                modifier = if (index == 0 && running == null) Modifier.focusRequester(focus) else Modifier,
                leading = { StagePopupIcon(icon) },
            )
        }
    }
}
