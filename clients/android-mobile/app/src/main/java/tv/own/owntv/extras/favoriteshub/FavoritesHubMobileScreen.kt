// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.screens.home.timeLeftText
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.stageAccent

@Composable
fun FavoritesHubMobileScreen(
    scrollToTop: SharedFlow<String>,
    onPlayChannel: (ChannelEntity) -> Unit,
    onPlayCatchup: ((ChannelEntity, EpgProgrammeEntity) -> Unit)? = null,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FavoritesHubViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uncategorizedLabel = stringResource(R.string.favorites_hub_uncategorized)
    val listState = rememberLazyListState()

    listState.ObeyScrollToTop(route = tv.own.owntv.mobile.ui.nav.MobileDestination.FAVORITES.route, scrollToTop = scrollToTop)

    LaunchedEffect(Unit) {
        vm.refresh(uncategorizedLabel)
    }

    if (state.isEmpty) {
        FavoritesHubEmptyMobile(
            onOpenLive = onOpenLive,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            FavoritesHubHeaderMobile(
                filter = state.filter,
                onFilterChanged = { newFilter -> vm.setFilter(newFilter, uncategorizedLabel) },
            )
        }

        if (state.allFavorites.isNotEmpty()) {
            item {
                FavoritesHubChannelRowMobile(
                    title = stringResource(R.string.favorites_hub_row_all),
                    channels = state.allFavorites,
                    onPlayChannel = onPlayChannel,
                )
            }
        }

        itemsIndexed(items = state.categoryRows) { _, row ->
            FavoritesHubChannelRowMobile(
                title = row.categoryName,
                channels = row.channels,
                onPlayChannel = onPlayChannel,
            )
        }
    }
}

@Composable
private fun FavoritesHubHeaderMobile(
    filter: FavoritesHubFilter,
    onFilterChanged: (FavoritesHubFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.favorites_hub_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = palette.text,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val allActive = filter == FavoritesHubFilter.ALL_PLAYLISTS
            tv.own.owntv.mobile.ui.stage.StageSegmented(
                options = listOf(
                    stringResource(R.string.favorites_hub_filter_all),
                    stringResource(R.string.favorites_hub_filter_current),
                ),
                selected = if (allActive) 0 else 1,
                onSelect = {
                    onFilterChanged(if (it == 0) FavoritesHubFilter.ALL_PLAYLISTS else FavoritesHubFilter.CURRENT_ONLY)
                },
                fill = true,
            )
        }
    }
}

@Composable
private fun FavoritesHubChannelRowMobile(
    title: String,
    channels: List<FavoriteChannelItem>,
    onPlayChannel: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = channels.size.toString(),
                fontSize = 14.sp,
                color = palette.muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(channels, key = { index, item -> item.channel.id * 1000L + index }) { _, item ->
                FavoritesHubChannelCardMobile(
                    item = item,
                    onClick = { onPlayChannel(item.channel) },
                )
            }
        }
    }
}

@Composable
internal fun FavoritesHubChannelCardMobile(
    item: FavoriteChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 270.dp,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    Box(
        modifier = modifier
            .width(width)
            .height(116.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.glassOff)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelLogoImage(
                url = item.channel.displayLogoUrl,
                modifier = Modifier
                    .size(54.dp, 40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                fallback = {
                    Text(item.channel.name.take(1).uppercase(), color = LocalStagePalette.current.text)
                },
            )

            Column(
                modifier = Modifier
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
                            text = item.channel.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.hasCatchup) {
                            Spacer(Modifier.width(6.dp))
                            ArchiveBadgeMobile(catchupDays = item.catchupDays)
                        }
                    }

                    val sourceLabel = item.sourceName
                    if (item.hasMultipleSources && !sourceLabel.isNullOrBlank()) {
                        Text(
                            text = sourceLabel,
                            fontSize = 11.sp,
                            color = accent.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val prog = item.programme
                    if (prog != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = prog.title,
                            fontSize = 13.sp,
                            color = palette.muted,
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
                            text = leftText,
                            fontSize = 11.sp,
                            color = palette.muted,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.ink.copy(alpha = 0.15f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.progress)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Brush.horizontalGradient(listOf(accent.accent, accent.focus))),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArchiveBadgeMobile(
    catchupDays: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.ink.copy(alpha = 0.10f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        StageIcon(
            icon = MobileIcons.History,
            size = 10.dp,
            tint = palette.muted,
        )
        val text = if (catchupDays > 0) {
            stringResource(R.string.favorites_hub_archive_days, catchupDays)
        } else {
            stringResource(R.string.favorites_hub_archive)
        }
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = palette.muted,
        )
    }
}

@Composable
private fun FavoritesHubEmptyMobile(
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageIcon(
            icon = MobileIcons.Favorite,
            size = 48.dp,
            tint = palette.dim,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favorites_hub_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = palette.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.favorites_hub_empty_mobile),
            fontSize = 15.sp,
            color = palette.muted,
        )
        Spacer(Modifier.height(24.dp))
        StageButton(
            text = stringResource(R.string.favorites_hub_open_live),
            onClick = onOpenLive,
            icon = MobileIcons.LiveTv,
            style = StageButtonStyle.PRIMARY,
        )
    }
}
