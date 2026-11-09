// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreensaverOverlay(modifier: Modifier = Modifier) {
    val isVisible by ScreensaverTvManager.isScreensaverVisible.collectAsStateWithLifecycle()
    val config by ScreensaverTvManager.config.collectAsStateWithLifecycle()

    if (!isVisible) return

    val items = remember { ScreensaverManifest.FALLBACK_ITEMS }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Change slide every 12 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(12000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    // Update clock every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(10000)
        }
    }

    // Continuous Ken Burns slow zoom (1.0f to 1.12f)
    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val pan by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pan",
    )

    val currentItem = items[currentIndex % items.size]
    val isClockMode = config.mode == ScreensaverMode.CLOCK

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMMM", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(currentTimeMs))
    val formattedDate = dateFormat.format(Date(currentTimeMs))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ScreensaverTvManager.dismiss() }
            )
    ) {
        if (!isClockMode) {
            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    fadeIn(animationSpec = tween(1500)).togetherWith(fadeOut(animationSpec = tween(1500)))
                },
                label = "screensaver_image",
                modifier = Modifier.fillMaxSize(),
            ) { item ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = pan
                                translationY = pan / 2f
                            },
                    )

                    // Bottom info gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                            .padding(start = 48.mpx, end = 48.mpx, top = 60.mpx, bottom = 36.mpx)
                    ) {
                        Column {
                            Text(
                                text = item.title,
                                style = stageText(26, 700),
                                color = StageColors.Text,
                            )
                            item.subtitle?.let { sub ->
                                Text(
                                    text = sub,
                                    style = stageText(16, 400),
                                    color = StageColors.Muted,
                                    modifier = Modifier.padding(top = 4.mpx),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Full-screen clock mode: large digits with gentle anti-burn-in shift
            val driftMinute = (currentTimeMs / 60000L % 10L).toInt()
            val offsetX = (driftMinute * 6 - 30).dp
            val offsetY = (driftMinute * 4 - 20).dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.roundToPx(), offsetY.roundToPx()) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        style = stageText(110, 800),
                        color = StageColors.Text,
                    )
                    Text(
                        text = formattedDate,
                        style = stageText(28, 500),
                        color = StageColors.Muted,
                        modifier = Modifier.padding(top = 12.mpx),
                    )
                }
            }
        }

        // Corner clock (when enabled and not in full clock mode)
        if (config.showCornerClock && !isClockMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(36.mpx)
            ) {
                Text(
                    text = formattedTime,
                    style = stageText(28, 600),
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        // Dismiss hint in corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.mpx)
        ) {
            Text(
                text = stringResource(R.string.screensaver_dismiss_hint),
                style = stageText(12, 400),
                color = StageColors.Muted.copy(alpha = 0.6f),
            )
        }
    }
}
