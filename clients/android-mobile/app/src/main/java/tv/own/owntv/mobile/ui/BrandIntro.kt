package tv.own.owntv.mobile.ui

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
 * it, then the whole thing fades away. About 1.6 s, drawn over the app while it finishes loading
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
            launch { spin.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            launch { pop.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { kotlinx.coroutines.delay(450); ring.animateTo(1f, tween(900, easing = LinearEasing)) }
            launch { kotlinx.coroutines.delay(650); name.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        }
        kotlinx.coroutines.delay(250)
        fade.animateTo(0f, tween(350, easing = LinearEasing))
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
fun YaiBadge(modifier: Modifier = Modifier, scale: Float = 1f) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        YaiLogoMark((36 * scale).dp)
        androidx.compose.foundation.layout.Spacer(Modifier.width((10 * scale).dp))
        Column {
            BasicText("YAiPlayer", style = TextStyle(color = Color.White, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold))
            BasicText(
                androidx.compose.ui.res.stringResource(tv.own.owntv.mobile.R.string.brand_our_player),
                style = TextStyle(color = Color(0xFF22D3EE), fontSize = (12 * scale).sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
