// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.radio.RadioPlayer

/** The normal build: the radio (src/radio and clients/radio-shared). */
object RadioFeature {
    const val AVAILABLE = true

    @Composable
    fun label(): String = stringResource(R.string.radio_title)

    @Composable
    fun Screen(onBack: () -> Unit) = RadioScreen(onBack)

    /** «Радио играет» for the pill at the top of the shell; null when no station is chosen. */
    @Composable
    fun playingLabel(): String? = nowPlaying()?.let { stringResource(R.string.radio_playing) }

    /** The station that plays (or is paused) in the background, or null. */
    @Composable
    fun nowPlaying(): String? {
        val context = LocalContext.current
        val player = remember { RadioPlayer.get(context) }
        val current by player.current.collectAsStateWithLifecycle()
        return current?.name
    }
}
