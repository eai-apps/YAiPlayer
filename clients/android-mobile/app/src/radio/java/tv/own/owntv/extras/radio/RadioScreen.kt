// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.radio

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageGlass
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion
import tv.own.owntv.radio.RadioGenres
import tv.own.owntv.radio.RadioLook
import tv.own.owntv.radio.RadioMode
import tv.own.owntv.radio.RadioPlayer
import tv.own.owntv.radio.RadioStation
import tv.own.owntv.radio.RadioViewModel

/** The genre's name from the radio strings (`radio_genre_<id>`), as in the site's genre list. */
@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@Composable
internal fun genreName(id: String): String {
    val context = LocalContext.current
    val res = context.resources.getIdentifier("radio_genre_$id", "string", context.packageName)
    return if (res != 0) stringResource(res) else id
}

private fun gradientOf(colors: LongArray): Brush = Brush.linearGradient(colors.map { Color(it) })

/**
 * Radio on the phone, after the YAiPlay Radio site (owner 12.11): coloured genre tiles, a station list with logos
 * (a letter on a gradient when a station has none), a mini player at the bottom that opens a full player.
 */
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
    val cover by vm.player.cover.collectAsStateWithLifecycle()
    val nowTitle by vm.player.title.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var home by rememberSaveable { mutableStateOf(true) }
    var heading by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        when {
            expanded -> expanded = false
            !home -> { home = true; heading = null; query = "" }
            else -> onBack()
        }
    }
    // Leaving the radio stops it: it must never play on under a channel.
    DisposableEffect(Unit) { onDispose { vm.player.stop() } }

    fun open(m: RadioMode, title: String) {
        vm.select(m)
        heading = title
        home = false
    }

    Column(Modifier.fillMaxSize().stageBackground().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            StagePill(
                onClick = { if (home) onBack() else { home = true; heading = null; query = "" } },
                icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.radio_back),
            )
            Text(heading ?: stringResource(R.string.radio_title), style = stageText(18f, 800), color = palette.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
            val accountTitle = stringResource(R.string.radio_account)
            StagePill(onClick = { query = ""; open(RadioMode.Account, accountTitle) }, icon = MobileIcons.Person,
                contentDescription = stringResource(R.string.radio_account))
        }
        StageSearchField(
            value = query,
            onValueChange = { query = it; vm.search(it); home = it.isBlank() && heading == null },
            placeholder = stringResource(R.string.radio_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (loading && !home) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                home && query.isBlank() -> HomeGrid(
                    onFavorites = { open(RadioMode.Favorites, it) },
                    onCollections = { open(RadioMode.Collections, it) },
                    onGenre = { g, t -> open(RadioMode.Genre(g), t) },
                )
                mode == RadioMode.Account -> RadioAccountPanel(vm)
                failed && stations.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.radio_failed), style = stageText(14f, 600), color = palette.muted)
                    StageButton(onClick = vm::retry, text = stringResource(R.string.radio_retry), modifier = Modifier.padding(top = 10.dp))
                }
                !loading && stations.isEmpty() -> Text(
                    stringResource(if (mode == RadioMode.Favorites) R.string.radio_favorites_empty else R.string.radio_empty),
                    style = stageText(14f, 600), color = palette.muted, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(stations, key = { it.id }) { s ->
                        StationRow(s, playing = current?.id == s.id, favorite = favorites.any { it.id == s.id },
                            onPlay = { vm.player.play(s) }, onFavorite = { vm.toggleFavorite(s) })
                    }
                }
            }
        }
        current?.let { s ->
            MiniPlayer(s, status, nowTitle, cover, favorite = favorites.any { it.id == s.id },
                onToggle = vm.player::toggle, onStop = vm.player::stop, onFavorite = { vm.toggleFavorite(s) }, onExpand = { expanded = true })
        }
    }
    val playing = current
    if (expanded && playing != null) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            FullPlayer(playing, status, nowTitle, cover, favorite = favorites.any { it.id == playing.id },
                onToggle = vm.player::toggle, onStop = { vm.player.stop(); expanded = false },
                onFavorite = { vm.toggleFavorite(playing) }, onClose = { expanded = false })
        }
    }
}

@Composable
private fun HomeGrid(onFavorites: (String) -> Unit, onCollections: (String) -> Unit, onGenre: (tv.own.owntv.radio.RadioGenre, String) -> Unit) {
    val fav = stringResource(R.string.radio_favorites)
    val col = stringResource(R.string.radio_collections)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { GenreTile(fav, longArrayOf(0xFFDB2777, 0xFF9333EA, 0xFF0891B2)) { onFavorites(fav) } }
        item { GenreTile(col, longArrayOf(0xFF0E7490, 0xFF2563EB, 0xFF6D28D9)) { onCollections(col) } }
        items(RadioGenres.all, key = { it.id }) { g ->
            val name = genreName(g.id)
            GenreTile(name, RadioLook.colorsOf(g.id)) { onGenre(g, name) }
        }
    }
}

@Composable
private fun GenreTile(name: String, colors: LongArray, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(gradientOf(colors)).clickable(onClick = onClick),
    ) {
        Text(name, style = stageText(15f, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

/** Country and bitrate; the station's raw tags are never shown (owner 12.11). */
@Composable
private fun stationLine(s: RadioStation): String? = listOfNotNull(
    s.country?.takeIf { it.isNotBlank() },
    s.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.radio_kbps, it) },
).joinToString(" · ").ifBlank { null }

@Composable
private fun StationArt(s: RadioStation, size: Dp, letter: Float, radius: Dp = 12.dp, cover: String? = null) {
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
private fun StationRow(s: RadioStation, playing: Boolean, favorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(s, 48.dp, 20f)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.name, style = stageText(14f, if (playing) 800 else 600), color = if (playing) stageAccent.accent else palette.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            stationLine(s)?.let { Text(it, style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        StagePill(
            onClick = onFavorite,
            icon = if (favorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.radio_fav_remove else R.string.radio_fav_add),
        )
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
    s: RadioStation, status: RadioPlayer.Status, title: String?, cover: String?, favorite: Boolean,
    onToggle: () -> Unit, onStop: () -> Unit, onFavorite: () -> Unit, onExpand: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).stageGlass(shape).clip(shape)
            .clickable(onClick = onExpand).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(s, 46.dp, 19f, cover = cover)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(s.name, style = stageText(14f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            statusLine(status, title)?.let {
                Text(it, style = stageText(12f, 500), color = if (status == RadioPlayer.Status.ERROR) palette.danger else palette.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (status == RadioPlayer.Status.PLAYING) Equalizer(Modifier.padding(end = 8.dp).height(22.dp).width(28.dp))
        StagePill(onClick = onFavorite, icon = if (favorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
            contentDescription = stringResource(if (favorite) R.string.radio_fav_remove else R.string.radio_fav_add))
        StagePill(onClick = onToggle, icon = if (status == RadioPlayer.Status.PLAYING || status == RadioPlayer.Status.BUFFERING) MobileIcons.Pause else MobileIcons.PlayArrow,
            contentDescription = stringResource(R.string.radio_play_pause), modifier = Modifier.padding(start = 6.dp))
        StagePill(onClick = onStop, icon = MobileIcons.Close, contentDescription = stringResource(R.string.radio_stop),
            modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun FullPlayer(
    s: RadioStation, status: RadioPlayer.Status, title: String?, cover: String?, favorite: Boolean,
    onToggle: () -> Unit, onStop: () -> Unit, onFavorite: () -> Unit, onClose: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val colors = RadioLook.placeholderColors(s.name)
    Column(
        Modifier.fillMaxSize().stageBackground()
            .background(Brush.verticalGradient(listOf(Color(colors[0]).copy(alpha = 0.5f), Color.Transparent)))
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            StagePill(onClick = onClose, icon = MobileIcons.KeyboardArrowDown, contentDescription = stringResource(R.string.radio_back))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StationArt(s, 260.dp, 110f, radius = 28.dp, cover = cover) }
        Text(s.name, style = stageText(22f, 800), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        statusLine(status, title)?.let {
            Text(it, style = stageText(15f, 600), color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        Equalizer(Modifier.padding(top = 24.dp).height(48.dp).width(120.dp), playing = status == RadioPlayer.Status.PLAYING)
        Row(Modifier.padding(top = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StagePill(onClick = onFavorite, icon = if (favorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
                contentDescription = stringResource(if (favorite) R.string.radio_fav_remove else R.string.radio_fav_add))
            Box(
                Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(stageAccent.accent).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    if (status == RadioPlayer.Status.PLAYING || status == RadioPlayer.Status.BUFFERING) MobileIcons.Pause else MobileIcons.PlayArrow,
                    contentDescription = stringResource(R.string.radio_play_pause), tint = Color.White, modifier = Modifier.size(34.dp),
                )
            }
            StagePill(onClick = onStop, icon = MobileIcons.Close, contentDescription = stringResource(R.string.radio_stop))
        }
    }
}

/** Five bars bouncing while the station plays; still when animations are off (light mode) or paused. */
@Composable
private fun Equalizer(modifier: Modifier, playing: Boolean = true) {
    val accent = stageAccent.accent
    val animate = LocalMobileMotion.current.enabled && playing
    val transition = rememberInfiniteTransition(label = "radioEq")
    val phases = listOf(520, 680, 430, 610, 750)
    val still = listOf(0.5f, 0.8f, 0.4f, 0.7f, 0.55f)
    val levels = phases.mapIndexed { i, ms ->
        if (animate) transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "eq$i").value else still[i]
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        levels.forEach { level -> Box(Modifier.weight(1f).fillMaxHeight(level).clip(RoundedCornerShape(2.dp)).background(accent)) }
    }
}
