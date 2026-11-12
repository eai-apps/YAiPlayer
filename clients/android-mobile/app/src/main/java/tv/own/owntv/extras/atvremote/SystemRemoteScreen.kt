// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/** Package of the YAiPlayer TV app, launched from the remote's app row. */
private const val YAIPLAYER_TV_PACKAGE = "com.yai.iptv.player.tv"
private const val YOUTUBE_LINK = "https://www.youtube.com"

/**
 * Remote for the whole TV box over the Android TV Remote protocol: finds TVs on the Wi-Fi, pairs once with the code
 * on the TV screen, remembers them, and sends system keys, text and app launches. Needs no YAiPlayer on the TV.
 */
@Composable
fun SystemRemoteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    /** «Ускорить ТВ»: a round accent button under the remote (owner 12.11: the remote first, speeding up is optional). */
    onSpeedUp: (() -> Unit)? = null,
    /** «Экран приставки», with the host of the TV in use when there is one. */
    onScreen: ((String?) -> Unit)? = null,
    vm: SystemRemoteViewModel = viewModel(),
) {
    val palette = LocalStagePalette.current
    val status by vm.status.collectAsStateWithLifecycle()
    val saved by vm.savedTvs.collectAsStateWithLifecycle()
    val found by vm.foundTvs.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val keyboard by vm.keyboard.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(found.size, saved.size) { vm.maybeAutoPair() }
    DisposableEffect(Unit) {
        vm.onScreenStart()
        onDispose { vm.onScreenStop() }
    }

    val connected = status as? SystemRemoteStatus.Connected
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StagePill(onClick = onBack, icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.remote_nav_back))
            Text(stringResource(R.string.atv_title), style = stageText(16f, 700), color = palette.text)
            StagePill(onClick = { showPicker = true }, icon = MobileIcons.Tv, contentDescription = stringResource(R.string.atv_choose_tv))
        }
        header()
        StatusLine(status, onRetry = { saved.firstOrNull { it.id == currentId }?.let(vm::connect) }, onRepair = {
            saved.firstOrNull { it.id == currentId }?.let(vm::repair)
        })
        // The remote itself opens at once, dimmed until the TV answers (owner 12.11: remote first, then connect).
        if (status is SystemRemoteStatus.NoTv || status is SystemRemoteStatus.Failed) {
            Spacer(Modifier.height(8.dp))
            TvList(
                saved = saved,
                found = found,
                onConnect = vm::connect,
                onPair = { vm.startPairing(it.serviceName, it.host, it.port) },
                onForget = vm::forget,
                onManual = { showManual = true },
            )
        }
        connected?.let {
            volume?.let { (level, max, muted) ->
                Text(
                    if (muted) stringResource(R.string.atv_muted) else stringResource(R.string.atv_volume_level, level, max),
                    style = stageText(12f, 500),
                    color = palette.muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Box(Modifier.alpha(if (connected != null) 1f else 0.35f)) { RemotePad(vm) }
        ExtrasRow(
            onSpeedUp = onSpeedUp,
            onScreen = onScreen?.let { open -> { open(saved.firstOrNull { it.id == currentId }?.host ?: found.firstOrNull()?.host) } },
        )
    }

    if (keyboard && connected != null) {
        LiveTypeDialog(onType = vm::type, onEnter = { vm.key(AtvKeys.ENTER); vm.closeKeyboard() }, onDismiss = vm::closeKeyboard)
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.atv_choose_tv), style = stageText(18f, 700), color = palette.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TvList(
                        saved = saved,
                        found = found,
                        onConnect = { showPicker = false; vm.connect(it) },
                        onPair = { showPicker = false; vm.startPairing(it.serviceName, it.host, it.port) },
                        onForget = vm::forget,
                        onManual = { showPicker = false; showManual = true },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.atv_cancel)) } },
        )
    }

    if (showManual) ManualIpDialog(onDismiss = { showManual = false }, onConnect = { ip ->
        showManual = false
        vm.startPairing(ip, ip)
    })

    pairing?.let { PairingDialog(it, onCode = vm::submitCode, onRestart = { vm.startPairing(it.name, it.host, it.remotePort) }, onCancel = vm::cancelPairing) }
}

@Composable
private fun StatusLine(status: SystemRemoteStatus, onRetry: () -> Unit, onRepair: () -> Unit) {
    val palette = LocalStagePalette.current
    val text = when (status) {
        SystemRemoteStatus.NoTv -> return
        is SystemRemoteStatus.Connecting -> stringResource(R.string.atv_status_connecting, status.name)
        is SystemRemoteStatus.Connected -> stringResource(R.string.atv_status_connected, listOf(status.name, status.model).filter { it.isNotBlank() }.distinct().joinToString(" · "))
        is SystemRemoteStatus.Failed -> when (status.problem) {
            SystemRemoteProblem.OFFLINE -> stringResource(R.string.atv_problem_offline, status.name)
            SystemRemoteProblem.NEEDS_PAIRING -> stringResource(R.string.atv_problem_needs_pairing, status.name)
            SystemRemoteProblem.CERT_CHANGED -> stringResource(R.string.atv_problem_cert_changed, status.name)
            SystemRemoteProblem.NOT_LOCAL -> stringResource(R.string.atv_problem_not_local)
            SystemRemoteProblem.DISCONNECTED -> stringResource(R.string.atv_problem_disconnected, status.name)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = stageText(13f, 600), color = if (status is SystemRemoteStatus.Failed) palette.danger else palette.text)
        (status as? SystemRemoteStatus.Failed)?.detail?.let { Text(it, style = stageText(11f, 500), color = palette.dim) }
        if (status is SystemRemoteStatus.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        if (status is SystemRemoteStatus.Failed) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = onRetry, text = stringResource(R.string.atv_retry), small = true)
                if (status.problem == SystemRemoteProblem.NEEDS_PAIRING || status.problem == SystemRemoteProblem.CERT_CHANGED) {
                    StageButton(onClick = onRepair, text = stringResource(R.string.atv_pair_again), small = true, style = StageButtonStyle.PRIMARY)
                }
            }
        }
    }
}

@Composable
private fun TvList(
    saved: List<AtvSavedTv>,
    found: List<AtvFoundTv>,
    onConnect: (AtvSavedTv) -> Unit,
    onPair: (AtvFoundTv) -> Unit,
    onForget: (AtvSavedTv) -> Unit,
    onManual: () -> Unit,
) {
    val palette = LocalStagePalette.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (saved.isNotEmpty()) {
            Text(stringResource(R.string.atv_my_tvs), style = stageText(13f, 700), color = palette.text)
            saved.forEach { tv ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StageButton(onClick = { onConnect(tv) }, text = tv.name, icon = MobileIcons.Tv, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    StageButton(onClick = { onForget(tv) }, icon = MobileIcons.Delete, small = true)
                }
            }
        }
        Text(stringResource(R.string.atv_found_tvs), style = stageText(13f, 700), color = palette.text)
        val savedNames = saved.map { it.name }.toSet()
        val fresh = found.filterNot { it.serviceName in savedNames }
        if (fresh.isEmpty()) {
            Text(stringResource(R.string.atv_searching), style = stageText(12.5f, 500), color = palette.muted)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.atv_none_found_hint), style = stageText(12f, 500), color = palette.muted)
            NetworkCheck(found = found.size)
        }
        fresh.forEach { tv ->
            StageButton(onClick = { onPair(tv) }, text = tv.serviceName, icon = MobileIcons.Add, modifier = Modifier.fillMaxWidth())
        }
        StageButton(onClick = onManual, text = stringResource(R.string.atv_enter_ip), icon = MobileIcons.Edit, style = StageButtonStyle.PLAIN)
    }
}

/** The TV asked for text: the phone's own keyboard opens and every letter goes to the TV as it is typed. */
@Composable
private fun LiveTypeDialog(onType: (String, String) -> Unit, onEnter: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalStagePalette.current
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.atv_type_title), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.atv_type_hint), style = stageText(13f, 500), color = palette.muted)
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> onType(text, new); text = new },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onEnter() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = onEnter) { Text(stringResource(R.string.atv_type_enter)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.atv_type_close)) } },
    )
}

/** Under the remote: the round «Ускорить ТВ» (accent colour, so it reads as a separate tool) and «Экран приставки». */
@Composable
private fun ExtrasRow(onSpeedUp: (() -> Unit)?, onScreen: (() -> Unit)?) {
    if (onSpeedUp == null && onScreen == null) return
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        onSpeedUp?.let { open ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accent).clickable(onClick = open),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(MobileIcons.Bolt, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White) }
                Text(stringResource(R.string.atv_speed_up), style = stageText(12f, 700), color = palette.text, modifier = Modifier.padding(top = 6.dp))
            }
        }
        onScreen?.let { open ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(palette.cardSolid).clickable(onClick = open),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(MobileIcons.Tv, contentDescription = null, tint = palette.text) }
                Text(stringResource(R.string.atv_screen_button), style = stageText(12f, 700), color = palette.text, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** The phone's side of "why is nothing found": Wi-Fi, VPN, its own address, how many TVs answered (owner 12.11). */
@Composable
private fun NetworkCheck(found: Int) {
    val palette = LocalStagePalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val check = remember { tv.own.owntv.extras.atvremote.AtvNetworkCheck.read(context) }
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.atv_check_title), style = stageText(12.5f, 700), color = palette.text)
        val yes = stringResource(R.string.atv_check_yes)
        val no = stringResource(R.string.atv_check_no)
        Text(stringResource(R.string.atv_check_wifi, if (check.wifi) yes else no), style = stageText(12f, 500), color = palette.muted)
        Text(stringResource(R.string.atv_check_vpn, if (check.vpn) yes else no), style = stageText(12f, 500),
            color = if (check.vpn) palette.danger else palette.muted)
        Text(stringResource(R.string.atv_check_address, check.addresses.joinToString().ifBlank { "—" }), style = stageText(12f, 500), color = palette.muted)
        Text(stringResource(R.string.atv_check_found, found), style = stageText(12f, 500), color = palette.muted)
        if (check.vpn) Text(stringResource(R.string.atv_check_vpn_hint), style = stageText(12f, 600), color = palette.danger)
        if (!check.wifi) Text(stringResource(R.string.atv_check_wifi_hint), style = stageText(12f, 600), color = palette.danger)
    }
}

@Composable
private fun ManualIpDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    val palette = LocalStagePalette.current
    var ip by rememberSaveable { mutableStateOf("") }
    val valid = ip.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) && ip.split('.').all { it.toInt() in 0..255 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.atv_enter_ip), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.atv_ip_hint), style = stageText(13f, 500), color = palette.muted)
                OutlinedTextField(
                    value = ip,
                    onValueChange = { v -> ip = v.filter { it.isDigit() || it == '.' }.take(15) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConnect(ip) }, enabled = valid) { Text(stringResource(R.string.atv_connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.atv_cancel)) } },
    )
}

@Composable
private fun PairingDialog(p: SystemRemotePairing, onCode: (String) -> Unit, onRestart: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalStagePalette.current
    var code by remember(p.host) { mutableStateOf("") }
    val ready = AtvPairingCode.normalize(code) != null
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.atv_pair_title, p.name), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (p.step) {
                    SystemRemotePairing.Step.OPENING -> if (p.error == null) {
                        Text(stringResource(R.string.atv_pair_opening), style = stageText(13.5f, 500), color = palette.muted)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    SystemRemotePairing.Step.ENTER_CODE, SystemRemotePairing.Step.CHECKING -> {
                        Text(stringResource(R.string.atv_pair_enter_code), style = stageText(13.5f, 500), color = palette.muted)
                        OutlinedTextField(
                            value = code,
                            onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                            label = { Text(stringResource(R.string.atv_pair_code_label)) },
                            singleLine = true,
                            enabled = p.step == SystemRemotePairing.Step.ENTER_CODE,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (p.step == SystemRemotePairing.Step.CHECKING) {
                            Text(stringResource(R.string.atv_pair_checking), style = stageText(12.5f, 500), color = palette.muted)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                p.error?.let { e ->
                    val msg = when (e) {
                        SystemRemotePairing.Error.CANNOT_REACH -> R.string.atv_pair_error_reach
                        SystemRemotePairing.Error.TYPO -> R.string.atv_pair_error_typo
                        SystemRemotePairing.Error.REJECTED -> R.string.atv_pair_error_rejected
                        SystemRemotePairing.Error.FAILED -> R.string.atv_pair_error_failed
                    }
                    Text(stringResource(msg), style = stageText(13f, 600), color = palette.danger)
                    p.detail?.let { Text(it, style = stageText(11f, 500), color = palette.dim) }
                }
            }
        },
        confirmButton = {
            if (p.step == SystemRemotePairing.Step.OPENING && p.error != null) {
                TextButton(onClick = onRestart) { Text(stringResource(R.string.atv_pair_start_again)) }
            } else {
                TextButton(onClick = { onCode(code) }, enabled = ready && p.step == SystemRemotePairing.Step.ENTER_CODE) {
                    Text(stringResource(R.string.atv_connect))
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.atv_cancel)) } },
    )
}

@Composable
private fun RemotePad(vm: SystemRemoteViewModel) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val haptic = LocalHapticFeedback.current
    var showNumbers by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var app by rememberSaveable { mutableStateOf("") }
    var appError by remember { mutableStateOf(false) }
    fun press(code: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.key(code)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StageButton(onClick = { press(AtvKeys.POWER) }, text = stringResource(R.string.atv_key_power), style = StageButtonStyle.DANGER, small = true)
            StageButton(onClick = { press(AtvKeys.TV_INPUT) }, text = stringResource(R.string.atv_key_input), small = true)
            StageButton(onClick = { press(AtvKeys.SETTINGS) }, icon = MobileIcons.Settings, small = true)
        }
        Text(stringResource(R.string.atv_power_hint), style = stageText(11f, 500), color = palette.dim)

        // D-pad
        Box(
            Modifier.size(232.dp).clip(CircleShape).background(palette.ink.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            PadKey(MobileIcons.KeyboardArrowUp, R.string.remote_nav_up, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) { press(AtvKeys.DPAD_UP) }
            PadKey(MobileIcons.KeyboardArrowDown, R.string.remote_nav_down, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) { press(AtvKeys.DPAD_DOWN) }
            PadKey(MobileIcons.KeyboardArrowRight, R.string.remote_nav_left, Modifier.align(Alignment.CenterStart).padding(start = 10.dp), rotation = 180f) { press(AtvKeys.DPAD_LEFT) }
            PadKey(MobileIcons.KeyboardArrowRight, R.string.remote_nav_right, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { press(AtvKeys.DPAD_RIGHT) }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(accent.accent).clickable { press(AtvKeys.DPAD_CENTER) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.remote_nav_ok), style = stageText(16f, 800), color = accent.onAccent)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledKey(MobileIcons.ArrowBack, R.string.remote_nav_back) { press(AtvKeys.BACK) }
            LabeledKey(MobileIcons.Home, R.string.remote_nav_home) { press(AtvKeys.HOME) }
            LabeledKey(MobileIcons.MoreHoriz, R.string.remote_nav_menu) { press(AtvKeys.MENU) }
            StageButton(
                onClick = { showNumbers = !showNumbers },
                text = stringResource(R.string.remote_numpad),
                style = if (showNumbers) StageButtonStyle.PRIMARY else StageButtonStyle.SECONDARY,
            )
        }

        if (showNumbers) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -1)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { n ->
                        if (n >= 0) StageButton(onClick = { press(AtvKeys.digit(n)) }, text = n.toString(), modifier = Modifier.width(64.dp))
                        else Spacer(Modifier.width(64.dp))
                    }
                }
            }
        }

        // Volume and channels
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            LabeledKey(MobileIcons.VolumeOff, R.string.atv_key_mute) { press(AtvKeys.VOLUME_MUTE) }
            StageButton(onClick = { press(AtvKeys.VOLUME_DOWN) }, text = stringResource(R.string.atv_key_volume_down), small = true)
            StageButton(onClick = { press(AtvKeys.VOLUME_UP) }, text = stringResource(R.string.atv_key_volume_up), icon = MobileIcons.VolumeUp, small = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StageButton(onClick = { press(AtvKeys.CHANNEL_DOWN) }, text = stringResource(R.string.atv_key_channel_down), small = true)
            StageButton(onClick = { press(AtvKeys.GUIDE) }, text = stringResource(R.string.atv_key_guide), icon = MobileIcons.Guide, small = true)
            StageButton(onClick = { press(AtvKeys.CHANNEL_UP) }, text = stringResource(R.string.atv_key_channel_up), small = true)
        }

        // Media
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledKey(MobileIcons.SkipPrevious, R.string.atv_key_previous) { press(AtvKeys.MEDIA_PREVIOUS) }
            LabeledKey(MobileIcons.FastRewind, R.string.atv_key_rewind) { press(AtvKeys.MEDIA_REWIND) }
            LabeledKey(MobileIcons.PlayArrow, R.string.atv_key_play_pause, primary = true) { press(AtvKeys.MEDIA_PLAY_PAUSE) }
            LabeledKey(MobileIcons.FastForward, R.string.atv_key_fast_forward) { press(AtvKeys.MEDIA_FAST_FORWARD) }
            LabeledKey(MobileIcons.SkipNext, R.string.atv_key_next) { press(AtvKeys.MEDIA_NEXT) }
        }

        // Text
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.atv_text_label), style = stageText(13f, 700), color = palette.text)
            Text(stringResource(R.string.atv_text_hint), style = stageText(11.5f, 500), color = palette.muted)
            StageSearchField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.atv_text_label),
                icon = null,
                imeAction = ImeAction.Send,
                onSearch = { vm.text(text); text = "" },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = { vm.text(text); text = "" }, text = stringResource(R.string.atv_send), style = StageButtonStyle.PRIMARY, small = true, enabled = text.isNotEmpty())
                StageButton(onClick = { press(AtvKeys.DEL) }, text = stringResource(R.string.atv_key_delete), small = true)
                StageButton(onClick = { press(AtvKeys.ENTER) }, text = stringResource(R.string.atv_key_enter), small = true)
            }
        }

        // Apps
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.atv_apps), style = stageText(13f, 700), color = palette.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = { vm.launch(YAIPLAYER_TV_PACKAGE) }, text = stringResource(R.string.atv_app_yaiplayer), icon = MobileIcons.LiveTv, small = true)
                StageButton(onClick = { vm.launch(YOUTUBE_LINK) }, text = stringResource(R.string.atv_app_youtube), icon = MobileIcons.PlayCircle, small = true)
            }
            StageSearchField(
                value = app,
                onValueChange = { app = it; appError = false },
                placeholder = stringResource(R.string.atv_app_custom_label),
                icon = null,
                imeAction = ImeAction.Go,
                isError = appError,
                onSearch = { appError = !vm.launch(app) },
            )
            if (appError) Text(stringResource(R.string.atv_app_bad), style = stageText(12f, 600), color = palette.danger)
            StageButton(onClick = { appError = !vm.launch(app) }, text = stringResource(R.string.atv_open), small = true, enabled = app.isNotBlank())
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PadKey(icon: ImageVector, label: Int, modifier: Modifier, rotation: Float = 0f, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val description = stringResource(label)
    Box(
        modifier.size(54.dp).clip(CircleShape).background(palette.controlFill)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StageIcon(icon, 28.dp, palette.text, Modifier.rotate(rotation))
    }
}

@Composable
private fun LabeledKey(icon: ImageVector, label: Int, primary: Boolean = false, onClick: () -> Unit) {
    val description = stringResource(label)
    StageButton(
        onClick = onClick,
        icon = icon,
        style = if (primary) StageButtonStyle.PRIMARY else StageButtonStyle.SECONDARY,
        modifier = Modifier.semantics { contentDescription = description },
    )
}
