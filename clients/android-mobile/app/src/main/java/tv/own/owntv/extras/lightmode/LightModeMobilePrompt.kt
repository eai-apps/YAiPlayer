// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet

@Composable
fun LightModeMobilePrompt(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    StageConfirmSheet(
        title = stringResource(R.string.light_mode_prompt_title),
        text = stringResource(R.string.light_mode_prompt_body),
        confirm = stringResource(R.string.light_mode_action_enable),
        cancel = stringResource(R.string.light_mode_action_not_now),
        onConfirm = onEnable,
        onDismiss = onDismiss,
    )
}
