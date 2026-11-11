// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

@Composable
fun LightModeMobileSheet(
    onDismiss: () -> Unit,
) {
    val settings: SettingsRepository = koinInject()
    val lightMode by settings.lightModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val sheetTitle = stringResource(R.string.light_mode_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = sheetTitle) {
        StageSheetHeader(
            title = sheetTitle,
            subtitle = stringResource(R.string.light_mode_dialog_description),
        )
        StageMenuItem(
            label = stringResource(R.string.light_mode_turn_on),
            onClick = {
                scope.launch { settings.setLightModeEnabled(true) }
                onDismiss()
            },
            checked = lightMode,
            filled = lightMode,
        )
        StageMenuItem(
            label = stringResource(R.string.light_mode_turn_off),
            onClick = {
                scope.launch { settings.setLightModeEnabled(false) }
                onDismiss()
            },
            checked = !lightMode,
            filled = !lightMode,
        )
    }
}
