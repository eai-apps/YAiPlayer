package tv.own.owntv.features.home

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.home.OnAirGenre
import tv.own.owntv.core.home.OnAirItem
import tv.own.owntv.player.formatTime
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * "On Air Now" feed row for Android TV.
 * Displays genre chips (Movies, Series, Sports, News, Kids, Music, Educational) and a horizontal
 * row of cards showing current programmes with progress bar, end time, and "From start" catchup button.
 */
@Composable
fun HomeOnAirRow(
    items: List<OnAirItem>,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onWatchFromStartClick: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var selectedGenre by rememberSaveable { mutableStateOf(OnAirGenre.ALL) }

    // Only show chips that have programmes (plus ALL)
    val availableGenres = remember(items) {
        OnAirGenre.entries.filter { genre ->
            genre == OnAirGenre.ALL || items.any { item -> genre in item.genres }
        }
    }

    val visibleItems = remember(items, selectedGenre) {
        if (selectedGenre == OnAirGenre.ALL) items else items.filter { selectedGenre in it.genres }
    }

    Column(modifier) {
        HomeRowHeader(stringResource(R.string.home_row_now_on_air), small = null, start = start)

        if (availableGenres.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                contentPadding = PaddingValues(start = start, end = 64.mpx),
                modifier = Modifier.padding(bottom = 12.mpx).focusRestorer().focusGroup(),
            ) {
                items(availableGenres, key = { it.name }) { genre ->
                    val selected = genre == selectedGenre
                    StageSurface(
                        onClick = { selectedGenre = genre },
                        radius = 16.mpx,
                        modifier = Modifier.height(34.mpx),
                        focusStyle = StageFocus.FILLED,
                        idle = Modifier.background(
                            if (selected) stageAccent.accent.copy(alpha = 0.25f) else StageColors.Ink.copy(alpha = 0.08f),
                            RoundedCornerShape(16.mpx),
                        ),
                    ) {
                        Box(Modifier.padding(horizontal = 14.mpx, vertical = 6.mpx), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(genre.titleRes),
                                style = stageText(14, if (selected) 700 else 500),
                                color = if (selected) stageAccent.accent else StageColors.Text,
                            )
                        }
                    }
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(visibleItems, key = { _, item -> item.channel.id }) { index, item ->
                OnAirCard(
                    item = item,
                    onClick = { onChannelClick(item.channel) },
                    onWatchFromStart = if (item.canWatchFromStart) { { onWatchFromStartClick(item.channel, item.programme) } } else null,
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track),
                )
            }
        }
    }
}

@Composable
internal fun OnAirCard(
    item: OnAirItem,
    onClick: () -> Unit,
    onWatchFromStart: (() -> Unit)?,
    modifier: Modifier = Modifier,
    width: Dp = 340.mpx,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier.size(width, 150.mpx),
        focusStyle = StageFocus.FX,
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.mpx, vertical = 12.mpx),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.channel.displayLogoUrl.isNullOrBlank()) ChannelPlate(item.channel, Modifier.size(68.mpx, 48.mpx))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.channel.name,
                        style = stageText(18, 700),
                        color = StageColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.mpx))
                    Text(
                        item.programme.title,
                        style = stageText(14, 500),
                        color = StageColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Progress bar
            Box(Modifier.fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(StageColors.Ink.copy(alpha = 0.15f))) {
                Box(
                    Modifier.fillMaxWidth(item.progress).height(4.mpx).clip(RoundedCornerShape(2.mpx))
                        .background(Brush.horizontalGradient(listOf(a.accent, a.focus))),
                )
            }

            // Bottom bar: end time and optional "From start" button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.on_air_ends_at, formatTime(item.programme.stopMs)),
                    style = stageText(13, 500),
                    color = StageColors.Dim,
                )

                if (onWatchFromStart != null) {
                    StageButton(
                        text = stringResource(R.string.on_air_from_start),
                        icon = OwnTVIcon.REWIND,
                        height = 32.mpx,
                        textSize = 13,
                        onClick = onWatchFromStart,
                    )
                }
            }
        }
    }
}
