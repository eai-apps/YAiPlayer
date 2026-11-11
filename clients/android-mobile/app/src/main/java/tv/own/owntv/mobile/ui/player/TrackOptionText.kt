package tv.own.owntv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.player.TrackLabelKind
import tv.own.owntv.player.TrackOption
import java.util.Locale
import tv.own.owntv.core.player.TrackTitle
import tv.own.owntv.core.player.TrackTitleKind

/**
 * The final wording of a track row. The engine hands out raw labels, language codes and ordinals and
 * nothing else — a translated word stored in engine state would be the wrong word after a language
 * change — so a nameless track becomes "English" or "Subtitle 2" here and only here.
 */
@Composable
fun TrackOption.displayLabel(): String {
    val configuration = LocalResources.current.configuration
    val locale = if (configuration.locales.isEmpty) Locale.ENGLISH else configuration.locales[0]
    val raw = label.takeIf { it.isNotBlank() }
    // External subs carry their source in the raw label's `OS_`/`LOCAL_` prefix (SubtitleTrackLabel).
    if (TrackTitle.isExternalLabel(raw)) return raw!!
    val fallback = when (labelKind) {
        TrackLabelKind.AUDIO -> stringResource(R.string.player_audio_track_number, displayNumber())
        TrackLabelKind.SUBTITLE -> stringResource(R.string.player_subtitle_track_number, displayNumber())
    }
    val kindWords = mapOf(
        TrackTitleKind.FORCED to stringResource(R.string.player_track_kind_forced),
        TrackTitleKind.FULL to stringResource(R.string.player_track_kind_full),
        TrackTitleKind.SDH to stringResource(R.string.player_track_kind_sdh),
        TrackTitleKind.DUB to stringResource(R.string.player_track_kind_dub),
        TrackTitleKind.MVO to stringResource(R.string.player_track_kind_mvo),
        TrackTitleKind.DVO to stringResource(R.string.player_track_kind_dvo),
        TrackTitleKind.AVO to stringResource(R.string.player_track_kind_avo),
        TrackTitleKind.ORIGINAL to stringResource(R.string.player_track_kind_original),
        TrackTitleKind.COMMENTARY to stringResource(R.string.player_track_kind_commentary),
    )
    // Language first, then what kind of track it is (П9): «Русский · Форсированные», not «forced».
    return TrackTitle.display(raw, lang, locale, { kindWords.getValue(it) }, fallback, stringResource(R.string.content_epg_bits_separator), forced)
}

private fun TrackOption.displayNumber(): Int = (typeIndex.takeIf { it >= 0 } ?: mpvId) + 1
