// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.mobile.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * A setting that asks instead of flipping on the spot: what it does in a sentence, then
 * Включить / Выключить with the current state ticked. «Лёгкий режим», «Превью каналов» and
 * «Быстрая загрузка портала» all use it, so the three read alike (П4, 11.11.2026).
 */
@Composable
fun OnOffSheet(
    title: String,
    description: String,
    enabled: Boolean,
    onSet: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = description)
        StageMenuItem(
            label = stringResource(R.string.light_mode_action_enable),
            onClick = { onSet(true); onDismiss() },
            checked = enabled,
            filled = enabled,
        )
        StageMenuItem(
            label = stringResource(R.string.light_mode_action_disable),
            onClick = { onSet(false); onDismiss() },
            checked = !enabled,
            filled = !enabled,
        )
    }
}

/** «Лёгкий режим»: what it does in one sentence, then Включить / Выключить — the television's dialog (П4). */
@Composable
fun LightModeSheet(enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    OnOffSheet(
        title = stringResource(R.string.light_mode_title),
        description = stringResource(R.string.light_mode_description),
        enabled = enabled,
        onSet = onSet,
        onDismiss = onDismiss,
    )
}
