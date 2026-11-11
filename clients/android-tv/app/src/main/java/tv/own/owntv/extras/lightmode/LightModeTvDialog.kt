// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StagePopupRadio
import tv.own.owntv.ui.theme.mpx

@Composable
fun LightModeTvDialog(
    onDismiss: () -> Unit,
) {
    val settings: SettingsRepository = koinInject()
    val lightMode by settings.lightModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstRequester.requestFocus() }
    }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.light_mode_title),
        body = stringResource(R.string.light_mode_dialog_description),
        width = 560.mpx,
    ) {
        StagePopupOption(
            title = stringResource(R.string.light_mode_turn_on),
            onClick = {
                scope.launch { settings.setLightModeEnabled(true) }
                onDismiss()
            },
            modifier = if (lightMode) Modifier.focusRequester(firstRequester) else Modifier,
            leading = { focused -> StagePopupRadio(on = lightMode, focused = focused) },
        )
        StagePopupOption(
            title = stringResource(R.string.light_mode_turn_off),
            onClick = {
                scope.launch { settings.setLightModeEnabled(false) }
                onDismiss()
            },
            modifier = if (!lightMode) Modifier.focusRequester(firstRequester) else Modifier,
            leading = { focused -> StagePopupRadio(on = !lightMode, focused = focused) },
        )
    }
}
