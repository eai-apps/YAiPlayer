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
 * A setting that asks instead of flipping on the spot (П4, 11.11.2026): a bare on/off row gave no
 * hint of what it does. The body says it in a sentence; the current state is ringed. «Лёгкий
 * режим», «Превью каналов» and «Быстрая загрузка портала» all use it, so the three read alike.
 */
@Composable
fun OnOffDialog(title: String, body: String, enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = body,
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

/** «Лёгкий режим»: one sentence on what it does, then Включить / Выключить (П4). */
@Composable
fun LightModeDialog(enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    OnOffDialog(
        title = stringResource(R.string.light_mode_title),
        body = stringResource(R.string.light_mode_description),
        enabled = enabled,
        onSet = onSet,
        onDismiss = onDismiss,
    )
}
