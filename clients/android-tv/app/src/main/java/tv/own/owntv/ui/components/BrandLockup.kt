package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.brand.PixelMark as CorePixelMark
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageAccent

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/** Settings › Accent-colored logo: the play triangle of every in-app logo in the accent (default on). */
@Composable
private fun accentTriangle(): Color? {
    val settings: SettingsRepository = koinInject()
    val on by settings.brandAccentTriangle.collectAsStateWithLifecycle(initialValue = true)
    return if (on) stageAccent.accent else null
}

/**
 * The canonical YAiPlayer mark (rounded squircle tile, play triangle, signal dot).
 * With Accent-colored logo on, the play triangle takes the accent.
 * [followAccent] false draws the icon in its launcher colours.
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    val small = size <= 32.dp
    val accent = accentTriangle().takeIf { followAccent }

    val (bg, defaultTri, dot) = iconColors(icon)
    val triColor = accent ?: defaultTri

    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 56f
        drawRoundRect(
            color = bg,
            cornerRadius = CornerRadius(13.5f * u, 13.5f * u),
            size = this.size,
        )
        val tri = Path().apply {
            moveTo(22.7f * u, 20f * u)
            lineTo(40.2f * u, 30f * u)
            lineTo(22.7f * u, 40f * u)
            close()
        }
        drawPath(tri, triColor)
        drawPath(
            path = tri,
            color = triColor,
            style = Stroke(
                width = 2.5f * u,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round,
            ),
        )
        if (!small) {
            drawCircle(dot, radius = 3.6f * u, center = Offset(41f * u, 17f * u))
        }
    }
}

private fun iconColors(icon: AppIcon): Triple<Color, Color, Color> = when (icon) {
    AppIcon.EGGSHELL -> Triple(Color(0xFF5B3FD6), Color.White, Color(0xFF22D3EE))
    AppIcon.PETROL -> Triple(Color(0xFF0E7C86), Color.White, Color(0xFFFDE68A))
    AppIcon.SUNFLOWER -> Triple(Color(0xFFE5A50A), Color.White, Color(0xFF5B3FD6))
    AppIcon.COBALT -> Triple(Color(0xFF3E63D2), Color.White, Color(0xFF22D3EE))
    AppIcon.TOMATO -> Triple(Color(0xFFD8523C), Color.White, Color(0xFFFDE68A))
    AppIcon.BOARD -> Triple(Color(0xFF171A24), Color.White, Color(0xFF22D3EE))
    AppIcon.OLIVE -> Triple(Color(0xFF2F8F5B), Color.White, Color(0xFFFDE68A))
    AppIcon.OLIVE_CREAM -> Triple(Color(0xFFE8E2FF), Color(0xFF5B3FD6), Color(0xFF0E7C86))
    AppIcon.PIXEL -> Triple(Color(0xFF0F172A), Color.White, Color(0xFF22D3EE))
}

/**
 * "Pixel": retained for compatibility if called explicitly; [BrandMark] draws the canonical mark.
 */
@Composable
fun PixelMark(modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        CorePixelMark.dots.forEach { d ->
            val color = when (d.kind) {
                CorePixelMark.Kind.BODY -> PixelBody
                CorePixelMark.Kind.TRIANGLE -> triangle
                CorePixelMark.Kind.SIDE -> PixelSide
            }
            drawCircle(color, CorePixelMark.DOT_RADIUS * u, Offset(d.x * u, d.y * u))
        }
        drawCircle(PixelPower, CorePixelMark.POWER_RADIUS * u, Offset(CorePixelMark.POWER_X * u, CorePixelMark.POWER_Y * u))
    }
}

private val PixelBody = Color(CorePixelMark.BODY)
private val PixelSide = Color(CorePixelMark.SIDE)
private val PixelPower = Color(CorePixelMark.POWER)

/**
 * The wordmark: "YAi" in [own] and the rest in [tv] (the accent).
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier, tv: Color = stageAccent.accent, own: Color = WordmarkCream) {
    val name = stringResource(R.string.app_name)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = own)) { append(name.take(3)) }
        withStyle(SpanStyle(color = tv)) { append(name.drop(3)) }
    }
    Box(modifier.width(width).height(width * 182f / 988f * 1.6f), contentAlignment = Alignment.CenterStart) {
        androidx.compose.foundation.text.BasicText(
            text, maxLines = 1, softWrap = false,
            style = TextStyle(
                fontSize = (width.value * 0.19f).sp, fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

val WordmarkCream = Color(0xFFFFF6EE)

/**
 * The lockup (Brand tab, C column): the mark, then the wordmark at 2.75 × the mark's width (62 →
 * 170 in the design). Side by side the gap is 0.23 × the mark (14 / 62); [stacked] (the preview pane)
 * puts the mark above with a 0.2 × gap.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    stacked: Boolean = false,
) {
    val icon = rememberAppliedIcon()
    val parts = @Composable {
        BrandMark(icon, markSize.dp)
        // On a light theme "own" takes the text colour; the cream is for dark surfaces.
        Wordmark((markSize * 2.75f).dp, own = if (tv.own.owntv.ui.theme.LocalStagePalette.current.isLight) StageColors.Text else WordmarkCream)
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.23f).dp),
        ) { parts() }
    }
}
