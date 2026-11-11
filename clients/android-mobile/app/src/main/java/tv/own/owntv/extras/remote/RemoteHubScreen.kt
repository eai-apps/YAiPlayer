// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.R
import tv.own.owntv.extras.atvremote.SystemRemoteScreen
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.stageBackground

/**
 * Entry of «Пульт ДУ»: the whole TV box over the Android TV Remote protocol (default), or the older remote that
 * talks only to YAiPlayer running on the TV.
 */
@Composable
fun RemoteHubScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val options = listOf(stringResource(R.string.atv_mode_system), stringResource(R.string.atv_mode_player))
    val switcher: @Composable () -> Unit = {
        StageSegmented(
            options = options,
            selected = mode,
            onSelect = { mode = it },
            fill = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }
    if (mode == 0) {
        SystemRemoteScreen(onBack = onBack, modifier = modifier.stageBackground(), header = switcher)
    } else {
        Column(modifier.fillMaxSize().stageBackground()) {
            Box(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) { switcher() }
            RemoteScreen(onBack = onBack, modifier = Modifier.weight(1f))
        }
    }
}
