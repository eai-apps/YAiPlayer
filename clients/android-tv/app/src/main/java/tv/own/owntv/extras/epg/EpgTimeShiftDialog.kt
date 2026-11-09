// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.features.settings.PanelStepper
import tv.own.owntv.ui.stage.StagePopup

@Composable
fun EpgTimeShiftDialog(
    sourceId: Long,
    sourceName: String,
    onDismiss: () -> Unit,
    shiftStore: PlaylistEpgShiftStore = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val shiftMinutes by shiftStore.observeShift(sourceId).collectAsStateWithLifecycle(0)

    val stepper: @Composable () -> Unit = {
        PanelStepper(
            value = EpgTimeShift.formatShiftMinutes(shiftMinutes),
            onStep = { d ->
                val next = EpgTimeShift.stepShiftMinutes(shiftMinutes, d)
                scope.launch { shiftStore.setShift(sourceId, next) }
            },
            onReset = {
                scope.launch { shiftStore.setShift(sourceId, 0) }
            },
            onDone = onDismiss,
        )
    }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.epg_time_shift_title),
        eyebrow = sourceName,
        body = stringResource(R.string.epg_time_shift_description),
    ) {
        stepper()
    }
}
