// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.settingHelp
import tv.own.owntv.ui.components.OwnTVIcon

internal val REMOTE_SEARCH_ROWS: List<Int> = listOf(
    tv.own.owntv.core.R.string.remote_title,
    tv.own.owntv.core.R.string.remote_enable_switch,
    tv.own.owntv.core.R.string.remote_pairing_code,
)

@Composable
fun PhoneRemoteSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { RemoteTvManager.get(context) }

    val enabled by manager.enabled.collectAsStateWithLifecycle()
    val serverPort by manager.serverPort.collectAsStateWithLifecycle()
    val currentPin by manager.currentPin.collectAsStateWithLifecycle()
    val pairedDevices by manager.pairedDevices.collectAsStateWithLifecycle()

    var showCertificate by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0L) }
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        while (enabled) { manager.refreshDevices(); seconds = manager.pairingManager.pinRemainingSeconds(); kotlinx.coroutines.delay(1000) }
    }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }

    val title = stringResource(tv.own.owntv.core.R.string.remote_title)
    val desc = stringResource(tv.own.owntv.core.R.string.remote_desc)

    if (showCertificate && enabled) {
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { showCertificate = false },
            title = stringResource(tv.own.owntv.core.R.string.remote_certificate),
            body = stringResource(tv.own.owntv.core.R.string.remote_certificate_hint),
            width = 800.mpx,
            buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(tv.own.owntv.core.R.string.remote_nav_back), onClick = { showCertificate = false }) },
        ) {
            androidx.tv.material3.Text(remember { RemoteTls.fingerprint().chunked(16).joinToString("\n") }, style = stageText(24, 600))
        }
    }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_network)),
        title = title,
        count = pluralStringResource(R.plurals.settings_setting_count, 3 + pairedDevices.size, 3 + pairedDevices.size),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        val enableTitle = stringResource(tv.own.owntv.core.R.string.remote_enable_switch)
        val enableDesc = stringResource(tv.own.owntv.core.R.string.remote_enable_switch_desc)
        val enableValue = SettingValue.Switch(enabled)
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = enableTitle,
            desc = enableDesc,
            value = enableValue,
            onClick = { manager.setEnabled(!enabled) },
            help = settingHelp(null, enableTitle, enableDesc, enableValue, pinnable = false),
        )

        if (enabled) {
            val portText = serverPort?.let { stringResource(tv.own.owntv.core.R.string.remote_tv_status_listening, it) }
                ?: stringResource(tv.own.owntv.core.R.string.remote_connecting)
            val portDesc = stringResource(tv.own.owntv.core.R.string.remote_tv_qr_hint)
            val portValue = SettingValue.Action(serverPort?.toString() ?: "-")
            StageSettingRow(
                icon = OwnTVIcon.NETWORK,
                title = portText,
                desc = portDesc,
                value = portValue,
                onClick = {},
                help = settingHelp(null, portText, portDesc, portValue, pinnable = false),
            )

            val certTitle = stringResource(tv.own.owntv.core.R.string.remote_certificate)
            val certValue = SettingValue.Action(remember { RemoteTls.fingerprint() })
            StageSettingRow(
                icon = OwnTVIcon.LOCK, title = certTitle, desc = stringResource(tv.own.owntv.core.R.string.remote_certificate_hint),
                value = certValue, onClick = { showCertificate = true },
            )
            val pinTitle = stringResource(tv.own.owntv.core.R.string.remote_pairing_code)
            val pinDesc = if (currentPin != null) {
                stringResource(tv.own.owntv.core.R.string.remote_pairing_code_expires, seconds.toString())
            } else {
                stringResource(tv.own.owntv.core.R.string.remote_pairing_code_desc)
            }
            val pinAction = currentPin?.takeIf { manager.pairingManager.pinRemainingSeconds() > 0 } ?: stringResource(tv.own.owntv.core.R.string.remote_get_new_code)
            val pinValue = SettingValue.Action(pinAction)
            StageSettingRow(
                icon = OwnTVIcon.LOCK,
                title = pinTitle,
                desc = pinDesc,
                value = pinValue,
                onClick = { manager.generateNewPin() },
                help = settingHelp(null, pinTitle, pinDesc, pinValue, pinnable = false),
            )

            if (pairedDevices.isEmpty()) {
                val noDevTitle = stringResource(tv.own.owntv.core.R.string.remote_paired_devices)
                val noDevDesc = stringResource(tv.own.owntv.core.R.string.remote_no_paired_devices)
                val noDevValue = SettingValue.Action("-")
                StageSettingRow(
                    icon = OwnTVIcon.PHONE,
                    title = noDevTitle,
                    desc = noDevDesc,
                    value = noDevValue,
                    onClick = {},
                    help = settingHelp(null, noDevTitle, noDevDesc, noDevValue, pinnable = false),
                )
            } else {
                pairedDevices.forEach { device ->
                    val devTitle = device.name
                    val devDesc = stringResource(tv.own.owntv.core.R.string.remote_forget_device)
                    val devValue = SettingValue.Action(devDesc)
                    StageSettingRow(
                        icon = OwnTVIcon.PHONE,
                        title = devTitle,
                        desc = devDesc,
                        value = devValue,
                        onClick = { manager.forgetDevice(device.id) },
                        help = settingHelp(null, devTitle, devDesc, devValue, pinnable = false),
                    )
                }
            }
        }
    }
}
