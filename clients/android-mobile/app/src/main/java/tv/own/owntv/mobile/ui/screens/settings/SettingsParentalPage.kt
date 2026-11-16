// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.mobile.ui.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow

/**
 * «Защита от детей» on the phone (owner 16.11), the same as on the TV: PIN first (1111 until the profile has
 * its own), then each playlist and portal with show / hide / behind the PIN for its adult categories, and
 * «Сменить PIN». The work is [ParentalControl]'s, shared with the TV.
 */
@Composable
fun SettingsParentalPage(modifier: Modifier = Modifier) {
    val parental = koinInject<ParentalControl>()
    val settings = koinInject<SettingsRepository>()
    val sourceDao = koinInject<SourceDao>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profileId by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var unlocked by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SourceEntity?>(null) }
    val wrongPin = stringResource(tv.own.owntv.core.R.string.parental_wrong_pin)
    val shortPin = stringResource(tv.own.owntv.core.R.string.parental_pin_too_short)
    val pinChanged = stringResource(tv.own.owntv.core.R.string.parental_pin_changed)
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    if (!unlocked) {
        val hasOwn by produceState(true, profileId) { value = profileId < 0 || parental.hasOwnPin(profileId) }
        tv.own.owntv.mobile.ui.categorylock.PinEntrySheet(
            title = stringResource(tv.own.owntv.core.R.string.parental_title),
            description = stringResource(
                if (hasOwn) tv.own.owntv.core.R.string.parental_enter_pin else tv.own.owntv.core.R.string.parental_enter_pin_default,
            ),
            onSubmit = { pin ->
                scope.launch { if (profileId >= 0 && parental.verify(profileId, pin)) unlocked = true else toast(wrongPin) }
            },
            // Cancelling the PIN leaves the page: there is nothing here to see without it.
            onDismiss = { backDispatcher?.onBackPressed() },
        )
        return
    }

    val sourcesFlow = remember(profileId) { sourceDao.observeForProfile(profileId) }
    val sources by sourcesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    SettingsPage(modifier) {
        settingsGroup(key = "parental-sources") {
            if (sources.isEmpty()) {
                StageSettingRow(title = stringResource(tv.own.owntv.core.R.string.parental_no_sources))
            }
            sources.forEach { src ->
                val modeFlow = remember(profileId, src.id) { parental.mode(profileId, src.id) }
                val mode by modeFlow.collectAsStateWithLifecycle(initialValue = ParentalControl.Mode.SHOW)
                val count by produceState(-1, src.id) { value = parental.adultCount(src.id) }
                StageSettingRow(
                    title = src.name,
                    subtitle = if (count < 0) null else pluralStringResource(tv.own.owntv.core.R.plurals.parental_adult_count, count, count),
                    onClick = { editing = src },
                ) { StageRowValue(modeLabel(mode), StageChevron.DOWN) }
            }
        }
        settingsGroup(key = "parental-pin") {
            StageSettingRow(
                title = stringResource(tv.own.owntv.core.R.string.parental_change_pin),
                subtitle = stringResource(tv.own.owntv.core.R.string.parental_description),
                onClick = { newPin = true },
            )
        }
    }

    editing?.let { src ->
        val modeFlow = remember(profileId, src.id) { parental.mode(profileId, src.id) }
        val current by modeFlow.collectAsStateWithLifecycle(initialValue = ParentalControl.Mode.SHOW)
        SettingsChoiceSheet(
            title = src.name,
            description = stringResource(tv.own.owntv.core.R.string.parental_source_body),
            choices = listOf(
                SettingsChoice(ParentalControl.Mode.SHOW, stringResource(tv.own.owntv.core.R.string.parental_mode_show), stringResource(tv.own.owntv.core.R.string.parental_mode_show_hint)),
                SettingsChoice(ParentalControl.Mode.HIDE, stringResource(tv.own.owntv.core.R.string.parental_mode_hide), stringResource(tv.own.owntv.core.R.string.parental_mode_hide_hint)),
                SettingsChoice(ParentalControl.Mode.LOCK, stringResource(tv.own.owntv.core.R.string.parental_mode_lock), stringResource(tv.own.owntv.core.R.string.parental_mode_lock_hint)),
            ),
            selected = current,
            onSelect = { picked -> scope.launch { parental.setMode(profileId, src.id, picked) } },
            onDismiss = { editing = null },
        )
    }

    if (newPin) {
        tv.own.owntv.mobile.ui.categorylock.PinEntrySheet(
            title = stringResource(tv.own.owntv.core.R.string.parental_new_pin),
            onSubmit = { pin ->
                if (pin.trim().length < ParentalControl.MIN_PIN) {
                    toast(shortPin)
                } else {
                    scope.launch { parental.changePin(profileId, pin); toast(pinChanged); newPin = false }
                }
            },
            onDismiss = { newPin = false },
        )
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
