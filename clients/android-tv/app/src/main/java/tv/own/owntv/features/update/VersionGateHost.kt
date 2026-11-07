package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.gate.GateState
import tv.own.owntv.core.gate.VersionGate
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.mpx

/**
 * The remote "this version is too old" switch (see core `VersionGate`). Off until `app-config.json` in the
 * repo says so. A reminder can be dismissed; the block cannot, and its button installs the current release
 * through the app's own updater.
 */
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
    val focus = remember { FocusRequester() }
    val current = state
    LaunchedEffect(current) { if (current !is GateState.Ok) runCatching { focus.requestFocus() } }
    val pressUpdate = { if (update is UpdateManager.State.Available) updater.downloadAndInstall() else { updateWanted = true; updater.check() } }
    val progress = (update as? UpdateManager.State.Downloading)?.percent
    when (current) {
        GateState.Ok -> Unit
        is GateState.Warn -> StagePopup(
            onDismiss = { state = GateState.Ok },
            title = stringResource(R.string.version_gate_warn_title),
            body = pluralStringResource(R.plurals.version_gate_warn_body, current.daysLeft, current.daysLeft),
            width = 820.mpx,
            buttons = {
                StageButton(stringResource(R.string.version_gate_update_now), onClick = pressUpdate, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                StageButton(stringResource(R.string.version_gate_later), onClick = { state = GateState.Ok }, height = 56.mpx, textSize = 19)
            },
        ) {}
        is GateState.Block -> StagePopup(
            onDismiss = {},
            dismissOnBackPress = false,
            title = stringResource(R.string.version_gate_block_title),
            body = progress?.let { stringResource(R.string.version_gate_downloading, it) } ?: stringResource(R.string.version_gate_block_body),
            width = 820.mpx,
            buttons = {
                StageButton(stringResource(R.string.version_gate_update_now), onClick = pressUpdate, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                StageButton(stringResource(R.string.version_gate_check_again), onClick = { recheck++ }, height = 56.mpx, textSize = 19)
            },
        ) {}
    }
}
