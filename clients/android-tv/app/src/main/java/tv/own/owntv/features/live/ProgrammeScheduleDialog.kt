package tv.own.owntv.features.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.theme.mpx

/**
 * Owner 26.11: «В эфире сейчас» in the player's top bar can be chosen with the remote and opens this list —
 * what is on the channel NOW and what comes next (up to a week of the guide), grouped by day. Nothing here
 * plays: channels without an archive have nothing to open, the owner only wants to see which films are ahead.
 * OK on a row opens/closes its description (fetched on demand, the list query drops it).
 */
@Composable
internal fun ProgrammeScheduleDialog(
    title: String,
    load: suspend () -> List<EpgProgrammeEntity>,
    description: suspend (Long) -> String?,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val programmes by produceState<List<EpgProgrammeEntity>?>(null) {
        value = runCatching { load() }.getOrDefault(emptyList())
    }
    var openId by remember { mutableStateOf<Long?>(null) }
    val openText by produceState<String?>(null, openId) {
        value = openId?.let { runCatching { description(it) }.getOrNull() }
    }
    val timeOnly = rememberBestDateFormatter("Hm")
    val dayTitle = rememberBestDateFormatter("EEEdMMM")
    val zone = remember { java.util.TimeZone.getDefault() }
    val list = programmes
    val now = remember { System.currentTimeMillis() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Open on the programme that is on air (or the next one), with the past scrolled up above it.
    val focusId = remember(list) { list?.firstOrNull { it.stopMs > now }?.id ?: list?.lastOrNull()?.id }
    LaunchedEffect(list) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        var index = 0
        for (day in list.groupBy { (it.startMs + zone.getOffset(it.startMs)) / 86_400_000L }.values) {
            index++
            val hit = day.indexOfFirst { it.id == focusId }
            if (hit >= 0) { index += hit; break }
            index += day.size
        }
        runCatching { listState.scrollToItem((index - 1).coerceAtLeast(0)) }
        kotlinx.coroutines.delay(60)
        runCatching { first.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = if (list != null && list.isEmpty()) stringResource(R.string.content_schedule_empty) else null,
        eyebrow = null,
        scroll = false,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        if (!list.isNullOrEmpty()) {
            val byDay = remember(list) { list.groupBy { (it.startMs + zone.getOffset(it.startMs)) / 86_400_000L } }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                byDay.values.forEach { day ->
                    item(key = -day.first().id) {
                        androidx.tv.material3.Text(
                            text = dayTitle(day.first().startMs),
                            style = tv.own.owntv.ui.theme.stageText(17, 700),
                            color = tv.own.owntv.ui.theme.StageColors.Muted,
                            modifier = Modifier.padding(start = 8.mpx, top = 12.mpx, bottom = 4.mpx),
                        )
                    }
                    items(day, key = { it.id }) { p ->
                        val onAir = p.startMs <= now && p.stopMs > now
                        val extra = if (openId == p.id) openText?.takeIf { it.isNotBlank() } else null
                        tv.own.owntv.ui.stage.StagePopupOption(
                            title = p.title,
                            subtitle = buildString {
                                append(stringResource(R.string.content_live_time_range, timeOnly(p.startMs), timeOnly(p.stopMs)))
                                if (extra != null) { append('\n'); append(extra) }
                            },
                            onClick = { openId = if (openId == p.id) null else p.id },
                            chosen = onAir,
                            modifier = if (p.id == focusId) Modifier.focusRequester(first) else Modifier,
                            leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.CLOCK) },
                        )
                    }
                }
            }
        }
    }
}
