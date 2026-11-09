// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.home.ChannelPlate
import tv.own.owntv.features.home.HomeRowHeader
import tv.own.owntv.features.home.timeLeftText
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageButtonStyle
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@Composable
fun FavoritesHubTvScreen(
    onPlayChannel: (ChannelEntity) -> Unit,
    onPlayCatchup: ((ChannelEntity, EpgProgrammeEntity) -> Unit)? = null,
    onOpenLive: () -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    contentStart: Dp = 150.mpx,
    modifier: Modifier = Modifier,
    vm: FavoritesHubViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uncategorizedLabel = stringResource(R.string.favorites_hub_uncategorized)
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onEntryHook?.invoke {
            runCatching { firstItemFocus.requestFocus() }.isSuccess
        }
        vm.refresh(uncategorizedLabel)
    }

    if (state.isEmpty) {
        FavoritesHubEmptyTv(
            onOpenLive = onOpenLive,
            contentStart = contentStart,
            focusRequester = firstItemFocus,
            onFocused = onChildFocused,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
        contentPadding = PaddingValues(top = 40.mpx, bottom = 64.mpx),
        verticalArrangement = Arrangement.spacedBy(24.mpx),
    ) {
        // Header with title and Playlist filter pills
        item {
            FavoritesHubHeaderTv(
                filter = state.filter,
                onFilterChanged = { newFilter -> vm.setFilter(newFilter, uncategorizedLabel) },
                contentStart = contentStart,
                firstFocusRequester = firstItemFocus,
            )
        }

        // Top row: All favorites
        if (state.allFavorites.isNotEmpty()) {
            item {
                FavoritesHubChannelRowTv(
                    title = stringResource(R.string.favorites_hub_row_all),
                    channels = state.allFavorites,
                    contentStart = contentStart,
                    onPlayChannel = onPlayChannel,
                    onPlayCatchup = onPlayCatchup,
                )
            }
        }

        // Category rows below
        itemsIndexed(items = state.categoryRows) { _, row ->
            FavoritesHubChannelRowTv(
                title = row.categoryName,
                channels = row.channels,
                contentStart = contentStart,
                onPlayChannel = onPlayChannel,
                onPlayCatchup = onPlayCatchup,
            )
        }
    }
}

@Composable
private fun FavoritesHubHeaderTv(
    filter: FavoritesHubFilter,
    onFilterChanged: (FavoritesHubFilter) -> Unit,
    contentStart: Dp,
    firstFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = contentStart, end = 64.mpx),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.favorites_hub_title),
                style = stageText(36, 800),
                color = StageColors.Text,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allActive = filter == FavoritesHubFilter.ALL_PLAYLISTS
                StagePill(
                    onClick = { onFilterChanged(FavoritesHubFilter.ALL_PLAYLISTS) },
                    label = stringResource(R.string.favorites_hub_filter_all),
                    selected = allActive,
                    icon = OwnTVIcon.LAYERS,
                    modifier = Modifier.focusRequester(firstFocusRequester),
                )
                StagePill(
                    onClick = { onFilterChanged(FavoritesHubFilter.CURRENT_ONLY) },
                    label = stringResource(R.string.favorites_hub_filter_current),
                    selected = !allActive,
                    icon = OwnTVIcon.PLAYLIST,
                )
            }
        }
    }
}

@Composable
private fun FavoritesHubChannelRowTv(
    title: String,
    channels: List<FavoriteChannelItem>,
    contentStart: Dp,
    onPlayChannel: (ChannelEntity) -> Unit,
    onPlayCatchup: ((ChannelEntity, EpgProgrammeEntity) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeRowHeader(title = title, small = channels.size.toString(), start = contentStart)
        Spacer(Modifier.height(10.mpx))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            contentPadding = PaddingValues(start = contentStart, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(channels, key = { index, item -> item.channel.id * 1000L + index }) { _, item ->
                FavoritesHubChannelCardTv(
                    item = item,
                    onClick = { onPlayChannel(item.channel) },
                )
            }
        }
    }
}

@Composable
internal fun FavoritesHubChannelCardTv(
    item: FavoriteChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 340.mpx,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier.size(width, 142.mpx),
        focusStyle = StageFocus.FX,
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.mpx, vertical = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
        ) {
            ChannelPlate(item.channel, Modifier.size(76.mpx, 54.mpx))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.channel.name,
                            style = stageText(18, 700),
                            color = StageColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.hasCatchup) {
                            Spacer(Modifier.width(8.mpx))
                            ArchiveBadgeTv(catchupDays = item.catchupDays)
                        }
                    }

                    if (item.hasMultipleSources && !item.sourceName.isNullOrBlank()) {
                        Text(
                            item.sourceName,
                            style = stageText(12, 500),
                            color = a.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val prog = item.programme
                    if (prog != null) {
                        Spacer(Modifier.height(3.mpx))
                        Text(
                            prog.title,
                            style = stageText(14, 400),
                            color = StageColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val prog = item.programme
                if (prog != null) {
                    Column {
                        val leftText = timeLeftText(item.timeLeftMinutes)
                        Text(
                            leftText,
                            style = stageText(13, 400),
                            color = StageColors.Muted,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.mpx))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.mpx)
                                .clip(RoundedCornerShape(2.mpx))
                                .background(StageColors.Ink.copy(alpha = 0.12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(item.progress)
                                    .height(4.mpx)
                                    .clip(RoundedCornerShape(2.mpx))
                                    .background(Brush.horizontalGradient(listOf(a.accent, a.focus))),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArchiveBadgeTv(
    catchupDays: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.mpx))
            .background(StageColors.Ink.copy(alpha = 0.10f))
            .padding(horizontal = 6.mpx, vertical = 2.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.mpx),
    ) {
        OwnTVIcon(
            icon = OwnTVIcon.CLOCK,
            tint = StageColors.Muted,
            modifier = Modifier.size(11.mpx),
        )
        val text = if (catchupDays > 0) {
            stringResource(R.string.favorites_hub_archive_days, catchupDays)
        } else {
            stringResource(R.string.favorites_hub_archive)
        }
        Text(
            text = text,
            style = stageText(11, 600),
            color = StageColors.Muted,
        )
    }
}

@Composable
private fun FavoritesHubEmptyTv(
    onOpenLive: () -> Unit,
    contentStart: Dp,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = contentStart, end = 64.mpx, top = 64.mpx)
            .onFocusChanged { if (it.hasFocus) onFocused() },
        verticalArrangement = Arrangement.spacedBy(16.mpx),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.favorites_hub_title),
            style = stageText(36, 800),
            color = StageColors.Text,
        )
        Spacer(Modifier.height(20.mpx))
        Text(
            text = stringResource(R.string.favorites_hub_empty_title),
            style = stageText(24, 700),
            color = StageColors.Text,
        )
        Text(
            text = stringResource(R.string.favorites_hub_empty_tv),
            style = stageText(18, 400),
            color = StageColors.Muted,
        )
        Spacer(Modifier.height(16.mpx))
        StageButton(
            text = stringResource(R.string.favorites_hub_open_live),
            onClick = onOpenLive,
            icon = OwnTVIcon.LIVE_TV,
            style = StageButtonStyle.PRIMARY,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}
