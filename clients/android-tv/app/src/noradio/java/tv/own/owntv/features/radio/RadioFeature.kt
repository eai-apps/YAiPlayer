// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import androidx.compose.runtime.Composable

/** Google Play build: no radio. This stub is all of it — no screens, no player, no strings. */
object RadioFeature {
    const val AVAILABLE = false

    @Composable
    fun label(): String = ""

    @Composable
    fun Screen(onBack: () -> Unit) = Unit

    @Composable
    fun playingLabel(): String? = null

    @Composable
    fun nowPlaying(): String? = null
}
