// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.R
import tv.own.owntv.ui.stage.StageConfirm
import tv.own.owntv.ui.theme.mpx

@Composable
fun LightModeTvPrompt(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    StageConfirm(
        title = stringResource(R.string.light_mode_prompt_title),
        body = stringResource(R.string.light_mode_prompt_body),
        confirm = stringResource(R.string.light_mode_action_enable),
        cancel = stringResource(R.string.light_mode_action_not_now),
        onConfirm = onEnable,
        onCancel = onDismiss,
        width = 560.mpx,
    )
}
