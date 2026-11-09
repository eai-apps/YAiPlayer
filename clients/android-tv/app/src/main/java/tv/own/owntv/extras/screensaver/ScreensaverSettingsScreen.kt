// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.settingHelp
import tv.own.owntv.ui.components.OwnTVIcon

internal val SCREENSAVER_SEARCH_ROWS: List<Int> =
    listOf(R.string.screensaver_title, R.string.screensaver_enable, R.string.screensaver_timeout, R.string.screensaver_mode, R.string.screensaver_preview)

@Composable
fun ScreensaverSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val config by ScreensaverTvManager.config.collectAsStateWithLifecycle()

    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }

    val timeouts = listOf(1, 3, 5, 10, 15, 30)
    val timeoutLabels = mapOf(
        1 to stringResource(R.string.screensaver_timeout_1m),
        3 to stringResource(R.string.screensaver_timeout_3m),
        5 to stringResource(R.string.screensaver_timeout_5m),
        10 to stringResource(R.string.screensaver_timeout_10m),
        15 to stringResource(R.string.screensaver_timeout_15m),
        30 to stringResource(R.string.screensaver_timeout_30m),
    )

    val modes = ScreensaverMode.entries
    val modeLabels = mapOf(
        ScreensaverMode.POSTERS to stringResource(R.string.screensaver_mode_posters),
        ScreensaverMode.NATURE_SPACE to stringResource(R.string.screensaver_mode_nature),
        ScreensaverMode.CLOCK to stringResource(R.string.screensaver_mode_clock),
        ScreensaverMode.MIX to stringResource(R.string.screensaver_mode_mix),
    )

    val title = stringResource(R.string.screensaver_title)
    val desc = stringResource(R.string.screensaver_desc)

    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_app)),
        title = title,
        count = pluralStringResource(R.plurals.settings_setting_count, 5, 5),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        val enableTitle = stringResource(R.string.screensaver_enable)
        val enableValue = SettingValue.Switch(config.enabled)
        StageSettingRow(
            icon = OwnTVIcon.IMAGE,
            title = enableTitle,
            desc = desc,
            value = enableValue,
            onClick = { ScreensaverTvManager.setEnabled(!config.enabled) },
            help = settingHelp(null, enableTitle, desc, enableValue, pinnable = false),
        )

        if (config.enabled) {
            val timeoutTitle = stringResource(R.string.screensaver_timeout)
            val currentTimeoutLabel = timeoutLabels[config.timeoutMinutes] ?: "${config.timeoutMinutes} m"
            val timeoutValue = SettingValue.Action(currentTimeoutLabel)
            StageSettingRow(
                icon = OwnTVIcon.CLOCK,
                title = timeoutTitle,
                desc = currentTimeoutLabel,
                value = timeoutValue,
                onClick = {
                    val idx = timeouts.indexOf(config.timeoutMinutes)
                    val next = timeouts[(idx + 1) % timeouts.size]
                    ScreensaverTvManager.setTimeoutMinutes(next)
                },
                help = settingHelp(null, timeoutTitle, currentTimeoutLabel, timeoutValue, pinnable = false),
            )

            val modeTitle = stringResource(R.string.screensaver_mode)
            val currentModeLabel = modeLabels[config.mode] ?: config.mode.name
            val modeValue = SettingValue.Action(currentModeLabel)
            StageSettingRow(
                icon = OwnTVIcon.PALETTE,
                title = modeTitle,
                desc = currentModeLabel,
                value = modeValue,
                onClick = {
                    val idx = modes.indexOf(config.mode)
                    val next = modes[(idx + 1) % modes.size]
                    ScreensaverTvManager.setMode(next)
                },
                help = settingHelp(null, modeTitle, currentModeLabel, modeValue, pinnable = false),
            )

            val cornerClockTitle = stringResource(R.string.screensaver_corner_clock)
            val cornerClockDesc = stringResource(R.string.screensaver_corner_clock_desc)
            val cornerClockValue = SettingValue.Switch(config.showCornerClock)
            StageSettingRow(
                icon = OwnTVIcon.CLOCK,
                title = cornerClockTitle,
                desc = cornerClockDesc,
                value = cornerClockValue,
                onClick = { ScreensaverTvManager.setShowCornerClock(!config.showCornerClock) },
                help = settingHelp(null, cornerClockTitle, cornerClockDesc, cornerClockValue, pinnable = false),
            )

            val previewTitle = stringResource(R.string.screensaver_preview)
            val previewDesc = stringResource(R.string.screensaver_preview_desc)
            val previewValue = SettingValue.Action(previewTitle)
            StageSettingRow(
                icon = OwnTVIcon.PLAY,
                title = previewTitle,
                desc = previewDesc,
                value = previewValue,
                onClick = { ScreensaverTvManager.preview() },
                help = settingHelp(null, previewTitle, previewDesc, previewValue, pinnable = false),
            )
        }
    }
}
