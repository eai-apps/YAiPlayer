// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStepper

@Composable
fun EpgTimeShiftSheet(
    sourceId: Long,
    sourceName: String,
    onDismiss: () -> Unit,
    shiftStore: PlaylistEpgShiftStore = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val shiftMinutes by shiftStore.observeShift(sourceId).collectAsStateWithLifecycle(0)

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.epg_time_shift_title),
    ) {
        StageSheetHeader(
            title = stringResource(R.string.epg_time_shift_title),
            eyebrow = sourceName,
            subtitle = stringResource(R.string.epg_time_shift_description),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StageSettingRow(
                title = stringResource(R.string.epg_time_shift_title),
                subtitle = stringResource(R.string.epg_time_shift_description),
                minHeight = 56.dp,
            ) {
                StageStepper(
                    value = EpgTimeShift.formatShiftMinutes(shiftMinutes),
                    onMinus = {
                        val next = EpgTimeShift.stepShiftMinutes(shiftMinutes, -1)
                        scope.launch { shiftStore.setShift(sourceId, next) }
                    },
                    onPlus = {
                        val next = EpgTimeShift.stepShiftMinutes(shiftMinutes, 1)
                        scope.launch { shiftStore.setShift(sourceId, next) }
                    },
                    minusEnabled = shiftMinutes > EpgTimeShift.MIN_SHIFT_MINUTES,
                    plusEnabled = shiftMinutes < EpgTimeShift.MAX_SHIFT_MINUTES,
                )
            }

            if (shiftMinutes != 0) {
                StageButton(
                    text = stringResource(R.string.epg_time_shift_reset),
                    onClick = { scope.launch { shiftStore.setShift(sourceId, 0) } },
                    style = StageButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            StageButton(
                text = stringResource(R.string.common_done),
                onClick = onDismiss,
                style = StageButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
