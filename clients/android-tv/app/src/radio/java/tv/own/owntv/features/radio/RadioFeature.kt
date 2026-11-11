// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

/** The normal build: the radio (src/radio and clients/radio-shared). */
object RadioFeature {
    const val AVAILABLE = true

    @Composable
    fun label(): String = stringResource(R.string.radio_title)

    @Composable
    fun Screen(onBack: () -> Unit) = RadioScreen(onBack)
}
