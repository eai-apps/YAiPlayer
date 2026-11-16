// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.epg.GuideOffer
import tv.own.owntv.core.sync.NewChannels
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.mpx

/**
 * The guide and channel offers (owner 13.11 / 16.11), one at a time:
 * - «Not all channels have a guide — load from additional source N?» (a playlist, or a portal without a guide);
 * - Monday/Tuesday morning: «New TV guide for the week» — from the provider or from our sources;
 * - «Your provider added N new channels» — «Show» opens the «New channels» category of Live TV.
 */
@Composable
fun GuideOfferHost(onShowNewChannels: (categoryId: String) -> Unit = {}) {
    val offers: GuideOffer = koinInject()
    val newChannels: NewChannels = koinInject()
    val settings: tv.own.owntv.core.settings.SettingsRepository = koinInject()
    val offer by offers.offer.collectAsStateWithLifecycle()
    val weekly by offers.weekly.collectAsStateWithLifecycle()
    val fresh by newChannels.notice.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val o = offer
    when {
        weekly -> {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
            StagePopup(
                onDismiss = { offers.weeklyLater() },
                title = stringResource(R.string.guide_weekly_title),
                body = stringResource(R.string.guide_weekly_body),
                width = 900.mpx,
                buttons = {
                    StageButton(stringResource(R.string.guide_weekly_provider), onClick = { offers.weeklyFromProvider() }, height = 56.mpx, textSize = 19,
                        tinted = true, modifier = Modifier.focusRequester(focus))
                    StageButton(stringResource(R.string.guide_weekly_reserve), onClick = { offers.weeklyFromReserve() }, height = 56.mpx, textSize = 19)
                    StageButton(stringResource(R.string.guide_offer_later), onClick = { offers.weeklyLater() }, height = 56.mpx, textSize = 19)
                },
            ) {}
        }
        o != null -> {
            LaunchedEffect(o) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
            val portal = o.portalId != null
            StagePopup(
                onDismiss = { offers.decline() },
                title = stringResource(if (portal) R.string.guide_portal_offer_title else R.string.guide_offer_title),
                body = if (portal) stringResource(R.string.guide_portal_offer_body, o.missing, o.total, o.portalName.orEmpty())
                else stringResource(R.string.guide_offer_body, o.missing, o.total, o.number),
                width = 860.mpx,
                buttons = {
                    StageButton(
                        if (portal) stringResource(R.string.guide_portal_offer_load) else stringResource(R.string.guide_offer_load, o.number),
                        onClick = { offers.accept(o) }, height = 56.mpx, textSize = 19,
                        tinted = true, modifier = Modifier.focusRequester(focus),
                    )
                    StageButton(stringResource(R.string.guide_offer_later), onClick = { offers.decline() }, height = 56.mpx, textSize = 19)
                },
            ) {}
        }
        (fresh ?: 0) > 0 -> {
            val n = fresh ?: 0
            LaunchedEffect(n) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            StagePopup(
                onDismiss = { newChannels.dismiss() },
                title = stringResource(R.string.new_channels_title),
                body = pluralStringResource(R.plurals.new_channels_body, n, n),
                width = 860.mpx,
                buttons = {
                    StageButton(stringResource(R.string.new_channels_show), onClick = {
                        newChannels.dismiss()
                        scope.launch {
                            val pid = settings.activeProfileId.first()
                            newChannels.categoryId(pid)?.let(onShowNewChannels)
                        }
                    }, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus))
                    StageButton(stringResource(R.string.guide_offer_later), onClick = { newChannels.dismiss() }, height = 56.mpx, textSize = 19)
                },
            ) {}
        }
    }
}
