package tv.own.owntv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.player.TrackFormatter
import tv.own.owntv.player.TrackLabelKind
import tv.own.owntv.player.TrackLocalizedStrings
import tv.own.owntv.player.TrackOption
import java.util.Locale

/**
 * The final wording of a track row. The engine hands out raw labels, language codes and ordinals and
 * nothing else — a translated word stored in engine state would be the wrong word after a language
 * change — so a nameless track becomes "English" or "Subtitle 2" here and only here.
 */
@Composable
fun TrackOption.displayLabel(): String {
    val resources = LocalResources.current
    val configuration = resources.configuration
    val locale = if (configuration.locales.isEmpty) Locale.ENGLISH else configuration.locales[0]
    val fallbackNumber: (Int) -> String = { num ->
        when (labelKind) {
            TrackLabelKind.AUDIO -> resources.getString(R.string.player_audio_track_number, num)
            TrackLabelKind.SUBTITLE -> resources.getString(R.string.player_subtitle_track_number, num)
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
