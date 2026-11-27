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
    // Back from full screen: put the cursor on the card that was played (owner 26.11 — it landed on the
    // side menu's heart and the channel had to be found again by hand).
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    vm: FavoritesHubViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uncategorizedLabel = stringResource(R.string.favorites_hub_uncategorized)
    val firstItemFocus = remember { FocusRequester() }

    val returnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        onEntryHook?.invoke {
            // Entering from the side menu: the last played card first, the header pills otherwise.
            (vm.lastPlayed != null && runCatching { returnFocus.requestFocus() }.getOrDefault(false)) ||
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

    // Back from full screen: the card that was played, not the side menu (owner 15.11, again 26.11).
    // Read live — the screen stays composed under the player, so a value remembered on first
    // composition was the one from BEFORE the channel was played.
    val columnState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val returnTo = vm.lastPlayed
    val grid = vm.gridView
    val hasAll = state.allFavorites.isNotEmpty()
    LaunchedEffect(restoreFocus, returnTo, grid, state.allFavorites.size, state.categoryRows.size) {
        val (row, id) = returnTo ?: run { if (restoreFocus) onRestored(); return@LaunchedEffect }
        if (state.allFavorites.isEmpty() && state.categoryRows.isEmpty()) return@LaunchedEffect
        if (grid) {
            gridIndexOf(gridSections(state.allFavorites, state.categoryRows.map { it.categoryName to it.channels }), row, id)
                ?.let { runCatching { gridState.scrollToItem(it) } }
        } else {
            val item = 1 + if (hasAll) row else row - 1 // the header is item 0
            runCatching { columnState.scrollToItem(item.coerceAtLeast(0)) }
        }
        repeat(RETURN_FOCUS_FRAMES) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { returnFocus.requestFocus() }.getOrDefault(false)) { onRestored(); return@LaunchedEffect }
        }
        onRestored()
    }
    val play: (Int, ChannelEntity) -> Unit = { row, ch -> vm.lastPlayed = row to ch.id; onPlayChannel(ch) }

    if (grid && (hasAll || state.categoryRows.isNotEmpty())) {
        FavoritesHubGridTv(
            state = gridState,
            sections = gridSections(state.allFavorites, state.categoryRows.map { it.categoryName to it.channels }),
            contentStart = contentStart,
            returnTo = returnTo,
            returnFocus = returnFocus,
            onPlay = play,
            header = {
                FavoritesHubHeaderTv(
                    filter = state.filter,
                    onFilterChanged = { newFilter -> vm.setFilter(newFilter, uncategorizedLabel) },
                    contentStart = 0.mpx,
                    firstFocusRequester = firstItemFocus,
                    grid = true,
                    onToggleGrid = { vm.gridView = false },
                )
            },
            modifier = modifier.onFocusChanged { if (it.hasFocus) onChildFocused() },
        )
        return
    }

    LazyColumn(
        state = columnState,
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
        // Below the shell's status pills and clock (≈34 + 70 px), which this header ran into.
        contentPadding = PaddingValues(top = 120.mpx, bottom = 64.mpx),
        verticalArrangement = Arrangement.spacedBy(24.mpx),
    ) {
        // Header with title and Playlist filter pills
        item {
            FavoritesHubHeaderTv(
                filter = state.filter,
                onFilterChanged = { newFilter -> vm.setFilter(newFilter, uncategorizedLabel) },
                contentStart = contentStart,
                firstFocusRequester = firstItemFocus,
                grid = false,
                onToggleGrid = { vm.gridView = true },
            )
        }

        // Top row: All favorites
        if (state.allFavorites.isNotEmpty()) {
            item {
                FavoritesHubChannelRowTv(
                    title = stringResource(R.string.favorites_hub_row_all),
                    channels = state.allFavorites,
                    contentStart = contentStart,
                    onPlayChannel = { play(0, it) },
                    onPlayCatchup = onPlayCatchup,
                    returnChannelId = returnTo?.takeIf { it.first == 0 }?.second,
                    returnFocus = returnFocus,
                )
            }
        }

        // Category rows below
        itemsIndexed(items = state.categoryRows) { i, row ->
            FavoritesHubChannelRowTv(
                title = row.categoryName,
                channels = row.channels,
                contentStart = contentStart,
                onPlayChannel = { play(i + 1, it) },
                onPlayCatchup = onPlayCatchup,
                returnChannelId = returnTo?.takeIf { it.first == i + 1 }?.second,
                returnFocus = returnFocus,
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
    grid: Boolean = false,
    onToggleGrid: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = contentStart, end = 64.mpx),
    ) {
        // Filters right after the title, not pushed to the right edge: there they sat under the shell's
        // «Last channel» and playlist pills and the clock (owner 14.11, photo).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.mpx),
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
                    text = stringResource(R.string.favorites_hub_filter_all),
                    icon = if (allActive) OwnTVIcon.CHECK else OwnTVIcon.LAYERS,
                    modifier = Modifier.focusRequester(firstFocusRequester),
                )
                StagePill(
                    onClick = { onFilterChanged(FavoritesHubFilter.CURRENT_ONLY) },
                    text = stringResource(R.string.favorites_hub_filter_current),
                    icon = if (!allActive) OwnTVIcon.CHECK else OwnTVIcon.PLAYLIST,
                )
                if (onToggleGrid != null) {
                    Spacer(Modifier.width(18.mpx))
                    // Tiles across the screen ⇄ horizontal rows by category (owner 26.11).
                    StagePill(
                        onClick = onToggleGrid,
                        text = stringResource(if (grid) R.string.favorites_hub_view_rows else R.string.favorites_hub_view_grid),
                        icon = if (grid) OwnTVIcon.LIST else OwnTVIcon.GRID,
                    )
                }
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
    returnChannelId: Long? = null,
    returnFocus: FocusRequester? = null,
) {
    val returnIndex = returnChannelId?.let { id -> channels.indexOfFirst { it.channel.id == id } }?.takeIf { it >= 0 }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = returnIndex ?: 0)
    Column(modifier = modifier.fillMaxWidth()) {
        HomeRowHeader(title = title, small = channels.size.toString(), start = contentStart)
        Spacer(Modifier.height(10.mpx))
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            contentPadding = PaddingValues(start = contentStart, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(channels, key = { index, item -> item.channel.id * 1000L + index }) { index, item ->
                FavoritesHubChannelCardTv(
                    item = item,
                    onClick = { onPlayChannel(item.channel) },
                    modifier = if (index == returnIndex && returnFocus != null) Modifier.focusRequester(returnFocus) else Modifier,
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

                    val sourceLabel = item.sourceName
                if (item.hasMultipleSources && !sourceLabel.isNullOrBlank()) {
                        Text(
                            sourceLabel,
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
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

/**
 * Every favourite as a tile, the whole screen filled and scrolled up and down (owner 26.11): a big
 * logo, the name, what is on now and how much of it is left.
 */
/** One titled block of tiles: its row number (as `lastPlayed` counts rows) and its channels. */
private data class GridSection(val row: Int, val title: String?, val channels: List<FavoriteChannelItem>)

/**
 * Tiles split by category, each block under its own title (owner 26.11: «сразу разбито на категории»).
 * Favourites without categories fall back to one untitled block of everything.
 */
private fun gridSections(all: List<FavoriteChannelItem>, rows: List<Pair<String, List<FavoriteChannelItem>>>): List<GridSection> =
    if (rows.isEmpty()) listOf(GridSection(0, null, all))
    else rows.mapIndexed { i, (title, channels) -> GridSection(i + 1, title, channels) }

/** The grid item index of the tile [id] in block [row] (falls back to the first tile with that id). */
private fun gridIndexOf(sections: List<GridSection>, row: Int, id: Long): Int? {
    fun find(match: (GridSection) -> Boolean): Int? {
        var index = 1 // the page header
        for (section in sections) {
            if (section.title != null) index++
            val at = section.channels.indexOfFirst { it.channel.id == id }
            if (at >= 0 && match(section)) return index + at
            index += section.channels.size
        }
        return null
    }
    return find { it.row == row } ?: find { true }
}

/**
 * Every favourite as a tile, the whole screen filled and scrolled up and down (owner 26.11): a big
 * logo, the name, what is on now and how much of it is left — grouped under category titles.
 */
@Composable
private fun FavoritesHubGridTv(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    sections: List<GridSection>,
    contentStart: Dp,
    returnTo: Pair<Int, Long>?,
    returnFocus: FocusRequester,
    onPlay: (Int, ChannelEntity) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The tile the cursor goes back to: in its own block if it is there, else its first appearance.
    val returnIndex = returnTo?.let { gridIndexOf(sections, it.first, it.second) }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { androidx.compose.foundation.lazy.grid.GridItemSpan(it.maxLineSpan) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(GRID_COLUMNS),
        state = state,
        modifier = modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(start = contentStart, end = 64.mpx, top = 120.mpx, bottom = 64.mpx),
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        item(key = Long.MIN_VALUE, span = { full(this) }) {
            Column {
                header()
                Spacer(Modifier.height(8.mpx))
            }
        }
        var index = 1
        for (section in sections) {
            val title = section.title
            if (title != null) {
                index++
                item(key = -section.row.toLong() - 1, span = { full(this) }) {
                    HomeRowHeader(title = title, small = section.channels.size.toString(), start = 0.mpx)
                }
            }
            val first = index
            items(section.channels.size, key = { section.row * GRID_KEY_STRIDE + section.channels[it].channel.id }) { i ->
                val item = section.channels[i]
                FavoritesHubTileTv(
                    item = item,
                    onClick = { onPlay(section.row, item.channel) },
                    modifier = if (first + i == returnIndex) Modifier.focusRequester(returnFocus) else Modifier,
                )
            }
            index += section.channels.size
        }
    }
}

@Composable
private fun FavoritesHubTileTv(
    item: FavoriteChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier.fillMaxWidth().height(230.mpx),
        focusStyle = StageFocus.FX,
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxSize().padding(16.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelPlate(item.channel, Modifier.size(132.mpx, 92.mpx))
                Spacer(Modifier.weight(1f))
                if (item.hasCatchup) ArchiveBadgeTv(catchupDays = item.catchupDays)
            }
            Spacer(Modifier.height(12.mpx))
            Text(
                item.channel.name,
                style = stageText(19, 700),
                color = StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val prog = item.programme
            if (prog != null) {
                Spacer(Modifier.height(4.mpx))
                Text(
                    prog.title,
                    style = stageText(15, 400),
                    color = StageColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                )
                Text(timeLeftText(item.timeLeftMinutes), style = stageText(13, 400), color = StageColors.Muted, maxLines = 1)
                Spacer(Modifier.height(5.mpx))
                Box(
                    Modifier.fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx))
                        .background(StageColors.Ink.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(item.progress).height(4.mpx).clip(RoundedCornerShape(2.mpx))
                            .background(Brush.horizontalGradient(listOf(a.accent, a.focus))),
                    )
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 4
private const val GRID_KEY_STRIDE = 10_000_000_000L
private const val RETURN_FOCUS_FRAMES = 30
