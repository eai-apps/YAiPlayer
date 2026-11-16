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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.epg.GuideOffer
import tv.own.owntv.core.sync.NewChannels
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Phone twin of the TV's GuideOfferHost (owner 13.11 / 16.11): the additional-source offer (playlist or portal),
 * the Monday/Tuesday «new TV guide for the week» offer, and «your provider added N new channels».
 */
@Composable
fun GuideOfferHost(onShowNewChannels: () -> Unit = {}) {
    val offers: GuideOffer = koinInject()
    val newChannels: NewChannels = koinInject()
    val offer by offers.offer.collectAsStateWithLifecycle()
    val weekly by offers.weekly.collectAsStateWithLifecycle()
    val fresh by newChannels.notice.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val o = offer
    when {
        weekly -> MobileBottomSheet(onDismissRequest = { offers.weeklyLater() }) {
            tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.guide_weekly_title))
            Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
                Text(stringResource(R.string.guide_weekly_body), style = stageText(13.5f, 500), color = palette.dim)
                StageButton(text = stringResource(R.string.guide_weekly_provider), onClick = { offers.weeklyFromProvider() }, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
                StageButton(text = stringResource(R.string.guide_weekly_reserve), onClick = { offers.weeklyFromReserve() }, modifier = Modifier.fillMaxWidth())
                StageButton(text = stringResource(R.string.guide_offer_later), onClick = { offers.weeklyLater() }, modifier = Modifier.fillMaxWidth())
            }
        }
        o != null -> MobileBottomSheet(onDismissRequest = { offers.decline() }) {
            val portal = o.portalId != null
            tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(if (portal) R.string.guide_portal_offer_title else R.string.guide_offer_title))
            Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
                Text(
                    if (portal) stringResource(R.string.guide_portal_offer_body, o.missing, o.total, o.portalName.orEmpty())
                    else stringResource(R.string.guide_offer_body, o.missing, o.total, o.number),
                    style = stageText(13.5f, 500), color = palette.dim,
                )
                StageButton(
                    text = if (portal) stringResource(R.string.guide_portal_offer_load) else stringResource(R.string.guide_offer_load, o.number),
                    onClick = { offers.accept(o) }, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth(),
                )
                StageButton(text = stringResource(R.string.guide_offer_later), onClick = { offers.decline() }, modifier = Modifier.fillMaxWidth())
            }
        }
        (fresh ?: 0) > 0 -> MobileBottomSheet(onDismissRequest = { newChannels.dismiss() }) {
            val n = fresh ?: 0
            tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.new_channels_title))
            Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium)) {
                Text(pluralStringResource(R.plurals.new_channels_body, n, n), style = stageText(13.5f, 500), color = palette.dim)
                StageButton(text = stringResource(R.string.new_channels_show), onClick = { newChannels.dismiss(); onShowNewChannels() }, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
                StageButton(text = stringResource(R.string.guide_offer_later), onClick = { newChannels.dismiss() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
