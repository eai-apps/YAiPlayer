// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.epg.GuideOffer
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.mpx

/** «Not all channels have a guide — load from additional source N?» (owner 13.11), one source at a time. */
@Composable
fun GuideOfferHost() {
    val offers: GuideOffer = koinInject()
    val offer by offers.offer.collectAsStateWithLifecycle()
    val o = offer ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(o) { runCatching { focus.requestFocus() } }
    StagePopup(
        onDismiss = { offers.decline() },
        title = stringResource(R.string.guide_offer_title),
        body = stringResource(R.string.guide_offer_body, o.missing, o.total, o.number),
        width = 860.mpx,
        buttons = {
            StageButton(stringResource(R.string.guide_offer_load, o.number), onClick = { offers.accept(o) }, height = 56.mpx, textSize = 19,
                tinted = true, modifier = Modifier.focusRequester(focus))
            StageButton(stringResource(R.string.guide_offer_later), onClick = { offers.decline() }, height = 56.mpx, textSize = 19)
        },
    ) {}
}
