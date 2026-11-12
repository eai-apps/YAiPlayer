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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.blur
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

/** One line under the genre's name on its tile (as on the site); null when there is none. */
@SuppressLint("DiscouragedApi")
@Composable
internal fun genreDescription(id: String): String? {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("radio_genre_${id}_desc", "string", context.packageName)
    return if (res != 0) stringResource(res) else null
}

/** The genre's glyph in the TV's own icon set. */
internal fun glyphIcon(g: RadioLook.Glyph): OwnTVIcon = when (g) {
    RadioLook.Glyph.TOP -> OwnTVIcon.TREND
    RadioLook.Glyph.SPARKLE -> OwnTVIcon.SPARKLE
    RadioLook.Glyph.GUITAR -> OwnTVIcon.AUDIO
    RadioLook.Glyph.RETRO -> OwnTVIcon.HISTORY
    RadioLook.Glyph.BEAT -> OwnTVIcon.EQ
    RadioLook.Glyph.MIC -> OwnTVIcon.MIC
    RadioLook.Glyph.NOTE -> OwnTVIcon.HEADPHONES
    RadioLook.Glyph.CALM -> OwnTVIcon.BEDTIME
    RadioLook.Glyph.SUN -> OwnTVIcon.SUN
    RadioLook.Glyph.FILM -> OwnTVIcon.MOVIES
    RadioLook.Glyph.HEART -> OwnTVIcon.FAVORITE
    RadioLook.Glyph.SPORT -> OwnTVIcon.SPORTS
    RadioLook.Glyph.TALK -> OwnTVIcon.PERSON
    RadioLook.Glyph.NEWS -> OwnTVIcon.INFO
}

internal fun gradientOf(colors: LongArray): Brush = Brush.linearGradient(colors.map { Color(it) })

private enum class RadioView { HOME, LIST, ACCOUNT }

/**
 * Radio on the TV, after the YAiPlay Radio site (owner 12.11): coloured genre tiles; a genre opens its stations; a
 * mini player at the bottom (not buttons in the top corner) that opens a full player with the station's art and a
 * light equalizer. The page sits on the player's own background. Back: full player → genres → out (the radio plays on).
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
    val context = LocalContext.current
    val luckyFailed = stringResource(R.string.radio_lucky_failed)

    BackHandler {
        when {
            expanded -> expanded = false
            view != RadioView.HOME -> { view = RadioView.HOME; heading = null }
            else -> onBack()
        }
    }
    // TV: the radio plays only while its screen is open (owner 12.11: it must never play on in the background).
    // Leaving the screen, the Home button or the TV going to sleep stops it.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        tv.own.owntv.radio.RadioPlayer.backgroundService = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.player.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.player.stop()
        }
    }
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
                if (e.type != KeyEventType.KeyDown || current == null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause -> { vm.player.toggle(); true }
                    Key.MediaPlay -> { vm.player.setPlaying(true); true }
                    Key.MediaPause -> { vm.player.setPlaying(false); true }
                    Key.MediaStop -> { vm.player.stop(); expanded = false; true }
                    else -> false
                }
            }
            .background(tv.own.owntv.ui.theme.LocalStagePalette.current.page)
            .stageBackground(stageAccent.accent),
    ) {
        val playingNow = current
        if (expanded && playingNow != null) {
            // Alone on screen, so the D-pad cannot wander into the grid behind it.
            FullPlayer(
                station = playingNow, status = status, title = nowTitle, cover = cover,
                favorite = favorites.any { it.id == playingNow.id },
                onToggle = vm.player::toggle, onStop = { vm.player.stop(); expanded = false },
                onFavorite = { vm.toggleFavorite(playingNow) },
            )
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(start = 64.mpx, top = 40.mpx, end = 56.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 22.mpx)) {
                tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.EQ, tint = stageAccent.accent, modifier = Modifier.size(40.mpx))
                Text(
                    heading ?: stringResource(R.string.radio_title),
                    style = stageText(40, 800), color = StageColors.Text,
                    modifier = Modifier.padding(start = 16.mpx),
                )
                Spacer(Modifier.weight(1f))
                // What plays now, with its buttons at the TOP: from the first row of tiles «up» reaches them
                // (at the bottom the grid kept the focus and the buttons could not be reached, owner 12.11).
                current?.let { st ->
                    NowPlaying(
                        station = st, status = status, title = nowTitle, cover = cover,
                        favorite = favorites.any { it.id == st.id },
                        onToggle = vm.player::toggle, onStop = vm.player::stop,
                        onFavorite = { vm.toggleFavorite(st) }, onExpand = { expanded = true },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view) {
                    RadioView.HOME -> HomeGrid(
                        first = homeFirst,
                        onLucky = {
                            vm.lucky { ok ->
                                if (!ok) android.widget.Toast.makeText(context, luckyFailed, android.widget.Toast.LENGTH_LONG).show()
                                else expanded = true
                            }
                        },
                        onTrending = { t -> open(RadioMode.Genre(RadioGenres.all.first()), t) },
                        onMyCountry = { t -> open(RadioMode.Country(vm.myCountry()), t) },
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
                        else -> StationGrid(stations, current?.id, favorites.map { it.id }.toSet(), listFirst, onPlay = vm.player::play, onFavorite = vm::toggleFavorite)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGrid(
    first: FocusRequester,
    onLucky: () -> Unit,
    onTrending: (String) -> Unit,
    onMyCountry: (String) -> Unit,
    onFavorites: (String) -> Unit,
    onCollections: (String) -> Unit,
    onAccount: (String) -> Unit,
    onGenre: (tv.own.owntv.radio.RadioGenre, String) -> Unit,
) {
    val favTitle = stringResource(R.string.radio_favorites)
    val colTitle = stringResource(R.string.radio_collections)
    val accTitle = stringResource(R.string.radio_account)
    val trendTitle = stringResource(R.string.radio_trending)
    val countryTitle = stringResource(R.string.radio_my_country)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
        contentPadding = PaddingValues(top = 6.mpx, bottom = 40.mpx, start = 6.mpx, end = 6.mpx),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        // The site's top row: «Мне повезёт», «В тренде», «Моя страна» (owner 12.11).
        item {
            GenreTile(stringResource(R.string.radio_lucky), longArrayOf(0xFF7C3AED, 0xFFDB2777, 0xFFF59E0B), OwnTVIcon.PLAY_CIRCLE,
                stringResource(R.string.radio_lucky_desc), Modifier.focusRequester(first), onClick = onLucky)
        }
        item { GenreTile(trendTitle, RadioLook.colorsOf("popular"), OwnTVIcon.TREND, stringResource(R.string.radio_trending_desc)) { onTrending(trendTitle) } }
        item { GenreTile(countryTitle, longArrayOf(0xFF0F766E, 0xFF0891B2, 0xFF2563EB), OwnTVIcon.LANGUAGE, stringResource(R.string.radio_my_country_desc)) { onMyCountry(countryTitle) } }
        item { GenreTile(favTitle, longArrayOf(0xFFDB2777, 0xFF9333EA, 0xFF0891B2), OwnTVIcon.FAVORITE) { onFavorites(favTitle) } }
        item { GenreTile(colTitle, longArrayOf(0xFF0E7490, 0xFF2563EB, 0xFF6D28D9), OwnTVIcon.LIST) { onCollections(colTitle) } }
        item { GenreTile(accTitle, longArrayOf(0xFF1E293B, 0xFF0F766E, 0xFF4C1D95), OwnTVIcon.PERSON) { onAccount(accTitle) } }
        items(RadioGenres.all, key = { it.id }) { g ->
            val name = genreName(g.id)
            GenreTile(name, RadioLook.colorsOf(g.id), glyphIcon(RadioLook.glyphOf(g.id)), genreDescription(g.id)) { onGenre(g, name) }
        }
    }
}

/**
 * A genre tile as on the site: the genre's gradient as a glass card, its glyph in a small square, the name and one
 * line of description, the same glyph large and faint in the corner; the frame lights up in focus.
 */
@Composable
private fun GenreTile(
    name: String,
    colors: LongArray,
    icon: OwnTVIcon? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val r = 22.mpx
    val shape = RoundedCornerShape(r)
    StageSurface(
        onClick = onClick, radius = r, focusStyle = StageFocus.POSTER,
        modifier = modifier.fillMaxWidth().height(170.mpx),
    ) { focused ->
        Box(
            Modifier.fillMaxSize().clip(shape).background(gradientOf(colors))
                .border(2.mpx, Color.White.copy(alpha = if (focused) 0.55f else 0.12f), shape),
        ) {
            icon?.let {
                tv.own.owntv.ui.components.OwnTVIcon(it, tint = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 22.mpx, y = 22.mpx).size(130.mpx))
                Box(
                    Modifier.padding(16.mpx).size(50.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    tv.own.owntv.ui.components.OwnTVIcon(it, tint = Color.White, modifier = Modifier.size(28.mpx))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 18.mpx, end = 18.mpx, bottom = 16.mpx)) {
                Text(name, style = stageText(24, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                description?.let {
                    Text(it, style = stageText(16, 500), color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StationGrid(
    stations: List<RadioStation>,
    playingId: String?,
    favoriteIds: Set<String>,
    first: FocusRequester,
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    // Clear cards (owner 12.11: the list was hard to read): a big logo or letter on top, the name, country and
    // bitrate below; the heart in the corner. OK plays, long OK adds to / removes from the favourites.
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
        contentPadding = PaddingValues(top = 10.mpx, bottom = 40.mpx, start = 10.mpx, end = 10.mpx),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        items(stations, key = { it.id }) { s ->
            StageSurface(
                onClick = { onPlay(s) },
                onLongClick = { onFavorite(s) },
                radius = 20.mpx,
                focusStyle = StageFocus.POSTER,
                highlighted = playingId == s.id,
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().height(290.mpx)
                    .then(if (s == stations.first()) Modifier.focusRequester(first) else Modifier),
            ) { _ ->
                Column(Modifier.fillMaxSize().padding(12.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        StationArt(s, 170.mpx, letter = 70, radius = 18.mpx)
                        if (s.id in favoriteIds) {
                            tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.FAVORITE, tint = Color(0xFFF43F5E), filled = true,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.mpx).size(28.mpx))
                        }
                    }
                    Text(s.name, style = stageText(19, 700), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.mpx))
                    stationLine(s)?.let {
                        Text(it, style = stageText(14, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Country and bitrate. The station's raw tags (`kannadahits` and the like) are never shown (owner 12.11). */
@Composable
internal fun stationLine(s: RadioStation): String? = listOfNotNull(
    s.country?.takeIf { it.isNotBlank() }?.let { c -> RadioLook.flag(s.countryCode)?.let { "$it $c" } ?: c },
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
private fun NowPlaying(
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
        Modifier.height(96.mpx).stageGlass(26.mpx, overContent = true).padding(horizontal = 14.mpx).focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(station, 68.mpx, letter = 30, cover = cover)
        Column(Modifier.width(300.mpx).padding(horizontal = 14.mpx)) {
            Text(station.name, style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (statusLine(status, title) ?: title)?.let {
                Text(it, style = stageText(16, 500), color = if (status == RadioPlayer.Status.ERROR) StageColors.Danger else StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StageButton(text = null, onClick = onToggle, round = true, height = 64.mpx,
            icon = if (tv.own.owntv.radio.RadioPlayback.showsPause(status)) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, iconFilled = true)
        StageButton(text = stringResource(R.string.radio_stop), onClick = onStop, height = 64.mpx, icon = OwnTVIcon.CLOSE,
            modifier = Modifier.padding(start = 10.mpx))
        StageButton(text = null, onClick = onFavorite, round = true, height = 64.mpx, icon = OwnTVIcon.FAVORITE, iconFilled = favorite,
            modifier = Modifier.padding(start = 10.mpx))
        StageButton(text = null, onClick = onExpand, round = true, height = 64.mpx, icon = OwnTVIcon.EXPAND,
            modifier = Modifier.padding(start = 10.mpx))
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
            .background(tv.own.owntv.ui.theme.LocalStagePalette.current.page),
    ) {
        // A blurred copy of the cover fills the screen (as on the site); blur needs Android 12, older boxes keep the
        // colour wash only, and light mode (animations off) keeps it too: blur is costly on weak boxes.
        val art = cover ?: station.favicon
        if (!art.isNullOrBlank() && android.os.Build.VERSION.SDK_INT >= 31 && animationsOn) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.mpx), alpha = 0.45f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(colors[0]).copy(alpha = 0.55f), Color.Transparent))))
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
                    StageButton(text = stringResource(R.string.radio_play_pause), onClick = onToggle, height = 96.mpx, textSize = 24,
                        icon = if (tv.own.owntv.radio.RadioPlayback.showsPause(status)) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, iconFilled = true,
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
