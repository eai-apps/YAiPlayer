// miptv (operator edition of YAiPlayer). Original code. See docs/MIP_PLAYER.md.
package tv.own.owntv.mobile.ui.mip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** miptv colours (the same as the logo drawing: tools/mip/assets/mip_mark.svg). */
private val Violet = Color(0xFF8B5CFF)
private val Pink = Color(0xFFFF3D9A)
private val Orange = Color(0xFFFFA24A)
private val NameWhite = Color(0xFFFFFFFF)
private val NameWarm = Color(0xFFFF8A5B)

private val MipFont = FontFamily(Font(tv.own.owntv.mobile.R.font.mip_unbounded, FontWeight.Bold))

/** Process-wide: the intro plays once per cold start. */
private object MipIntroState { var played = false }

/**
 * The miptv TV in a 100x100 box. [body] 0..1 — the case fades/scales in; [line] 0..1 — the bright line of the
 * screen switching on; [open] 0..1 — the line opens into the lit screen; [play] 0..1 — the play triangle pops.
 */
private fun DrawScope.drawMipTv(body: Float, line: Float, open: Float, play: Float) {
    val u = size.minDimension / 100f
    val brush = Brush.linearGradient(listOf(Violet, Pink, Orange), start = Offset(0f, 100f * u), end = Offset(100f * u, 0f))
    withTransform({ scale(0.86f + 0.14f * body, 0.86f + 0.14f * body, pivot = center) }) {
        drawLine(brush, Offset(50f * u, 32f * u), Offset(34f * u, 10f * u), strokeWidth = 6f * u, cap = StrokeCap.Round, alpha = body)
        drawLine(brush, Offset(50f * u, 32f * u), Offset(66f * u, 10f * u), strokeWidth = 6f * u, cap = StrokeCap.Round, alpha = body)
        drawRoundRect(brush, topLeft = Offset(9f * u, 30f * u), size = Size(82f * u, 60f * u), cornerRadius = CornerRadius(19f * u, 19f * u), alpha = body)
        drawRoundRect(Color(0xFF0E1030), topLeft = Offset(19f * u, 40f * u), size = Size(62f * u, 40f * u), cornerRadius = CornerRadius(11f * u, 11f * u), alpha = body)
        if (line > 0f && open <= 0f) { // the thin bright line in the middle of the dark screen
            val w = 62f * u * line
            drawRoundRect(Color.White, topLeft = Offset(50f * u - w / 2f, 59.2f * u), size = Size(w, 1.6f * u), cornerRadius = CornerRadius(0.8f * u, 0.8f * u), alpha = line)
        }
        if (open > 0f) { // the line opens into the lit screen (with a flash that settles)
            val h = 40f * u * open
            val r = (11f * u).coerceAtMost(h / 2f)
            val top = Offset(19f * u, 60f * u - h / 2f)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3B2A9E), Color(0xFF17104A)), startY = top.y, endY = top.y + h), topLeft = top, size = Size(62f * u, h), cornerRadius = CornerRadius(r, r))
            drawRoundRect(Color.White, topLeft = top, size = Size(62f * u, h), cornerRadius = CornerRadius(r, r), alpha = 0.55f * (1f - open))
        }
        if (play > 0f) {
            withTransform({ scale(play, play, pivot = Offset(52f * u, 60f * u)) }) {
                val tri = Path().apply { moveTo(42f * u, 50f * u); lineTo(42f * u, 70f * u); lineTo(62f * u, 60f * u); close() }
                drawPath(tri, Color.White, alpha = play.coerceIn(0f, 1f))
                drawPath(tri, Color.White, alpha = play.coerceIn(0f, 1f), style = Stroke(width = 3f * u, join = StrokeJoin.Round))
            }
        }
    }
}

/** The finished miptv logo mark (no animation): the home badge, the loading state. */
@Composable
fun MipMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawMipTv(1f, 0f, 1f, 1f) }
}

/** «miptv» in lowercase, «mip» white and «tv» warm. */
@Composable
fun MipWordmark(fontSize: Int, modifier: Modifier = Modifier) {
    Row(modifier) {
        "miptv".forEachIndexed { i, c ->
            BasicText(c.toString(), style = TextStyle(color = if (i < 3) NameWhite else NameWarm, fontSize = fontSize.sp, fontFamily = MipFont, fontWeight = FontWeight.Bold))
        }
    }
}

/** What BrandLockup shows in the miptv edition: the mark and the name instead of YAiPlayer's. */
@Composable
fun MipLockup(markSize: Int, stacked: Boolean, modifier: Modifier = Modifier) {
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp)) {
            MipMark(markSize.dp)
            MipWordmark((markSize * 0.62f).toInt())
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((markSize * 0.25f).dp)) {
            MipMark(markSize.dp)
            MipWordmark((markSize * 0.62f).toInt())
        }
    }
}

/**
 * The miptv launch animation (owner 28.11): the TV appears, its screen switches on (a line of light opens into the
 * picture), the play button pops and the letters «m-i-p-t-v» dissolve in one by one; then everything fades. ~3.4 s,
 * over the app while it loads (never delays it). Replaces the YAiPlayer animation in this edition.
 */
@Composable
fun MipBrandIntro(enabled: Boolean, beta: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(enabled && !MipIntroState.played) }
    if (!visible) return
    val body = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    val play = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val letters = remember { List(5) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        MipIntroState.played = true
        coroutineScope {
            launch { body.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            launch {
                delay(700)
                line.animateTo(1f, tween(280, easing = LinearEasing))
                open.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
            launch { delay(1500); play.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { delay(1500); ring.animateTo(1f, tween(1100, easing = LinearEasing)) }
            letters.forEachIndexed { i, a -> launch { delay(1650L + i * 120L); a.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) } }
        }
        delay(800)
        fade.animateTo(0f, tween(450, easing = LinearEasing))
        visible = false
    }
    Box(
        modifier.fillMaxSize().alpha(fade.value).background(Brush.verticalGradient(listOf(Color(0xFF150D3A), Color(0xFF07080F)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    val r = size.minDimension / 2f * (0.5f + 0.5f * ring.value)
                    drawCircle(Pink.copy(alpha = 0.4f * (1f - ring.value)), radius = r, center = center, style = Stroke(width = 3.dp.toPx()))
                }
                Canvas(Modifier.size(150.dp)) { drawMipTv(body.value, line.value, open.value, play.value) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                "miptv".forEachIndexed { i, c ->
                    val p = letters[i].value
                    BasicText(
                        c.toString(),
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            val s = 1.6f - 0.6f * p // grows out of a haze: «dissolves» into the letter
                            scaleX = s; scaleY = s
                            translationY = (1f - p) * 14.dp.toPx()
                        },
                        style = TextStyle(color = if (i < 3) NameWhite else NameWarm, fontSize = 50.sp, fontFamily = MipFont, fontWeight = FontWeight.Bold),
                    )
                }
            }
            if (beta) {
                Spacer(Modifier.height(14.dp))
                BasicText(
                    "BETA",
                    modifier = Modifier.background(Color(0xFFFFC83D), androidx.compose.foundation.shape.RoundedCornerShape(50)).graphicsLayer { alpha = letters[4].value }.padding(horizontal = 18.dp, vertical = 4.dp),
                    style = TextStyle(color = Color(0xFF1B1147), fontSize = 20.sp, fontFamily = MipFont, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
