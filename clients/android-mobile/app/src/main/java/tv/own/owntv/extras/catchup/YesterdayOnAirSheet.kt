// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.catchup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageChoiceRow
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText

@Composable
fun YesterdayOnAirSheet(
    channel: ChannelEntity,
    loadProgrammes: suspend (fromMs: Long, toMs: Long) -> List<EpgProgrammeEntity>,
    onPick: (EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val days = remember(channel.catchupDays) {
        YesterdayOnAir.availableDays(channel.catchupDays)
    }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val currentDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()

    var programmes by remember { mutableStateOf<List<EpgProgrammeEntity>?>(null) }
    LaunchedEffect(currentDay) {
        programmes = null
        if (currentDay != null) {
            programmes = runCatching { loadProgrammes(currentDay.startMs, currentDay.stopMs) }.getOrDefault(emptyList())
        } else {
            programmes = emptyList()
        }
    }

    val locales = LocalConfiguration.current.locales
    val timeFormat = remember(locales) { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val title = stringResource(R.string.yesterday_on_air_title, channel.name)

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)

        if (days.size > 1) {
            val options = days.take(4).map { day ->
                when (day.daysAgo) {
                    1 -> stringResource(R.string.yesterday_day_yesterday)
                    2 -> stringResource(R.string.yesterday_day_2_days)
                    else -> stringResource(R.string.yesterday_day_ago, day.daysAgo)
                }
            }
            StageChoiceRow(
                options = options,
                selected = selectedDayIndex,
                onSelect = { selectedDayIndex = it },
            )
        }

        when (val list = programmes) {
            null -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            else -> if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.yesterday_on_air_empty),
                        style = stageText(14f, 400),
                        color = LocalStagePalette.current.muted,
                    )
                }
            } else {
                LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
                    items(list, key = { it.id }) { prog ->
                        val timeRange = stringResource(
                            R.string.content_live_time_range_plain,
                            timeFormat.format(Date(prog.startMs)),
                            timeFormat.format(Date(prog.stopMs)),
                        )
                        StageMenuItem(
                            label = prog.title,
                            onClick = { onPick(prog); onDismiss() },
                            icon = MobileIcons.Catchup,
                            value = timeRange,
                        )
                    }
                }
            }
        }
    }
}
