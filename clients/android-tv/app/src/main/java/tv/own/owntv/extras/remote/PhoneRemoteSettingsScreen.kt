// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.settingHelp
import tv.own.owntv.ui.components.OwnTVIcon

internal val PHONE_REMOTE_SEARCH_ROWS: List<Int> =
    listOf(R.string.remote_tv_settings_title, R.string.remote_pairing_pin_title)

@Composable
fun PhoneRemoteSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled by RemoteTvManager.isEnabled.collectAsStateWithLifecycle()
    val serverState by RemoteTvManager.serverState.collectAsStateWithLifecycle()

    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }

    var currentPin by remember { mutableStateOf<String?>(null) }
    var pairedList by remember { mutableStateOf<List<RemoteDevice>>(emptyList()) }

    LaunchedEffect(enabled, serverState) {
        val pm = RemoteTvManager.pairingManager()
        currentPin = pm?.getCurrentPin()
        pairedList = pm?.getPairedDevices().orEmpty()
    }

    val title = stringResource(R.string.remote_tv_settings_title)
    val desc = stringResource(R.string.remote_tv_settings_desc)
    val switchValue = SettingValue.Switch(enabled)

    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_app)),
        title = title,
        count = pluralStringResource(R.plurals.settings_setting_count, 1 + pairedList.size, 1 + pairedList.size),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = title,
            desc = desc,
            value = switchValue,
            onClick = {
                RemoteTvManager.setEnabled(context, !enabled)
            },
            help = settingHelp(null, title, desc, switchValue, pinnable = false),
        )

        if (enabled) {
            val pinText = currentPin?.let {
                "${it.take(3)} ${it.takeLast(3)}"
            } ?: stringResource(R.string.remote_device_connecting)
            val pinTitle = stringResource(R.string.remote_pairing_pin_title)
            val pinDesc = stringResource(R.string.remote_pairing_pin_desc)
            StageSettingRow(
                icon = OwnTVIcon.LOCK,
                title = pinTitle,
                desc = "$pinText — $pinDesc",
                value = SettingValue.Action(pinText),
                onClick = {},
                help = settingHelp(null, pinTitle, pinDesc, null, pinnable = false),
            )

            pairedList.forEach { dev ->
                val devName = dev.name
                val unpairLabel = stringResource(R.string.remote_unpair)
                StageSettingRow(
                    icon = OwnTVIcon.PHONE,
                    title = devName,
                    desc = unpairLabel,
                    value = SettingValue.Action(unpairLabel),
                    onClick = {
                        RemoteTvManager.pairingManager()?.forgetDevice(dev.token)
                        pairedList = RemoteTvManager.pairingManager()?.getPairedDevices().orEmpty()
                    },
                    help = settingHelp(null, devName, unpairLabel, null, pinnable = false),
                )
            }
        }
    }
}
