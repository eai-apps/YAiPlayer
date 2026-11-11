// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.mobile.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/** «Лёгкий режим»: what it does in one sentence, then Включить / Выключить — the television's dialog (П4). */
@Composable
fun LightModeSheet(enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.light_mode_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.light_mode_description))
        StageMenuItem(
            label = stringResource(R.string.light_mode_action_enable),
            onClick = { onSet(true); onDismiss() },
            checked = enabled,
        )
        StageMenuItem(
            label = stringResource(R.string.light_mode_action_disable),
            onClick = { onSet(false); onDismiss() },
            checked = !enabled,
        )
    }
}
