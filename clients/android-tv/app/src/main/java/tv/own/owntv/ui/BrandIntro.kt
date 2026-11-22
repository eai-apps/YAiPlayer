package tv.own.owntv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Process-wide: the intro plays once per cold start, never again when the activity is recreated. */
private object BrandIntroState { var played = false }

/**
 * The launch animation: the logo spins in and settles, a ring of light pulses out, the name rises under
 * it, then the whole thing fades away. About 3.3 s, drawn over the app while it finishes loading
 * underneath — it never delays the app. Off when animations are off.
 */
@Composable
fun BrandIntro(enabled: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(enabled && !BrandIntroState.played) }
    if (!visible) return
    val spin = remember { Animatable(-200f) }
    val pop = remember { Animatable(0.3f) }
    val ring = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        BrandIntroState.played = true
        kotlinx.coroutines.coroutineScope {
            launch { spin.animateTo(0f, tween(1500, easing = FastOutSlowInEasing)) }
            launch { pop.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
            launch { kotlinx.coroutines.delay(800); ring.animateTo(1f, tween(1700, easing = LinearEasing)) }
            launch { kotlinx.coroutines.delay(1200); name.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        }
        kotlinx.coroutines.delay(700)
        fade.animateTo(0f, tween(450, easing = LinearEasing))
        visible = false
    }
    Box(
        modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Brush.verticalGradient(listOf(Color(0xFF161233), Color(0xFF0B0D18)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val r = size.minDimension / 2 * (0.55f + 0.45f * ring.value)
                    drawCircle(Color(0xFF22D3EE).copy(alpha = 0.45f * (1f - ring.value)), radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                }
                Canvas(
                    Modifier
                        .size(120.dp)
                        .scale(pop.value)
                        .rotate(spin.value),
                ) {
                    val u = size.minDimension / 56f
                    drawRoundRect(Color(0xFF5B3FD6), cornerRadius = CornerRadius(13.5f * u, 13.5f * u), size = size)
                    val tri = Path().apply {
                        moveTo(22.7f * u, 20f * u); lineTo(40.2f * u, 30f * u); lineTo(22.7f * u, 40f * u); close()
                    }
                    drawPath(tri, Color.White)
                    drawCircle(Color(0xFF22D3EE), radius = 3.6f * u, center = Offset(41f * u, 17f * u))
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "YAiPlayer",
                modifier = Modifier.graphicsLayer { alpha = name.value; translationY = (1f - name.value) * 24.dp.toPx() },
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            // Beta build (owner 23.11): the word BETA in graffiti, in the middle of the launch screen.
            if (tv.own.owntv.BuildConfig.BETA) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    "BETA",
                    modifier = Modifier.rotate(-6f).graphicsLayer { alpha = name.value; scaleX = 0.7f + 0.3f * name.value; scaleY = 0.7f + 0.3f * name.value },
                    style = TextStyle(
                        color = Color(0xFFFFD640),
                        fontSize = 84.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(tv.own.owntv.R.font.permanent_marker)),
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF140C3C), offset = Offset(6f, 6f), blurRadius = 3f),
                    ),
                )
            }
        }
    }
}


/** The YAi logo as a small flat mark: violet tile, white play triangle, cyan dot. */
@Composable
fun YaiLogoMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 56f
        drawRoundRect(Color(0xFF5B3FD6), cornerRadius = CornerRadius(13.5f * u, 13.5f * u), size = this.size)
        val tri = Path().apply {
            moveTo(22.7f * u, 20f * u); lineTo(40.2f * u, 30f * u); lineTo(22.7f * u, 40f * u); close()
        }
        drawPath(tri, Color.White)
        drawCircle(Color(0xFF22D3EE), radius = 3.6f * u, center = Offset(41f * u, 17f * u))
    }
}

/** Top-left badge of the home screen: the logo, the name and "our player" under it. */
@Composable
fun YaiBadge(modifier: Modifier = Modifier, scale: Float = 1f, extra: String? = null) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        YaiLogoMark((36 * scale).dp)
        androidx.compose.foundation.layout.Spacer(Modifier.width((10 * scale).dp))
        Column {
            BasicText("YAiPlayer", style = TextStyle(color = Color.White, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold))
            BasicText(
                "v" + androidx.compose.ui.platform.LocalContext.current.let { c ->
                    runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
                },
                style = TextStyle(color = Color(0xFF22D3EE), fontSize = (12 * scale).sp, fontWeight = FontWeight.Medium),
            )
        }
        // Owner 16.11: which profile and which playlist / portal is on, at a glance («sly · stalker3»).
        // Owner 17.11: not under the version (it ran into «Продолжить просмотр») but to the right of
        // the badge, larger, in a warm yellow-to-orange «fire» gradient, like a sub-profile title.
        if (!extra.isNullOrBlank()) {
            androidx.compose.foundation.layout.Spacer(Modifier.width((22 * scale).dp))
            BasicText(
                extra,
                style = TextStyle(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFFFFE259), Color(0xFFFFA751), Color(0xFFFF6A3D)),
                    ),
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99FF6A00), blurRadius = 14f),
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** «Profile · playlist» of what is active now, for [YaiBadge]. */
@Composable
fun rememberProfileSourceLabel(): String? {
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val db: tv.own.owntv.core.database.OwnTVDatabase = org.koin.compose.koinInject()
    val pid by settings.activeProfileId.collectAsState(initial = -1L)
    val sid by settings.defaultSourceId.collectAsState(initial = -1L)
    val label = androidx.compose.runtime.produceState<String?>(null, pid, sid) {
        value = runCatching {
            val profile = if (pid > 0) db.profileDao().getById(pid)?.name else null
            val source = if (sid > 0) db.sourceDao().getById(sid)?.name else null
            listOfNotNull(profile, source).joinToString(" · ").ifBlank { null }
        }.getOrNull()
    }
    return label.value
}
