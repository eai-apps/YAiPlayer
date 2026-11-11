package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.player.TrackFormatter
import tv.own.owntv.player.TrackLabelKind
import tv.own.owntv.player.TrackLocalizedStrings
import tv.own.owntv.player.TrackOption
import java.util.Locale

/**
 * Final player presentation for an engine-owned track row. Engines expose raw labels, language
 * codes and stable ordinals; only this Compose mapper supplies localized fallback and source words.
 */
@Composable
fun TrackOption.displayLabel(): String {
    val configuration = LocalResources.current.configuration
    val locale = if (configuration.locales.isEmpty) Locale.ENGLISH else configuration.locales[0]
    val fallbackNumber: (Int) -> String = { num ->
        when (labelKind) {
            TrackLabelKind.AUDIO -> stringResource(R.string.player_audio_track_number, num)
            TrackLabelKind.SUBTITLE -> stringResource(R.string.player_subtitle_track_number, num)
        }
    }
    val strings = TrackLocalizedStrings(
        dubbing = stringResource(R.string.track_type_dubbing),
        original = stringResource(R.string.track_type_original),
        forced = stringResource(R.string.track_type_forced),
        full = stringResource(R.string.track_type_full),
        multiVoice = stringResource(R.string.track_type_multi_voice),
        twoVoice = stringResource(R.string.track_type_two_voice),
        singleVoice = stringResource(R.string.track_type_single_voice),
        commentary = stringResource(R.string.track_type_commentary),
        sdh = stringResource(R.string.track_type_sdh),
        fallbackNumber = fallbackNumber,
    )
    return TrackFormatter.format(this, locale, strings)
}
