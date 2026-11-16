// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.ui.theme.mpx

/**
 * A phone asked to pair with «Только плеер» (owner 16.11): its 6-digit code, big, on the TV screen — over the
 * profile chooser as well as over the app, for two minutes or until OK.
 */
@Composable
fun RemotePinHost() {
    val context = LocalContext.current
    val manager = remember { RemoteTvManager.get(context) }
    val pin by manager.pinPrompt.collectAsStateWithLifecycle()
    val code = pin ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(code) {
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
        kotlinx.coroutines.delay(120_000)
        manager.dismissPinPrompt()
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = { manager.dismissPinPrompt() },
        title = stringResource(tv.own.owntv.core.R.string.remote_pair_popup_title),
        body = stringResource(tv.own.owntv.core.R.string.remote_pair_popup_body),
        width = 760.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(
                stringResource(tv.own.owntv.core.R.string.common_ok), onClick = { manager.dismissPinPrompt() },
                height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus),
            )
        },
    ) {
        androidx.tv.material3.Text(code.chunked(3).joinToString(" "), style = tv.own.owntv.ui.theme.stageText(64, 800), color = Color.White)
    }
}
