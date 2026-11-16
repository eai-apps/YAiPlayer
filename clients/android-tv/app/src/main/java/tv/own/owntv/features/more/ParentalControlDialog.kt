// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.parental.ParentalControl
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.theme.mpx

private enum class ParentalStep { PIN, MAIN, SOURCE, NEW_PIN }

/**
 * «Защита от детей» on the TV (owner 15.11–16.11): PIN first (1111 until the profile has its own), then each
 * playlist and Stalker portal of the profile with its choice for adult categories — show, hide, behind the PIN —
 * and «Сменить PIN». The work is [ParentalControl]'s.
 */
@Composable
fun ParentalControlDialog(onDismiss: () -> Unit) {
    val parental = koinInject<ParentalControl>()
    val settings = koinInject<SettingsRepository>()
    val sourceDao = koinInject<SourceDao>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profileId by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var step by remember { mutableStateOf(ParentalStep.PIN) }
    var source by remember { mutableStateOf<SourceEntity?>(null) }
    val wrongPin = stringResource(tv.own.owntv.core.R.string.parental_wrong_pin)
    val shortPin = stringResource(tv.own.owntv.core.R.string.parental_pin_too_short)
    val pinChanged = stringResource(tv.own.owntv.core.R.string.parental_pin_changed)
    val toast = { text: String -> android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show() }

    when (step) {
        ParentalStep.PIN -> {
            val hasOwn by produceState(true, profileId) { value = profileId < 0 || parental.hasOwnPin(profileId) }
            tv.own.owntv.features.profiles.PinDialog(
                title = stringResource(if (hasOwn) tv.own.owntv.core.R.string.parental_enter_pin else tv.own.owntv.core.R.string.parental_enter_pin_default),
                onSubmit = { pin ->
                    scope.launch {
                        if (profileId >= 0 && parental.verify(profileId, pin)) step = ParentalStep.MAIN else toast(wrongPin)
                    }
                },
                onDismiss = onDismiss,
            )
        }
        ParentalStep.NEW_PIN -> tv.own.owntv.features.profiles.PinDialog(
            title = stringResource(tv.own.owntv.core.R.string.parental_new_pin),
            onSubmit = { pin ->
                if (pin.trim().length < ParentalControl.MIN_PIN) {
                    toast(shortPin)
                } else {
                    scope.launch {
                        parental.changePin(profileId, pin)
                        toast(pinChanged)
                        step = ParentalStep.MAIN
                    }
                }
            },
            onDismiss = { step = ParentalStep.MAIN },
        )
        ParentalStep.MAIN -> {
            val sourcesFlow = remember(profileId) {
                if (profileId >= 0) sourceDao.observeForProfile(profileId) else kotlinx.coroutines.flow.flowOf(emptyList())
            }
            val sources by sourcesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            val focus = remember { FocusRequester() }
            LaunchedEffect(sources.isNotEmpty()) {
                kotlinx.coroutines.delay(60)
                runCatching { focus.requestFocus() }
            }
            tv.own.owntv.ui.stage.StagePopup(
                onDismiss = onDismiss,
                title = stringResource(tv.own.owntv.core.R.string.parental_title),
                body = stringResource(tv.own.owntv.core.R.string.parental_description),
                width = 820.mpx,
            ) {
                if (sources.isEmpty()) {
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = stringResource(tv.own.owntv.core.R.string.parental_no_sources),
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(focus),
                    )
                }
                sources.forEachIndexed { i, src ->
                    val modeFlow = remember(profileId, src.id) { parental.mode(profileId, src.id) }
                    val mode by modeFlow.collectAsStateWithLifecycle(initialValue = ParentalControl.Mode.SHOW)
                    val count by produceState(-1, src.id) { value = parental.adultCount(src.id) }
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = src.name,
                        subtitle = if (count < 0) null else pluralStringResource(tv.own.owntv.core.R.plurals.parental_adult_count, count, count),
                        value = modeLabel(mode),
                        onClick = { source = src; step = ParentalStep.SOURCE },
                        modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
                    )
                }
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(tv.own.owntv.core.R.string.parental_change_pin),
                    onClick = { step = ParentalStep.NEW_PIN },
                )
            }
        }
        ParentalStep.SOURCE -> {
            val src = source
            if (src == null) {
                LaunchedEffect(Unit) { step = ParentalStep.MAIN }
                return
            }
            val currentFlow = remember(profileId, src.id) { parental.mode(profileId, src.id) }
            val current by currentFlow.collectAsStateWithLifecycle(initialValue = ParentalControl.Mode.SHOW)
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(60)
                runCatching { focus.requestFocus() }
            }
            val choose = { m: ParentalControl.Mode ->
                scope.launch { parental.setMode(profileId, src.id, m); step = ParentalStep.MAIN }
                Unit
            }
            tv.own.owntv.ui.stage.StagePopup(
                onDismiss = { step = ParentalStep.MAIN },
                title = src.name,
                body = stringResource(tv.own.owntv.core.R.string.parental_source_body),
                width = 760.mpx,
            ) {
                listOf(
                    Triple(ParentalControl.Mode.SHOW, tv.own.owntv.core.R.string.parental_mode_show, tv.own.owntv.core.R.string.parental_mode_show_hint),
                    Triple(ParentalControl.Mode.HIDE, tv.own.owntv.core.R.string.parental_mode_hide, tv.own.owntv.core.R.string.parental_mode_hide_hint),
                    Triple(ParentalControl.Mode.LOCK, tv.own.owntv.core.R.string.parental_mode_lock, tv.own.owntv.core.R.string.parental_mode_lock_hint),
                ).forEach { (m, title, hint) ->
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = stringResource(title),
                        subtitle = stringResource(hint),
                        onClick = { choose(m) },
                        modifier = if (m == current) Modifier.focusRequester(focus) else Modifier,
                        leading = { focused -> tv.own.owntv.ui.stage.StagePopupRadio(on = m == current, focused = focused) },
                    )
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: ParentalControl.Mode): String = stringResource(
    when (mode) {
        ParentalControl.Mode.SHOW -> tv.own.owntv.core.R.string.parental_mode_show
        ParentalControl.Mode.HIDE -> tv.own.owntv.core.R.string.parental_mode_hide
        ParentalControl.Mode.LOCK -> tv.own.owntv.core.R.string.parental_mode_lock
    },
)
