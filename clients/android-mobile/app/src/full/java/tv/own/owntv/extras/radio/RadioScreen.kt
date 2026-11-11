// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.radio

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.radio.RadioGenres
import tv.own.owntv.radio.RadioMode
import tv.own.owntv.radio.RadioPlayer
import tv.own.owntv.radio.RadioStation
import tv.own.owntv.radio.RadioViewModel

/** The genre's name from the full-flavor strings (`radio_genre_<id>`), as in the sample's genre list. */
@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@Composable
internal fun genreName(id: String): String {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("radio_genre_$id", "string", context.packageName)
    return if (res != 0) stringResource(res) else id
}

/** Radio (APK / RuStore): genres as in YAiPlay Radio, the site's collections, favourites and search. */
@Composable
fun RadioScreen(onBack: () -> Unit, vm: RadioViewModel = viewModel()) {
    val palette = LocalStagePalette.current
    val mode by vm.mode.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val favorites by vm.repository.favorites.collectAsStateWithLifecycle()
    val current by vm.player.current.collectAsStateWithLifecycle()
    val status by vm.player.status.collectAsStateWithLifecycle()
    val nowTitle by vm.player.title.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)
    // Leaving the radio stops it: it must never play on under a channel.
    DisposableEffect(Unit) { onDispose { vm.player.stop() } }

    Column(Modifier.fillMaxSize().stageBackground().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            StagePill(onClick = onBack, icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.radio_back))
            Text(stringResource(R.string.radio_title), style = stageText(18f, 800), color = palette.text, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            StageSearchField(value = query, onValueChange = { query = it; vm.search(it) }, placeholder = stringResource(R.string.radio_search_hint))
            val tab = when (mode) {
                RadioMode.Collections -> 1
                RadioMode.Favorites -> 2
                else -> 0
            }
            StageSegmented(
                options = listOf(stringResource(R.string.radio_genres), stringResource(R.string.radio_collections), stringResource(R.string.radio_favorites)),
                selected = tab,
                onSelect = {
                    query = ""
                    vm.select(
                        when (it) {
                            1 -> RadioMode.Collections
                            2 -> RadioMode.Favorites
                            else -> RadioMode.Genre(RadioGenres.all.first())
                        },
                    )
                },
                fill = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            if (tab == 0 && mode !is RadioMode.Search) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    items(RadioGenres.all, key = { it.id }) { g ->
                        val on = (mode as? RadioMode.Genre)?.genre?.id == g.id
                        StageButton(onClick = { vm.select(RadioMode.Genre(g)) }, text = genreName(g.id), small = true,
                            style = if (on) StageButtonStyle.PRIMARY else StageButtonStyle.SECONDARY)
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                failed && stations.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.radio_failed), style = stageText(14f, 600), color = palette.muted)
                    StageButton(onClick = vm::retry, text = stringResource(R.string.radio_retry), modifier = Modifier.padding(top = 10.dp))
                }
                !loading && stations.isEmpty() -> Text(
                    stringResource(if (mode == RadioMode.Favorites) R.string.radio_favorites_empty else R.string.radio_empty),
                    style = stageText(14f, 600), color = palette.muted, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(stations, key = { it.id }) { s ->
                        StationRow(s, playing = current?.id == s.id, favorite = favorites.any { it.id == s.id },
                            onPlay = { vm.player.play(s) }, onFavorite = { vm.toggleFavorite(s) })
                    }
                }
            }
        }
        current?.let { s -> NowPlaying(s, status, nowTitle, onToggle = vm.player::toggle, onStop = vm.player::stop) }
    }
}

@Composable
private fun StationRow(s: RadioStation, playing: Boolean, favorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = s.favicon, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.name, style = stageText(14f, if (playing) 800 else 600), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(
                s.tags.take(3).joinToString(", ").ifBlank { null },
                s.country,
                s.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.radio_kbps, it) },
            ).joinToString(" · ")
            Text(line, style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StagePill(
            onClick = onFavorite,
            icon = if (favorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.radio_fav_remove else R.string.radio_fav_add),
        )
    }
}

@Composable
private fun NowPlaying(s: RadioStation, status: RadioPlayer.Status, title: String?, onToggle: () -> Unit, onStop: () -> Unit) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = stageText(14f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when (status) {
                RadioPlayer.Status.BUFFERING -> stringResource(R.string.radio_buffering)
                RadioPlayer.Status.ERROR -> stringResource(R.string.radio_error)
                else -> title
            }
            line?.let {
                Text(it, style = stageText(12f, 500), color = if (status == RadioPlayer.Status.ERROR) palette.danger else palette.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StagePill(
            onClick = onToggle,
            icon = if (status == RadioPlayer.Status.PLAYING) MobileIcons.Pause else MobileIcons.PlayArrow,
            contentDescription = stringResource(R.string.radio_play_pause),
        )
        StagePill(onClick = onStop, icon = MobileIcons.Close, contentDescription = stringResource(R.string.radio_stop), modifier = Modifier.padding(start = 8.dp))
    }
}
