package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.core.subscription.ExpiryNotice
import tv.own.owntv.core.subscription.SubscriptionExpiryChecker
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** See the TV twin: a week-or-less warning with the days left, once a day per source. */
@Composable
fun SubscriptionExpiryHost(enabled: Boolean) {
    val checker: SubscriptionExpiryChecker = koinInject()
    var notice by remember { mutableStateOf<ExpiryNotice?>(null) }
    var queue by remember { mutableStateOf<List<ExpiryNotice>>(emptyList()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        delay(8_000)
        if (!checker.claimLaunchRun()) return@LaunchedEffect
        val found = runCatching { checker.check() }.getOrDefault(emptyList())
        if (found.isNotEmpty()) {
            checker.markShown(found)
            queue = found.drop(1)
            notice = found.first()
        }
    }
    val current = notice ?: return
    val next = { notice = queue.firstOrNull(); queue = queue.drop(1) }
    MobileBottomSheet(onDismissRequest = next) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.subscription_expiry_title, current.sourceName))
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            Text(
                text = if (current.daysLeft <= 0) stringResource(R.string.subscription_expiry_today)
                else pluralStringResource(R.plurals.subscription_expiry_message, current.daysLeft, current.daysLeft),
                style = tv.own.owntv.mobile.ui.stage.stageText(13.5f, 500),
                color = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current.dim,
            )
            Spacer(Modifier.height(MobileDimens.GapMedium))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                StageButton(
                    text = stringResource(R.string.subscription_expiry_ok),
                    onClick = next,
                    style = StageButtonStyle.PRIMARY,
                )
            }
        }
    }
}
