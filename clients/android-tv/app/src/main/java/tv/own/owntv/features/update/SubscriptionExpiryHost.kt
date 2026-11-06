package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.subscription.ExpiryNotice
import tv.own.owntv.core.subscription.SubscriptionExpiryChecker
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.mpx

/**
 * Once per launch, a few seconds after start: if a saved portal or Xtream line ends within a week,
 * say so with the number of days left. Shown at most once a day per source; any provider that
 * reports an end date takes part.
 */
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
    val focus = remember { FocusRequester() }
    val next = { notice = queue.firstOrNull(); queue = queue.drop(1) }
    LaunchedEffect(current) { runCatching { focus.requestFocus() } }
    StagePopup(
        onDismiss = next,
        title = stringResource(R.string.subscription_expiry_title, current.sourceName),
        body = if (current.daysLeft <= 0) stringResource(R.string.subscription_expiry_today)
        else pluralStringResource(R.plurals.subscription_expiry_message, current.daysLeft, current.daysLeft),
        width = 820.mpx,
        buttons = {
            StageButton(
                stringResource(R.string.subscription_expiry_ok), onClick = next,
                height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(focus),
            )
        },
    ) {}
}
