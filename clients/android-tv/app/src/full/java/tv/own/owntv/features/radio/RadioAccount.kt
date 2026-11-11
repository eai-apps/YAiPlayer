// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.radio.RadioSync
import tv.own.owntv.radio.RadioViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

internal fun RadioSync.Failure.message(): Int = when (this) {
    RadioSync.Failure.WRONG_CREDENTIALS -> R.string.radio_err_wrong
    RadioSync.Failure.LOGIN_TAKEN -> R.string.radio_err_taken
    RadioSync.Failure.INVALID -> R.string.radio_err_invalid
    RadioSync.Failure.TOO_MANY -> R.string.radio_err_too_many
    RadioSync.Failure.UNAVAILABLE -> R.string.radio_err_unavailable
    RadioSync.Failure.NETWORK -> R.string.radio_err_network
}

/** The YAiPlay Radio account on the TV: sign in or create one; signed in — sync now or sign out. */
@Composable
internal fun RadioAccountPanel(vm: RadioViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf("") }
    // The password is never kept: only the account's token is stored, like on the site.
    var password by remember { mutableStateOf("") }
    Column(Modifier.width(820.mpx).padding(top = 8.mpx), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        Text(stringResource(R.string.radio_account), style = stageText(30, 800), color = StageColors.Text)
        Text(stringResource(R.string.radio_account_hint), style = stageText(19, 500), color = StageColors.Muted)
        val signedIn = account
        if (signedIn == null) {
            StageFieldRow(icon = OwnTVIcon.PERSON, label = stringResource(R.string.radio_login), value = login,
                onValueChange = { login = it }, backLabel = stringResource(R.string.radio_back))
            StageFieldRow(icon = OwnTVIcon.LOCK, label = stringResource(R.string.radio_password), value = password,
                onValueChange = { password = it }, password = true, backLabel = stringResource(R.string.radio_back))
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                StageButton(text = stringResource(R.string.radio_sign_in), onClick = { vm.signIn(login, password, create = false) })
                StageButton(text = stringResource(R.string.radio_create_account), onClick = { vm.signIn(login, password, create = true) })
            }
        } else {
            Text(stringResource(R.string.radio_signed_in_as, signedIn), style = stageText(22, 700), color = StageColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                StageButton(text = stringResource(R.string.radio_sync_now), icon = OwnTVIcon.REFRESH, onClick = vm::syncNow)
                StageButton(text = stringResource(R.string.radio_sign_out), onClick = { password = ""; vm.signOut() })
            }
        }
        if (busy) OwnTVSpinner(sizeDp = 36)
        failure?.let { Text(stringResource(it.message()), style = stageText(19, 600), color = StageColors.Danger) }
    }
}
