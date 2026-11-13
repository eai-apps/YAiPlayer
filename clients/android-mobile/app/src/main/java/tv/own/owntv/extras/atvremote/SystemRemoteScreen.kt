// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/** Package of the YAiPlayer TV app, launched from the remote's app row. */
private val YAIPLAYER_TV_PACKAGE = if (tv.own.owntv.mobile.BuildConfig.BETA) "com.yai.iptv.player.tv.beta" else "com.yai.iptv.player.tv"

/**
 * App shortcuts on the remote: our own round letter badges (no foreign logos), launched by link or package name.
 * Links are the ones the TV's own apps register for; a bare package opens through `market://launch`.
 */
private data class AppShortcut(val label: String, val target: String, val color: Color)

private val APP_SHORTCUTS = listOf(
    AppShortcut("YAiPlayer", YAIPLAYER_TV_PACKAGE, Color(0xFF7C4DFF)),
    AppShortcut("YouTube", "https://www.youtube.com", Color(0xFFE53935)),
    AppShortcut("Netflix", "https://www.netflix.com/title", Color(0xFFB71C1C)),
    AppShortcut("Prime Video", "https://app.primevideo.com", Color(0xFF1E88E5)),
    AppShortcut("Disney+", "https://www.disneyplus.com", Color(0xFF283593)),
    AppShortcut("Apple TV", "com.apple.atve.androidtv.appletv", Color(0xFF424242)),
    AppShortcut("Max", "com.wbd.stream", Color(0xFF3949AB)),
)

/**
 * Remote for the whole TV box over the Android TV Remote protocol, laid out like the remote apps owners already
 * know (owner 12.11): header with the TV and a connection dot, power, app shortcuts, a D-pad or touchpad that
 * swipes to a page of digits, volume on the left, channels on the right, keyboard / home / pages / mute / voice /
 * back between them. Needs no YAiPlayer on the TV.
 */
@Composable
fun SystemRemoteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    /** «Ускорить ТВ»: a secondary button under the remote (owner 12.11: the remote first, speeding up is optional). */
    onSpeedUp: (() -> Unit)? = null,
    /** «Экран приставки», with the host of the TV in use when there is one. */
    onScreen: ((String?) -> Unit)? = null,
    vm: SystemRemoteViewModel = viewModel(),
) {
    val status by vm.status.collectAsStateWithLifecycle()
    val saved by vm.savedTvs.collectAsStateWithLifecycle()
    val found by vm.foundTvs.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val keyboard by vm.keyboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()
    val probing by vm.probing.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTyping by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(found.size, saved.size) { vm.maybeAutoPair() }
    DisposableEffect(Unit) {
        vm.onScreenStart()
        onDispose { vm.onScreenStop() }
    }

    val connected = status as? SystemRemoteStatus.Connected
    val current = saved.firstOrNull { it.id == currentId }
    val showTvList = saved.isEmpty() && connected == null

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            status = status,
            name = current?.name,
            onBack = onBack,
            onPick = { showPicker = true },
            onSettings = { showSettings = true },
            onPower = { vm.key(AtvKeys.POWER) },
            powerEnabled = connected != null,
        )
        header()
        if (showTvList) {
            TvsPanel(
                found = found,
                onPair = { vm.startPairing(it.serviceName, it.host, it.port) },
                onManual = { showManual = true },
            )
        } else {
            StatusCard(
                status = status,
                probe = probe,
                probing = probing,
                onRetry = { current?.let(vm::connect) },
                onRepair = { current?.let(vm::repair) },
                onCheck = { vm.checkConnection() },
                journal = vm::journal,
            )
        }
        if (!showTvList) {
            Box(Modifier.alpha(if (connected != null) 1f else 0.4f)) {
                RemoteBody(vm, settings, volume, connected != null, onKeyboard = { showTyping = true })
            }
        }
        ExtrasRow(
            onSpeedUp = onSpeedUp,
            onScreen = onScreen?.let { open -> { open(current?.host ?: found.firstOrNull()?.host) } },
        )
    }

    if ((keyboard || showTyping) && connected != null) {
        LiveTypeDialog(
            onType = vm::type,
            onEnter = { vm.key(AtvKeys.ENTER); vm.closeKeyboard(); showTyping = false },
            onDismiss = { vm.closeKeyboard(); showTyping = false },
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.atv_choose_tv), style = stageText(18f, 700), color = LocalStagePalette.current.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    saved.forEach { tv ->
                        TvRow(tv.name, tv.host, stringResource(R.string.atv_connect), primary = tv.id != currentId) {
                            showPicker = false; vm.connect(tv)
                        }
                    }
                    TvsPanel(
                        found = found.filterNot { f -> saved.any { it.name == f.serviceName } },
                        onPair = { showPicker = false; vm.startPairing(it.serviceName, it.host, it.port) },
                        onManual = { showPicker = false; showManual = true },
                        compact = true,
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

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            saved = saved,
            onChange = vm::updateSettings,
            onRename = vm::rename,
            onForget = vm::forget,
            onAdd = { showSettings = false; showPicker = true },
            onLaunch = vm::launch,
            onDismiss = { showSettings = false },
        )
    }

    // First start: how to steer (as in the sample app), shown once the remote itself is on screen.
    if (!settings.chosen && !showTvList && pairing == null) {
        FirstRunDialog(settings, onDone = { vm.updateSettings(it.copy(chosen = true)) })
    }

    pairing?.let {
        PairingDialog(
            it,
            onCode = vm::submitCode,
            onRestart = { vm.startPairing(it.name, it.host, it.remotePort) },
            onCancel = vm::cancelPairing,
            onCheck = { vm.checkConnection(it.host) },
            probe = probe?.takeIf { r -> r.host == it.host },
            probing = probing,
            journal = vm::journal,
        )
    }
}

/** Header: back, the TV with its connection dot and a chooser, settings, power (as in the sample). */
@Composable
private fun TopBar(
    status: SystemRemoteStatus,
    name: String?,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onSettings: () -> Unit,
    onPower: () -> Unit,
    powerEnabled: Boolean,
) {
    val palette = LocalStagePalette.current
    val dot = when (status) {
        is SystemRemoteStatus.Connected -> palette.ok
        is SystemRemoteStatus.Connecting -> palette.warn
        is SystemRemoteStatus.Failed -> palette.danger
        SystemRemoteStatus.NoTv -> palette.dim
    }
    val title = when (status) {
        is SystemRemoteStatus.Connected -> status.name
        is SystemRemoteStatus.Connecting -> status.name
        is SystemRemoteStatus.Failed -> status.name
        SystemRemoteStatus.NoTv -> name ?: stringResource(R.string.atv_title)
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        StagePill(onClick = onBack, icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.remote_nav_back))
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onPick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(title, style = stageText(16f, 700), color = palette.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            StageIcon(MobileIcons.KeyboardArrowDown, 20.dp, palette.muted)
        }
        StagePill(onClick = onSettings, icon = MobileIcons.Settings, contentDescription = stringResource(R.string.atv_settings_title))
        Spacer(Modifier.width(8.dp))
        val description = stringResource(R.string.atv_key_power)
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.danger.copy(alpha = if (powerEnabled) 0.9f else 0.35f))
                .semantics { contentDescription = description }
                .clickable(enabled = powerEnabled, onClick = onPower),
            contentAlignment = Alignment.Center,
        ) { Text("⏻", style = stageText(18f, 800), color = Color.White) }
    }
}

/** «Телевизоры»: TVs found on the Wi-Fi, each with «Соединить» (as in the sample), plus the IP fallback. */
@Composable
private fun TvsPanel(found: List<AtvFoundTv>, onPair: (AtvFoundTv) -> Unit, onManual: () -> Unit, compact: Boolean = false) {
    val palette = LocalStagePalette.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!compact) Text(stringResource(R.string.atv_tvs_title), style = stageText(20f, 800), color = palette.text)
        Text(stringResource(R.string.atv_tvs_hint), style = stageText(12.5f, 500), color = palette.muted)
        found.forEach { tv -> TvRow(tv.serviceName, tv.host, stringResource(R.string.atv_pair_button), primary = true) { onPair(tv) } }
        if (found.isEmpty()) {
            Text(stringResource(R.string.atv_searching), style = stageText(12.5f, 500), color = palette.muted)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!compact) {
                Text(stringResource(R.string.atv_none_found_hint), style = stageText(12f, 500), color = palette.muted)
                NetworkCheck(found = 0)
            }
        }
        StageButton(onClick = onManual, text = stringResource(R.string.atv_enter_ip), icon = MobileIcons.Edit, style = StageButtonStyle.PLAIN)
    }
}

@Composable
private fun TvRow(name: String, host: String, action: String, primary: Boolean, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.ink.copy(alpha = 0.05f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageIcon(MobileIcons.Tv, 22.dp, palette.text)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = stageText(14.5f, 700), color = palette.text, maxLines = 1)
            Text(host, style = stageText(11.5f, 500), color = palette.dim, maxLines = 1)
        }
        StageButton(onClick = onClick, text = action, small = true, style = if (primary) StageButtonStyle.PRIMARY else StageButtonStyle.SECONDARY)
    }
}

/** Connecting / connected / the error with its technical reason, «Проверить связь» and «Скопировать журнал». */
@Composable
private fun StatusCard(
    status: SystemRemoteStatus,
    probe: AtvProbe.Result?,
    probing: Boolean,
    onRetry: () -> Unit,
    onRepair: () -> Unit,
    onCheck: () -> Unit,
    journal: () -> String,
) {
    val palette = LocalStagePalette.current
    when (status) {
        SystemRemoteStatus.NoTv -> return
        is SystemRemoteStatus.Connecting -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.atv_status_connecting, status.name), style = stageText(13f, 600), color = palette.text)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        is SystemRemoteStatus.Connected -> Unit
        is SystemRemoteStatus.Failed -> Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
                .background(palette.danger.copy(alpha = 0.08f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val text = when (status.problem) {
                SystemRemoteProblem.OFFLINE -> stringResource(R.string.atv_problem_offline, status.name)
                SystemRemoteProblem.NEEDS_PAIRING -> stringResource(R.string.atv_problem_needs_pairing, status.name)
                SystemRemoteProblem.CERT_CHANGED -> stringResource(R.string.atv_problem_cert_changed, status.name)
                SystemRemoteProblem.NOT_LOCAL -> stringResource(R.string.atv_problem_not_local)
                SystemRemoteProblem.DISCONNECTED -> stringResource(R.string.atv_problem_disconnected, status.name)
            }
            Text(text, style = stageText(13f, 600), color = palette.danger)
            status.detail?.let { Text(it, style = stageText(11f, 500), color = palette.dim) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = onRetry, text = stringResource(R.string.atv_retry), small = true)
                if (status.problem == SystemRemoteProblem.NEEDS_PAIRING || status.problem == SystemRemoteProblem.CERT_CHANGED) {
                    StageButton(onClick = onRepair, text = stringResource(R.string.atv_pair_again), small = true, style = StageButtonStyle.PRIMARY)
                }
            }
            DiagnosticsBlock(probe, probing, onCheck, journal)
        }
    }
}

/** «Проверить связь» (ports 6467/6466 and the secure connection) and «Скопировать журнал». */
@Composable
private fun DiagnosticsBlock(probe: AtvProbe.Result?, probing: Boolean, onCheck: () -> Unit, journal: () -> String) {
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StageButton(onClick = onCheck, text = stringResource(R.string.atv_check_connection), icon = MobileIcons.Wifi, small = true, enabled = !probing)
        StageButton(
            onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                runCatching { cm?.setPrimaryClip(ClipData.newPlainText("YAiPlayer remote", journal())) }
                copied = true
            },
            text = stringResource(R.string.atv_copy_log),
            icon = MobileIcons.BugReport,
            small = true,
        )
    }
    if (copied) Text(stringResource(R.string.atv_log_copied), style = stageText(11.5f, 600), color = palette.ok)
    if (probing) {
        Text(stringResource(R.string.atv_probe_running), style = stageText(12f, 500), color = palette.muted)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    probe?.let { r ->
        val open = stringResource(R.string.atv_probe_open)
        val closed = stringResource(R.string.atv_probe_closed)
        val silent = stringResource(R.string.atv_probe_no_answer)
        val error = stringResource(R.string.atv_probe_error)
        fun label(p: AtvProbe.Port) = when (p) {
            AtvProbe.Port.OPEN -> open
            AtvProbe.Port.CLOSED -> closed
            AtvProbe.Port.NO_ANSWER -> silent
            AtvProbe.Port.ERROR -> error
        }
        Text(r.host, style = stageText(11.5f, 600), color = palette.dim)
        Text(stringResource(R.string.atv_probe_pairing, label(r.pairing)), style = stageText(12f, 600), color = palette.text)
        Text(stringResource(R.string.atv_probe_remote, label(r.remote)), style = stageText(12f, 600), color = palette.text)
        r.tls?.let {
            Text(
                stringResource(R.string.atv_probe_tls, if (r.tlsOk) stringResource(R.string.atv_probe_tls_ok) else it),
                style = stageText(12f, 600),
                color = if (r.tlsOk) palette.ok else palette.danger,
            )
        }
        val hint = when {
            r.pairing == AtvProbe.Port.NO_ANSWER && r.remote == AtvProbe.Port.NO_ANSWER -> R.string.atv_probe_hint_silent
            r.pairing == AtvProbe.Port.CLOSED && r.remote == AtvProbe.Port.CLOSED -> R.string.atv_probe_hint_closed
            r.pairing == AtvProbe.Port.OPEN && !r.tlsOk -> R.string.atv_probe_hint_tls
            r.pairing == AtvProbe.Port.OPEN -> R.string.atv_probe_hint_ok
            else -> R.string.atv_probe_hint_mixed
        }
        Text(stringResource(hint), style = stageText(12f, 500), color = palette.muted)
    }
}

@Composable
private fun RemoteBody(
    vm: SystemRemoteViewModel,
    settings: SystemRemoteViewModel.Settings,
    volume: Triple<Int, Int, Boolean>?,
    connected: Boolean,
    onKeyboard: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    val context = LocalContext.current
    var voiceMissing by remember { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) vm.text(heard)
    }
    val press: (Int) -> Unit = { code ->
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.key(code)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppsRow(onLaunch = { if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.launch(it) })

        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(260.dp), userScrollEnabled = !settings.touchpad || pager.currentPage == 1) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (page == 0) {
                    if (settings.touchpad) Touchpad(press) else DPad(press, settings.holdRepeats)
                } else {
                    DigitsPage(press)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                Box(Modifier.size(if (pager.currentPage == i) 9.dp else 7.dp).clip(CircleShape)
                    .background(if (pager.currentPage == i) stageAccent.accent else palette.ink.copy(alpha = 0.25f)))
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RockerBar(
                label = stringResource(R.string.atv_vol),
                upText = "+",
                downText = "−",
                upDescription = stringResource(R.string.atv_key_volume_up),
                downDescription = stringResource(R.string.atv_key_volume_down),
                repeat = settings.holdRepeats,
                onUp = { press(AtvKeys.VOLUME_UP) },
                onDown = { press(AtvKeys.VOLUME_DOWN) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundKey(MobileIcons.Keyboard, R.string.atv_key_keyboard) { onKeyboard() }
                    RoundKey(MobileIcons.Home, R.string.remote_nav_home) { press(AtvKeys.HOME) }
                    RoundKey(
                        if (pager.currentPage == 0) MobileIcons.GridView else MobileIcons.Tune,
                        if (pager.currentPage == 0) R.string.atv_key_numbers else R.string.atv_key_dpad,
                    ) { scope.launch { pager.animateScrollToPage(1 - pager.currentPage) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundKey(MobileIcons.VolumeOff, R.string.atv_key_mute) { press(AtvKeys.VOLUME_MUTE) }
                    RoundKey(MobileIcons.Mic, R.string.atv_key_voice) {
                        // The TV opens its search field; what the phone hears is typed into it.
                        if (connected) press(AtvKeys.SEARCH)
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.atv_voice_prompt))
                        voiceMissing = runCatching { voice.launch(intent) }.isFailure
                    }
                    RoundKey(MobileIcons.ArrowBack, R.string.remote_nav_back) { press(AtvKeys.BACK) }
                }
            }
            RockerBar(
                label = stringResource(R.string.atv_ch),
                upText = "∧",
                downText = "∨",
                upDescription = stringResource(R.string.atv_key_channel_up),
                downDescription = stringResource(R.string.atv_key_channel_down),
                repeat = settings.holdRepeats,
                onUp = { press(AtvKeys.CHANNEL_UP) },
                onDown = { press(AtvKeys.CHANNEL_DOWN) },
            )
        }
        if (voiceMissing) Text(stringResource(R.string.atv_voice_missing), style = stageText(12f, 600), color = palette.danger)
        volume?.let { (level, max, muted) ->
            Text(
                if (muted) stringResource(R.string.atv_muted) else stringResource(R.string.atv_volume_level, level, max),
                style = stageText(12f, 500),
                color = palette.muted,
            )
        }

        // Media keys under the main block.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(MobileIcons.SkipPrevious, R.string.atv_key_previous) { press(AtvKeys.MEDIA_PREVIOUS) }
            RoundKey(MobileIcons.FastRewind, R.string.atv_key_rewind) { press(AtvKeys.MEDIA_REWIND) }
            RoundKey(MobileIcons.PlayArrow, R.string.atv_key_play_pause, primary = true) { press(AtvKeys.MEDIA_PLAY_PAUSE) }
            RoundKey(MobileIcons.FastForward, R.string.atv_key_fast_forward) { press(AtvKeys.MEDIA_FAST_FORWARD) }
            RoundKey(MobileIcons.SkipNext, R.string.atv_key_next) { press(AtvKeys.MEDIA_NEXT) }
        }
    }
}

@Composable
private fun AppsRow(onLaunch: (String) -> Unit) {
    val palette = LocalStagePalette.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        APP_SHORTCUTS.forEach { app ->
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onLaunch(app.target) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(app.color), contentAlignment = Alignment.Center) {
                    Text(app.label.take(1), style = stageText(20f, 800), color = Color.White)
                }
                Text(app.label, style = stageText(10.5f, 600), color = palette.muted, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Repeats [action] while the finger stays down (after a short pause), like a physical remote; one press otherwise. */
@Composable
private fun Modifier.holdToRepeat(repeat: Boolean, action: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(action)
    return this.pointerInput(repeat) {
        detectTapGestures(onPress = {
            current()
            if (repeat) {
                val job = scope.launch {
                    delay(400)
                    while (isActive) {
                        current()
                        delay(110)
                    }
                }
                tryAwaitRelease()
                job.cancel()
            }
        })
    }
}

@Composable
private fun DPad(press: (Int) -> Unit, repeat: Boolean) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    Box(
        Modifier.size(240.dp).clip(CircleShape).background(palette.ink.copy(alpha = 0.06f))
            .border(1.dp, palette.ink.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        PadArrow(R.string.remote_nav_up, Modifier.align(Alignment.TopCenter), 270f, repeat) { press(AtvKeys.DPAD_UP) }
        PadArrow(R.string.remote_nav_down, Modifier.align(Alignment.BottomCenter), 90f, repeat) { press(AtvKeys.DPAD_DOWN) }
        PadArrow(R.string.remote_nav_left, Modifier.align(Alignment.CenterStart), 180f, repeat) { press(AtvKeys.DPAD_LEFT) }
        PadArrow(R.string.remote_nav_right, Modifier.align(Alignment.CenterEnd), 0f, repeat) { press(AtvKeys.DPAD_RIGHT) }
        val ok = stringResource(R.string.remote_nav_ok)
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(accent.accent)
                .semantics { contentDescription = ok }
                .clickable { press(AtvKeys.DPAD_CENTER) },
            contentAlignment = Alignment.Center,
        ) { Text(ok, style = stageText(18f, 800), color = accent.onAccent) }
    }
}

@Composable
private fun PadArrow(label: Int, modifier: Modifier, rotation: Float, repeat: Boolean, onPress: () -> Unit) {
    val palette = LocalStagePalette.current
    val description = stringResource(label)
    Box(
        modifier.size(72.dp).clip(CircleShape)
            .semantics { contentDescription = description }
            .holdToRepeat(repeat, onPress),
        contentAlignment = Alignment.Center,
    ) { StageIcon(MobileIcons.KeyboardArrowRight, 34.dp, palette.text, Modifier.rotate(rotation)) }
}

/** Touchpad: a swipe moves one step per ~48 dp in its main direction, a tap is OK (owner 12.11, as in the sample). */
@Composable
private fun Touchpad(press: (Int) -> Unit) {
    val palette = LocalStagePalette.current
    val current by rememberUpdatedState(press)
    Box(
        Modifier.size(260.dp, 240.dp).clip(RoundedCornerShape(28.dp)).background(palette.ink.copy(alpha = 0.06f))
            .border(1.dp, palette.ink.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { current(AtvKeys.DPAD_CENTER) }) }
            .pointerInput(Unit) {
                val step = 48.dp.toPx()
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                    change.consume()
                    acc += drag
                    while (abs(acc.x) >= step || abs(acc.y) >= step) {
                        if (abs(acc.x) >= abs(acc.y)) {
                            current(if (acc.x > 0) AtvKeys.DPAD_RIGHT else AtvKeys.DPAD_LEFT)
                            acc = acc.copy(x = acc.x - (if (acc.x > 0) step else -step))
                        } else {
                            current(if (acc.y > 0) AtvKeys.DPAD_DOWN else AtvKeys.DPAD_UP)
                            acc = acc.copy(y = acc.y - (if (acc.y > 0) step else -step))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.atv_layout_touchpad_desc), style = stageText(12.5f, 500), color = palette.dim, modifier = Modifier.padding(24.dp))
    }
}

/** Second page: 0–9, «TV» and «Menu» (as in the sample). */
@Composable
private fun DigitsPage(press: (Int) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("TV", "0", "Menu"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    val code = when (key) {
                        "TV" -> AtvKeys.TV
                        "Menu" -> AtvKeys.MENU
                        else -> AtvKeys.digit(key.toInt())
                    }
                    val text = when (key) {
                        "TV" -> stringResource(R.string.atv_key_tv)
                        "Menu" -> stringResource(R.string.remote_nav_menu)
                        else -> key
                    }
                    StageButton(onClick = { press(code) }, text = text, modifier = Modifier.width(78.dp))
                }
            }
        }
    }
}

@Composable
private fun RockerBar(
    label: String,
    upText: String,
    downText: String,
    upDescription: String,
    downDescription: String,
    repeat: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val palette = LocalStagePalette.current
    Column(
        Modifier.width(60.dp).clip(RoundedCornerShape(30.dp)).background(palette.ink.copy(alpha = 0.07f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(60.dp).semantics { contentDescription = upDescription }.holdToRepeat(repeat, onUp), contentAlignment = Alignment.Center) {
            Text(upText, style = stageText(22f, 800), color = palette.text)
        }
        Text(label, style = stageText(11f, 800), color = palette.muted)
        Box(Modifier.size(60.dp).semantics { contentDescription = downDescription }.holdToRepeat(repeat, onDown), contentAlignment = Alignment.Center) {
            Text(downText, style = stageText(22f, 800), color = palette.text)
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: Int, primary: Boolean = false, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val description = stringResource(label)
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(if (primary) accent.accent else palette.ink.copy(alpha = 0.07f))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { StageIcon(icon, 24.dp, if (primary) accent.onAccent else palette.text) }
}

/** The TV asked for text (or the keyboard key was pressed): every letter goes to the TV as it is typed. */
@Composable
private fun LiveTypeDialog(onType: (String, String) -> Unit, onEnter: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalStagePalette.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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

/** Under the remote: «Ускорить ТВ» and «Экран приставки», secondary tools. */
@Composable
private fun ExtrasRow(onSpeedUp: (() -> Unit)?, onScreen: (() -> Unit)?) {
    if (onSpeedUp == null && onScreen == null) return
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        onSpeedUp?.let { StageButton(onClick = it, text = stringResource(R.string.atv_speed_up), icon = MobileIcons.Bolt, small = true) }
        onScreen?.let { StageButton(onClick = it, text = stringResource(R.string.atv_screen_button), icon = MobileIcons.Tv, small = true) }
    }
}

/** The phone's side of "why is nothing found": Wi-Fi, VPN, its own address, how many TVs answered. */
@Composable
private fun NetworkCheck(found: Int) {
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    val check = remember { AtvNetworkCheck.read(context) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.atv_check_title), style = stageText(12.5f, 700), color = palette.text)
        val yes = stringResource(R.string.atv_check_yes)
        val no = stringResource(R.string.atv_check_no)
        Text(stringResource(R.string.atv_check_wifi, if (check.wifi) yes else no), style = stageText(12f, 500), color = palette.muted)
        Text(stringResource(R.string.atv_check_vpn, if (check.vpn) yes else no), style = stageText(12f, 500), color = palette.muted)
        Text(stringResource(R.string.atv_check_address, check.addresses.joinToString().ifBlank { "—" }), style = stageText(12f, 500), color = palette.muted)
        Text(stringResource(R.string.atv_check_found, found), style = stageText(12f, 500), color = palette.muted)
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
private fun PairingDialog(
    p: SystemRemotePairing,
    onCode: (String) -> Unit,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    onCheck: () -> Unit,
    probe: AtvProbe.Result?,
    probing: Boolean,
    journal: () -> String,
) {
    val palette = LocalStagePalette.current
    var code by remember(p.host) { mutableStateOf("") }
    val ready = AtvPairingCode.normalize(code) != null
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.atv_pair_title, p.name), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            textStyle = stageText(22f, 800),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (ready) onCode(code) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (p.step == SystemRemotePairing.Step.CHECKING) {
                            Text(stringResource(R.string.atv_pair_checking), style = stageText(12.5f, 500), color = palette.muted)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            Text(
                                stringResource(R.string.atv_pair_resend),
                                style = stageText(12.5f, 700),
                                color = stageAccent.accent,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRestart).padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                p.error?.let { e ->
                    val msg = when (e) {
                        SystemRemotePairing.Error.CANNOT_REACH -> R.string.atv_pair_error_reach
                        SystemRemotePairing.Error.SECURE_FAILED -> R.string.atv_pair_error_secure
                        SystemRemotePairing.Error.TV_REFUSED -> R.string.atv_pair_error_refused
                        SystemRemotePairing.Error.TYPO -> R.string.atv_pair_error_typo
                        SystemRemotePairing.Error.REJECTED -> R.string.atv_pair_error_rejected
                        SystemRemotePairing.Error.FAILED -> R.string.atv_pair_error_failed
                    }
                    Text(stringResource(msg), style = stageText(13f, 600), color = palette.danger)
                    p.detail?.let { Text(it, style = stageText(11f, 500), color = palette.dim) }
                    if (e != SystemRemotePairing.Error.TYPO) DiagnosticsBlock(probe, probing, onCheck, journal)
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
private fun ToggleRow(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(14f, 700), color = palette.text)
            Text(description, style = stageText(12f, 500), color = palette.muted)
        }
        Spacer(Modifier.width(10.dp))
        StageSwitch(checked)
    }
}

@Composable
private fun LayoutChoice(touchpad: Boolean, onChange: (Boolean) -> Unit) {
    StageSegmented(
        options = listOf(stringResource(R.string.atv_layout_dpad), stringResource(R.string.atv_layout_touchpad)),
        selected = if (touchpad) 1 else 0,
        onSelect = { onChange(it == 1) },
        fill = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** First start: D-pad or touchpad, vibration, hold-to-repeat (owner 12.11, as in the sample). */
@Composable
private fun FirstRunDialog(initial: SystemRemoteViewModel.Settings, onDone: (SystemRemoteViewModel.Settings) -> Unit) {
    val palette = LocalStagePalette.current
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(s) },
        title = { Text(stringResource(R.string.atv_first_title), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LayoutChoice(s.touchpad) { s = s.copy(touchpad = it) }
                ToggleRow(stringResource(R.string.atv_haptics), stringResource(R.string.atv_haptics_desc), s.haptics) { s = s.copy(haptics = it) }
                ToggleRow(stringResource(R.string.atv_hold_repeat), stringResource(R.string.atv_hold_repeat_desc), s.holdRepeats) { s = s.copy(holdRepeats = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(s) }) { Text(stringResource(R.string.atv_done)) } },
    )
}

/** Settings: layout, vibration, hold-to-repeat, the paired TVs with «Редактировать», opening an app by name. */
@Composable
private fun SettingsDialog(
    settings: SystemRemoteViewModel.Settings,
    saved: List<AtvSavedTv>,
    onChange: (SystemRemoteViewModel.Settings) -> Unit,
    onRename: (AtvSavedTv, String) -> Unit,
    onForget: (AtvSavedTv) -> Unit,
    onAdd: () -> Unit,
    onLaunch: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val palette = LocalStagePalette.current
    var editing by remember { mutableStateOf<AtvSavedTv?>(null) }
    var app by rememberSaveable { mutableStateOf("") }
    var appError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.atv_settings_title), style = stageText(18f, 700), color = palette.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.atv_layout_title), style = stageText(13f, 700), color = palette.text)
                LayoutChoice(settings.touchpad) { onChange(settings.copy(touchpad = it)) }
                ToggleRow(stringResource(R.string.atv_haptics), stringResource(R.string.atv_haptics_desc), settings.haptics) {
                    onChange(settings.copy(haptics = it))
                }
                ToggleRow(stringResource(R.string.atv_hold_repeat), stringResource(R.string.atv_hold_repeat_desc), settings.holdRepeats) {
                    onChange(settings.copy(holdRepeats = it))
                }
                Text(stringResource(R.string.atv_devices), style = stageText(13f, 700), color = palette.text)
                saved.forEach { tv -> TvRow(tv.name, tv.host, stringResource(R.string.atv_edit), primary = false) { editing = tv } }
                StageButton(onClick = onAdd, text = stringResource(R.string.atv_add_tv), icon = MobileIcons.Add, small = true)
                Text(stringResource(R.string.atv_open_app_title), style = stageText(13f, 700), color = palette.text)
                StageSearchField(
                    value = app,
                    onValueChange = { app = it; appError = false },
                    placeholder = stringResource(R.string.atv_app_custom_label),
                    icon = null,
                    imeAction = ImeAction.Go,
                    onSearch = { appError = !onLaunch(app) },
                )
                if (appError) Text(stringResource(R.string.atv_app_bad), style = stageText(12f, 600), color = palette.danger)
                StageButton(onClick = { appError = !onLaunch(app) }, text = stringResource(R.string.atv_open), small = true, enabled = app.isNotBlank())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.atv_done)) } },
    )
    editing?.let { tv ->
        var name by remember(tv.id) { mutableStateOf(tv.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.atv_edit), style = stageText(18f, 700), color = palette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.atv_rename_label)) }, singleLine = true)
                    Text(tv.host, style = stageText(12f, 500), color = palette.dim)
                    StageButton(onClick = { onForget(tv); editing = null }, text = stringResource(R.string.atv_forget), style = StageButtonStyle.DANGER, small = true)
                }
            },
            confirmButton = { TextButton(onClick = { onRename(tv, name); editing = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.atv_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.atv_cancel)) } },
        )
    }
}
