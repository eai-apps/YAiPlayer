// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.catchup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.mpx
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.stageText

@Composable
fun YesterdayOnAirDialog(
    channel: ChannelEntity,
    loadProgrammes: suspend (fromMs: Long, toMs: Long) -> List<EpgProgrammeEntity>,
    onPick: (EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatTime = rememberSystemTimeFormatter()
    val days = remember(channel.catchupDays) {
        YesterdayOnAir.availableDays(channel.catchupDays)
    }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val currentDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()

    val programmes by produceState<List<EpgProgrammeEntity>?>(initialValue = null, key1 = currentDay) {
        value = null
        if (currentDay != null) {
            value = runCatching { loadProgrammes(currentDay.startMs, currentDay.stopMs) }.getOrDefault(emptyList())
        } else {
            value = emptyList()
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(programmes) {
        if (!programmes.isNullOrEmpty()) {
            kotlinx.coroutines.delay(60)
            runCatching { firstFocus.requestFocus() }
        }
    }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.yesterday_on_air_title, channel.name),
        body = stringResource(R.string.yesterday_on_air_prompt),
        width = 880.mpx,
        scroll = false,
        buttons = {
            StageButton(
                stringResource(R.string.content_close),
                onClick = onDismiss,
                height = 56.mpx,
                textSize = 19,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            // Day selector tabs
            if (days.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.mpx),
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                ) {
                    days.take(5).forEachIndexed { index, day ->
                        val selected = index == selectedDayIndex
                        val label = when (day.daysAgo) {
                            1 -> stringResource(R.string.yesterday_day_yesterday)
                            2 -> stringResource(R.string.yesterday_day_2_days)
                            else -> stringResource(R.string.yesterday_day_ago, day.daysAgo)
                        }
                        StageButton(
                            label = label,
                            onClick = { selectedDayIndex = index },
                            selected = selected,
                            height = 42.mpx,
                            textSize = 16,
                        )
                    }
                }
            }

            // Programme list
            when (val list = programmes) {
                null -> Box(Modifier.fillMaxWidth().height(140.mpx), contentAlignment = Alignment.Center) {
                    OwnTVSpinner(sizeDp = 28)
                }
                else -> if (list.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.mpx), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.yesterday_on_air_empty),
                            style = stageText(18, 400),
                            color = StageColors.Muted,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.mpx),
                    ) {
                        items(list, key = { it.id }) { prog ->
                            val timeLabel = stringResource(R.string.content_live_time_range_plain, formatTime(prog.startMs), formatTime(prog.stopMs))
                            val isFirst = prog == list.first()
                            StagePopupOption(
                                title = prog.title,
                                subtitle = timeLabel,
                                onClick = { onPick(prog) },
                                modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                                leading = {
                                    tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.REWIND)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
