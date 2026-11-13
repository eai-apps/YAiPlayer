// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.epg.GuideOffer
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** Phone twin of the TV's GuideOfferHost: one additional guide source at a time, only when asked (owner 13.11). */
@Composable
fun GuideOfferHost() {
    val offers: GuideOffer = koinInject()
    val offer by offers.offer.collectAsStateWithLifecycle()
    val o = offer ?: return
    val palette = LocalStagePalette.current
    MobileBottomSheet(onDismissRequest = { offers.decline() }) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.guide_offer_title))
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
            Text(stringResource(R.string.guide_offer_body, o.missing, o.total, o.number), style = stageText(13.5f, 500), color = palette.dim)
            StageButton(text = stringResource(R.string.guide_offer_load, o.number), onClick = { offers.accept(o) }, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
            StageButton(text = stringResource(R.string.guide_offer_later), onClick = { offers.decline() }, modifier = Modifier.fillMaxWidth())
        }
    }
}
