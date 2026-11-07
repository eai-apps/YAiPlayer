package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.core.gate.GateState
import tv.own.owntv.core.gate.VersionGate
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** See the TV twin: reminders can be dismissed, the block covers the whole screen and installs the current release. */
@Composable
fun VersionGateHost() {
    val gate: VersionGate = koinInject()
    val updater: UpdateManager = koinInject()
    var state by remember { mutableStateOf<GateState>(GateState.Ok) }
    var recheck by remember { mutableStateOf(0) }
    var updateWanted by remember { mutableStateOf(false) }
    val update by updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(recheck) {
        if (recheck == 0) delay(6_000)
        state = runCatching { gate.check() }.getOrDefault(GateState.Ok)
        (state as? GateState.Warn)?.let { gate.markWarned(it) }
    }
    LaunchedEffect(update, updateWanted) {
        if (updateWanted && update is UpdateManager.State.Available) { updateWanted = false; updater.downloadAndInstall() }
    }
    val pressUpdate = { if (update is UpdateManager.State.Available) updater.downloadAndInstall() else { updateWanted = true; updater.check() } }
    val progress = (update as? UpdateManager.State.Downloading)?.percent
    val palette = LocalStagePalette.current
    when (val current = state) {
        GateState.Ok -> Unit
        is GateState.Warn -> MobileBottomSheet(onDismissRequest = { state = GateState.Ok }) {
            tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.version_gate_warn_title))
            Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
                Text(
                    pluralStringResource(R.plurals.version_gate_warn_body, current.daysLeft, current.daysLeft),
                    style = stageText(13.5f, 500), color = palette.dim,
                )
                StageButton(text = stringResource(R.string.version_gate_update_now), onClick = pressUpdate, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
                StageButton(text = stringResource(R.string.version_gate_later), onClick = { state = GateState.Ok }, modifier = Modifier.fillMaxWidth())
            }
        }
        is GateState.Block -> {
            BackHandler(true) {}
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F14)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
                    Text(stringResource(R.string.version_gate_block_title), style = stageText(26f, 800), color = palette.text)
                    Text(
                        progress?.let { stringResource(R.string.version_gate_downloading, it) } ?: stringResource(R.string.version_gate_block_body),
                        style = stageText(14f, 500), color = palette.dim,
                    )
                    Spacer(Modifier.height(8.dp))
                    StageButton(text = stringResource(R.string.version_gate_update_now), onClick = pressUpdate, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
                    StageButton(text = stringResource(R.string.version_gate_check_again), onClick = { recheck++ }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
