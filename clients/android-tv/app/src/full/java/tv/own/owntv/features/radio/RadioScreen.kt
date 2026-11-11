// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.radio.RadioGenres
import tv.own.owntv.radio.RadioMode
import tv.own.owntv.radio.RadioPlayer
import tv.own.owntv.radio.RadioViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@Composable
private fun genreName(id: String): String {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("radio_genre_$id", "string", context.packageName)
    return if (res != 0) stringResource(res) else id
}

/**
 * Radio on the TV (APK / RuStore build): genres, collections and favourites on the left, stations as tiles on the
 * right, what plays on top. Back leaves the radio and stops it.
 */
@Composable
fun RadioScreen(onBack: () -> Unit, vm: RadioViewModel = viewModel()) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val favorites by vm.repository.favorites.collectAsStateWithLifecycle()
    val current by vm.player.current.collectAsStateWithLifecycle()
    val status by vm.player.status.collectAsStateWithLifecycle()
    val nowTitle by vm.player.title.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { onDispose { vm.player.stop() } }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page).padding(start = 64.mpx, top = 48.mpx, end = 48.mpx)) {
        LazyColumn(
            Modifier.width(380.mpx).fillMaxSize().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.mpx),
            contentPadding = PaddingValues(bottom = 40.mpx),
        ) {
            item {
                Text(stringResource(R.string.radio_title), style = stageText(40, 800), color = StageColors.Text,
                    modifier = Modifier.padding(bottom = 18.mpx))
            }
            item {
                StageMenuItem(text = stringResource(R.string.radio_favorites), icon = OwnTVIcon.FAVORITE,
                    checked = mode == RadioMode.Favorites, onClick = { vm.select(RadioMode.Favorites) })
            }
            item {
                StageMenuItem(text = stringResource(R.string.radio_collections), icon = OwnTVIcon.LIST,
                    checked = mode == RadioMode.Collections, onClick = { vm.select(RadioMode.Collections) })
            }
            item {
                StageMenuItem(text = stringResource(R.string.radio_account), icon = OwnTVIcon.PERSON,
                    checked = mode == RadioMode.Account, onClick = { vm.select(RadioMode.Account) })
            }
            items(RadioGenres.all, key = { it.id }) { g ->
                StageMenuItem(
                    text = genreName(g.id),
                    checked = (mode as? RadioMode.Genre)?.genre?.id == g.id,
                    onClick = { vm.select(RadioMode.Genre(g)) },
                    modifier = if (g == RadioGenres.all.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        Column(Modifier.padding(start = 40.mpx).fillMaxSize()) {
            current?.let { s ->
                Row(Modifier.fillMaxWidth().padding(bottom = 20.mpx).focusGroup(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = stageText(28, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val line = when (status) {
                            RadioPlayer.Status.BUFFERING -> stringResource(R.string.radio_buffering)
                            RadioPlayer.Status.ERROR -> stringResource(R.string.radio_error)
                            else -> nowTitle
                        }
                        line?.let {
                            Text(it, style = stageText(20, 500), color = if (status == RadioPlayer.Status.ERROR) StageColors.Danger else StageColors.Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    StageButton(text = null, onClick = vm.player::toggle, round = true, height = 60.mpx,
                        icon = if (status == RadioPlayer.Status.PLAYING) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, iconFilled = true)
                    StageButton(text = stringResource(R.string.radio_stop), onClick = vm.player::stop, height = 60.mpx, textSize = 20,
                        modifier = Modifier.padding(start = 12.mpx))
                    StageButton(
                        text = null, round = true, height = 60.mpx, icon = OwnTVIcon.FAVORITE,
                        iconFilled = favorites.any { it.id == s.id }, onClick = { vm.toggleFavorite(s) },
                        modifier = Modifier.padding(start = 12.mpx),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    mode == RadioMode.Account -> RadioAccountPanel(vm)
                    loading -> OwnTVSpinner(sizeDp = 48, modifier = Modifier.align(Alignment.Center))
                    failed && stations.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.radio_failed), style = stageText(22, 600), color = StageColors.Muted)
                        StageButton(text = stringResource(R.string.radio_retry), onClick = vm::retry, modifier = Modifier.padding(top = 16.mpx))
                    }
                    stations.isEmpty() -> Text(
                        stringResource(if (mode == RadioMode.Favorites) R.string.radio_favorites_empty else R.string.radio_empty),
                        style = stageText(22, 600), color = StageColors.Muted, modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(300.mpx),
                        horizontalArrangement = Arrangement.spacedBy(18.mpx),
                        verticalArrangement = Arrangement.spacedBy(18.mpx),
                        contentPadding = PaddingValues(top = 8.mpx, bottom = 48.mpx),
                        modifier = Modifier.fillMaxSize().focusGroup(),
                    ) {
                        items(stations, key = { it.id }) { s ->
                            val playing = current?.id == s.id
                            StageSurface(
                                onClick = { vm.player.play(s) },
                                onLongClick = { vm.toggleFavorite(s) },
                                radius = 20.mpx,
                                highlighted = playing,
                                modifier = Modifier.fillMaxWidth().height(116.mpx),
                            ) {
                                Row(Modifier.padding(horizontal = 16.mpx), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = s.favicon, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.mpx).clip(RoundedCornerShape(14.mpx)),
                                    )
                                    Column(Modifier.padding(start = 14.mpx)) {
                                        Text(s.name, style = stageText(21, 700), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val line = listOfNotNull(
                                            s.tags.take(2).joinToString(", ").ifBlank { null },
                                            s.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.radio_kbps, it) },
                                        ).joinToString(" · ")
                                        Text(line, style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
