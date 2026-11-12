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
    LaunchedEffect(host) { if (!host.isNullOrBlank() && s.host.isBlank()) vm.setHost(host) }
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
            Text(stringResource(R.string.atv_screen_hint), style = stageText(12.5f, 500), color = palette.muted)
            StageField(value = s.host, onValueChange = vm::setHost, label = stringResource(R.string.atv_enter_ip),
                keyboardType = KeyboardType.Decimal, enabled = !s.connecting, onImeDone = vm::connect)
            StageButton(onClick = vm::connect, text = stringResource(R.string.atv_connect), icon = MobileIcons.Tv,
                style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
            if (s.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.atv_screen_allow), style = stageText(12.5f, 600), color = palette.text)
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
        }
        s.image?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.atv_screen_title), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
        if (s.failed) Text(stringResource(R.string.atv_screen_failed), style = stageText(12.5f, 600), color = palette.danger)
    }
}
