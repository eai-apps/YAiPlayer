// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.radio.RadioGenres
import tv.own.owntv.radio.RadioLook
import tv.own.owntv.radio.RadioMode
import tv.own.owntv.radio.RadioPlayer
import tv.own.owntv.radio.RadioStation
import tv.own.owntv.radio.RadioViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@Composable
internal fun genreName(id: String): String {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("radio_genre_$id", "string", context.packageName)
    return if (res != 0) stringResource(res) else id
}

internal fun gradientOf(colors: LongArray): Brush = Brush.linearGradient(colors.map { Color(it) })

private enum class RadioView { HOME, LIST, ACCOUNT }

/**
 * Radio on the TV, after the YAiPlay Radio site (owner 12.11): coloured genre tiles; a genre opens its stations; a
 * mini player at the bottom (not buttons in the top corner) that opens a full player with the station's art and a
 * light equalizer. The page sits on the player's own background. Back: full player → genres → out (radio stops).
 */
@Composable
fun RadioScreen(onBack: () -> Unit, vm: RadioViewModel = viewModel()) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val favorites by vm.repository.favorites.collectAsStateWithLifecycle()
    val current by vm.player.current.collectAsStateWithLifecycle()
    val status by vm.player.status.collectAsStateWithLifecycle()
    val cover by vm.player.cover.collectAsStateWithLifecycle()
    val nowTitle by vm.player.title.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(RadioView.HOME) }
    var heading by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val homeFirst = remember { FocusRequester() }
    val listFirst = remember { FocusRequester() }

    BackHandler {
        when {
            expanded -> expanded = false
            view != RadioView.HOME -> { view = RadioView.HOME; heading = null }
            else -> onBack()
        }
    }
    DisposableEffect(Unit) { onDispose { vm.player.stop() } }
    LaunchedEffect(view, loading) {
        kotlinx.coroutines.delay(60)
        runCatching { if (view == RadioView.HOME) homeFirst.requestFocus() else if (!loading) listFirst.requestFocus() }
    }

    fun open(m: RadioMode, title: String, account: Boolean = false) {
        vm.select(m)
        heading = title
        view = if (account) RadioView.ACCOUNT else RadioView.LIST
    }

    Box(
        Modifier.fillMaxSize()
            // The remote's own Play / Pause keys control the radio (before, only the on-screen button did).
            .onPreviewKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || current == null) return@onPreviewKeyEvent false
                when (e.key) {
                    androidx.compose.ui.input.key.Key.MediaPlayPause -> { vm.player.toggle(); true }
                    androidx.compose.ui.input.key.Key.MediaPlay -> { vm.player.setPlaying(true); true }
                    androidx.compose.ui.input.key.Key.MediaPause -> { vm.player.setPlaying(false); true }
                    else -> false
                }
            }
            .background(tv.own.owntv.ui.theme.LocalStagePalette.current.page)
            .stageBackground(stageAccent.accent),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 64.mpx, top = 40.mpx, end = 56.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 22.mpx)) {
                tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.EQ, tint = stageAccent.accent, modifier = Modifier.size(40.mpx))
                Text(
                    heading ?: stringResource(R.string.radio_title),
                    style = stageText(40, 800), color = StageColors.Text,
                    modifier = Modifier.padding(start = 16.mpx),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view) {
                    RadioView.HOME -> HomeGrid(
                        first = homeFirst,
                        onFavorites = { t -> open(RadioMode.Favorites, t) },
                        onCollections = { t -> open(RadioMode.Collections, t) },
                        onAccount = { t -> open(RadioMode.Account, t, account = true) },
                        onGenre = { g, t -> open(RadioMode.Genre(g), t) },
                    )
                    RadioView.ACCOUNT -> RadioAccountPanel(vm)
                    RadioView.LIST -> when {
                        loading -> OwnTVSpinner(sizeDp = 48, modifier = Modifier.align(Alignment.Center))
                        failed && stations.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.radio_failed), style = stageText(22, 600), color = StageColors.Muted)
                            StageButton(text = stringResource(R.string.radio_retry), onClick = vm::retry,
                                modifier = Modifier.padding(top = 16.mpx).focusRequester(listFirst))
                        }
                        stations.isEmpty() -> Text(
                            stringResource(if (mode == RadioMode.Favorites) R.string.radio_favorites_empty else R.string.radio_empty),
                            style = stageText(22, 600), color = StageColors.Muted, modifier = Modifier.align(Alignment.Center),
                        )
                        else -> StationGrid(stations, current?.id, listFirst, onPlay = vm.player::play, onFavorite = vm::toggleFavorite)
                    }
                }
            }
            current?.let { s ->
                MiniPlayer(
                    station = s, status = status, title = nowTitle, cover = cover,
                    favorite = favorites.any { it.id == s.id },
                    onToggle = vm.player::toggle, onStop = vm.player::stop,
                    onFavorite = { vm.toggleFavorite(s) }, onExpand = { expanded = true },
                )
            }
        }
        val playing = current
        if (expanded && playing != null) {
            FullPlayer(
                station = playing, status = status, title = nowTitle, cover = cover,
                favorite = favorites.any { it.id == playing.id },
                onToggle = vm.player::toggle, onStop = { vm.player.stop(); expanded = false },
                onFavorite = { vm.toggleFavorite(playing) },
            )
        }
    }
}

@Composable
private fun HomeGrid(
    first: FocusRequester,
    onFavorites: (String) -> Unit,
    onCollections: (String) -> Unit,
    onAccount: (String) -> Unit,
    onGenre: (tv.own.owntv.radio.RadioGenre, String) -> Unit,
) {
    val favTitle = stringResource(R.string.radio_favorites)
    val colTitle = stringResource(R.string.radio_collections)
    val accTitle = stringResource(R.string.radio_account)
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
        contentPadding = PaddingValues(top = 6.mpx, bottom = 40.mpx, start = 6.mpx, end = 6.mpx),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        item { GenreTile(favTitle, longArrayOf(0xFFDB2777, 0xFF9333EA, 0xFF0891B2), OwnTVIcon.FAVORITE, Modifier.focusRequester(first)) { onFavorites(favTitle) } }
        item { GenreTile(colTitle, longArrayOf(0xFF0E7490, 0xFF2563EB, 0xFF6D28D9), OwnTVIcon.LIST) { onCollections(colTitle) } }
        item { GenreTile(accTitle, longArrayOf(0xFF1E293B, 0xFF0F766E, 0xFF4C1D95), OwnTVIcon.PERSON) { onAccount(accTitle) } }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(1.mpx)) }
        items(RadioGenres.all, key = { it.id }) { g ->
            val name = genreName(g.id)
            GenreTile(name, RadioLook.colorsOf(g.id)) { onGenre(g, name) }
        }
    }
}

@Composable
private fun GenreTile(name: String, colors: LongArray, icon: OwnTVIcon? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val r = 22.mpx
    StageSurface(
        onClick = onClick, radius = r, focusStyle = StageFocus.POSTER,
        modifier = modifier.fillMaxWidth().height(150.mpx),
    ) { _ ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(r)).background(gradientOf(colors))) {
            icon?.let {
                tv.own.owntv.ui.components.OwnTVIcon(it, tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.mpx).size(40.mpx))
            }
            Text(
                name, style = stageText(24, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.mpx),
            )
        }
    }
}

@Composable
private fun StationGrid(
    stations: List<RadioStation>,
    playingId: String?,
    first: FocusRequester,
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(18.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx),
        contentPadding = PaddingValues(top = 6.mpx, bottom = 40.mpx, start = 6.mpx, end = 6.mpx),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        items(stations, key = { it.id }) { s ->
            StageSurface(
                onClick = { onPlay(s) },
                onLongClick = { onFavorite(s) },
                radius = 20.mpx,
                highlighted = playingId == s.id,
                modifier = Modifier.fillMaxWidth().height(112.mpx)
                    .then(if (s == stations.first()) Modifier.focusRequester(first) else Modifier),
            ) { _ ->
                Row(Modifier.padding(horizontal = 14.mpx), verticalAlignment = Alignment.CenterVertically) {
                    StationArt(s, 76.mpx, letter = 32)
                    Column(Modifier.padding(start = 14.mpx)) {
                        Text(s.name, style = stageText(20, 700), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        stationLine(s)?.let {
                            Text(it, style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Country and bitrate. The station's raw tags (`kannadahits` and the like) are never shown (owner 12.11). */
@Composable
internal fun stationLine(s: RadioStation): String? = listOfNotNull(
    s.country?.takeIf { it.isNotBlank() },
    s.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.radio_kbps, it) },
).joinToString(" · ").ifBlank { null }

/** The station's logo over a placeholder: its first letter on a gradient, which shows when there is no logo. */
@Composable
private fun StationArt(s: RadioStation, size: Dp, letter: Int, radius: Dp = 16.mpx, cover: String? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(gradientOf(RadioLook.placeholderColors(s.name))),
        contentAlignment = Alignment.Center,
    ) {
        Text(RadioLook.initial(s.name), style = stageText(letter, 800), color = Color.White)
        if (!cover.isNullOrBlank() || !s.favicon.isNullOrBlank()) {
            AsyncImage(model = cover ?: s.favicon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun statusLine(status: RadioPlayer.Status, title: String?): String? = when (status) {
    RadioPlayer.Status.BUFFERING -> stringResource(R.string.radio_buffering)
    RadioPlayer.Status.ERROR -> stringResource(R.string.radio_error)
    else -> title
}

@Composable
private fun MiniPlayer(
    station: RadioStation,
    status: RadioPlayer.Status,
    title: String?,
    cover: String?,
    favorite: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onFavorite: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 28.mpx, top = 8.mpx).height(112.mpx)
            .stageGlass(26.mpx, overContent = true).padding(horizontal = 18.mpx).focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(station, 80.mpx, letter = 34, cover = cover)
        Column(Modifier.weight(1f).padding(horizontal = 18.mpx)) {
            Text(station.name, style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            statusLine(status, title)?.let {
                Text(it, style = stageText(18, 500), color = if (status == RadioPlayer.Status.ERROR) StageColors.Danger else StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (status == RadioPlayer.Status.PLAYING) Equalizer(Modifier.padding(end = 18.mpx).height(40.mpx).width(56.mpx))
        StageButton(text = null, onClick = onToggle, round = true, height = 64.mpx,
            icon = if (status == RadioPlayer.Status.PLAYING || status == RadioPlayer.Status.BUFFERING) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, iconFilled = true)
        StageButton(text = null, onClick = onFavorite, round = true, height = 64.mpx, icon = OwnTVIcon.FAVORITE, iconFilled = favorite,
            modifier = Modifier.padding(start = 12.mpx))
        StageButton(text = null, onClick = onStop, round = true, height = 64.mpx, icon = OwnTVIcon.CLOSE,
            modifier = Modifier.padding(start = 12.mpx))
        StageButton(text = null, onClick = onExpand, round = true, height = 64.mpx, icon = OwnTVIcon.EXPAND,
            modifier = Modifier.padding(start = 12.mpx))
    }
}

@Composable
private fun FullPlayer(
    station: RadioStation,
    status: RadioPlayer.Status,
    title: String?,
    cover: String?,
    favorite: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onFavorite: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val colors = RadioLook.placeholderColors(station.name)
    Box(
        Modifier.fillMaxSize()
            .background(tv.own.owntv.ui.theme.LocalStagePalette.current.page)
            .background(Brush.verticalGradient(listOf(Color(colors[0]).copy(alpha = 0.55f), Color.Transparent))),
    ) {
        Row(Modifier.fillMaxSize().padding(80.mpx), verticalAlignment = Alignment.CenterVertically) {
            StationArt(station, 420.mpx, letter = 170, radius = 36.mpx, cover = cover)
            Column(Modifier.padding(start = 64.mpx).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(station.name, style = stageText(52, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                statusLine(status, title)?.let {
                    Text(it, style = stageText(28, 600), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.mpx))
                }
                stationLine(station)?.let { Text(it, style = stageText(20, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx)) }
                Equalizer(Modifier.padding(top = 36.mpx).height(90.mpx).width(220.mpx), playing = status == RadioPlayer.Status.PLAYING)
                Row(Modifier.padding(top = 40.mpx).focusGroup()) {
                    StageButton(text = null, onClick = onToggle, round = true, height = 96.mpx,
                        icon = if (status == RadioPlayer.Status.PLAYING || status == RadioPlayer.Status.BUFFERING) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, iconFilled = true,
                        modifier = Modifier.focusRequester(first))
                    StageButton(text = null, onClick = onFavorite, round = true, height = 96.mpx, icon = OwnTVIcon.FAVORITE,
                        iconFilled = favorite, modifier = Modifier.padding(start = 20.mpx))
                    StageButton(text = stringResource(R.string.radio_stop), onClick = onStop, height = 96.mpx, textSize = 24,
                        modifier = Modifier.padding(start = 20.mpx))
                }
            }
        }
    }
}

/** Five bars that bounce while the station plays; still bars when animations are off (light mode) or paused. */
@Composable
private fun Equalizer(modifier: Modifier, playing: Boolean = true) {
    val accent = stageAccent.accent
    val animate = animationsOn && playing
    val transition = rememberInfiniteTransition(label = "radioEq")
    val phases = listOf(520, 680, 430, 610, 750)
    val levels = phases.mapIndexed { i, ms ->
        if (animate) transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "eq$i",
        ).value else listOf(0.5f, 0.8f, 0.4f, 0.7f, 0.55f)[i]
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.mpx), verticalAlignment = Alignment.Bottom) {
        levels.forEach { level ->
            Box(Modifier.weight(1f).fillMaxHeight(level).clip(RoundedCornerShape(4.mpx)).background(accent))
        }
    }
}
