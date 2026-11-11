// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.shell.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

/**
 * «Лёгкий режим» asks instead of flipping on the spot (П4, 10.11.2026): a bare on/off row gave no
 * hint of what the mode does. The body says it in one sentence; the current state is ringed.
 */
@Composable
fun LightModeDialog(enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.light_mode_title),
        body = stringResource(R.string.light_mode_description),
        width = 640.mpx,
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.light_mode_action_enable),
            onClick = { onSet(true); onDismiss() },
            modifier = if (enabled) Modifier.focusRequester(focus) else Modifier,
            leading = { focused -> tv.own.owntv.ui.stage.StagePopupRadio(on = enabled, focused = focused) },
        )
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.light_mode_action_disable),
            onClick = { onSet(false); onDismiss() },
            modifier = if (!enabled) Modifier.focusRequester(focus) else Modifier,
            leading = { focused -> tv.own.owntv.ui.stage.StagePopupRadio(on = !enabled, focused = focused) },
        )
    }
}
