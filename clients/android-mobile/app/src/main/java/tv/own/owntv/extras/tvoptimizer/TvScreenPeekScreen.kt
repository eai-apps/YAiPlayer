// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText

/** «Экран приставки» from the remote: a snapshot of the TV's screen, or a picture every two seconds. */
@Composable
fun TvScreenPeekScreen(host: String?, onBack: () -> Unit, vm: TvScreenPeekViewModel = viewModel()) {
    val palette = LocalStagePalette.current
    val s by vm.state.collectAsStateWithLifecycle()
    // Opens by itself: the remote's address, the remembered boxes, then the whole Wi-Fi (owner 12.11).
    LaunchedEffect(host) { vm.autoConnect(host) }
    Column(
        Modifier.fillMaxSize().stageBackground().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StagePill(onClick = onBack, icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.remote_nav_back))
            Text(stringResource(R.string.atv_screen_title), style = stageText(16f, 700), color = palette.text)
        }
        if (!s.connected) {
            if (s.searching || s.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (s.searching) R.string.atv_screen_searching else R.string.atv_screen_allow),
                    style = stageText(13f, 600), color = palette.text)
            } else {
                StageButton(onClick = { vm.autoConnect(host) }, text = stringResource(R.string.atv_connect), icon = MobileIcons.Tv,
                    style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.atv_screen_hint), style = stageText(12.5f, 500), color = palette.muted)
                // Manual address only as a fallback, under everything else.
                StageField(value = s.host, onValueChange = vm::setHost, label = stringResource(R.string.atv_enter_ip),
                    keyboardType = KeyboardType.Decimal, enabled = !s.connecting, onImeDone = vm::connect)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = vm::shoot, text = stringResource(R.string.atv_screen_shoot), icon = MobileIcons.Refresh,
                    style = StageButtonStyle.PRIMARY)
            }
            StageSettingRow(
                title = stringResource(R.string.atv_screen_live),
                subtitle = stringResource(R.string.atv_screen_live_desc),
                onClick = { vm.setLive(!s.live) },
            ) { StageSwitch(s.live) }
            if (s.busy && s.image == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            // When YAiPlayer on the TV box does not start (owner 12.11): read its crash, or install a fresh copy.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = vm::readCrashLog, text = stringResource(R.string.atv_screen_crash_log), icon = MobileIcons.BugReport, small = true)
                StageButton(onClick = vm::installLatestOnTv, text = stringResource(R.string.atv_screen_install), icon = MobileIcons.Download,
                    small = true, enabled = !s.installing)
            }
            // Beta build only (owner 13.11): put the beta TV build on the box over Wi-Fi, next to the normal app.
            if (tv.own.owntv.mobile.BuildConfig.BETA) {
                StageButton(onClick = vm::installBetaOnTv, text = stringResource(R.string.atv_screen_install_beta), icon = MobileIcons.Download,
                    small = true, enabled = !s.installing)
            }
            if (s.installing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.atv_screen_installing), style = stageText(12.5f, 600), color = palette.text)
            }
            s.installResult?.let { r ->
                Text(if (r.isEmpty()) stringResource(R.string.atv_screen_installed) else stringResource(R.string.atv_screen_install_failed, r),
                    style = stageText(12.5f, 600), color = if (r.isEmpty()) palette.ok else palette.danger)
            }
            s.crashLog?.let { log ->
                val context = androidx.compose.ui.platform.LocalContext.current
                StageButton(
                    onClick = {
                        runCatching {
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newPlainText("YAiPlayer TV crash", log))
                        }
                    },
                    text = stringResource(R.string.atv_copy_log), icon = MobileIcons.Share, small = true,
                )
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(log.ifBlank { stringResource(R.string.atv_screen_crash_empty) }.takeLast(6000),
                        style = stageText(10.5f, 500), color = palette.muted)
                }
            }
        }
        s.image?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.atv_screen_title), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
        if (s.failed) {
            val msg = when (s.problem) {
                TvScreenPeekViewModel.Problem.NOT_FOUND -> R.string.atv_screen_not_found
                TvScreenPeekViewModel.Problem.NOT_ALLOWED -> R.string.atv_screen_not_allowed
                TvScreenPeekViewModel.Problem.UNREACHABLE -> R.string.atv_screen_unreachable
                else -> R.string.atv_screen_failed
            }
            Text(stringResource(msg), style = stageText(12.5f, 600), color = palette.danger)
            s.detail?.let { Text(it, style = stageText(11f, 500), color = palette.dim) }
        }
    }
}
