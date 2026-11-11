// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.radio.RadioSync
import tv.own.owntv.radio.RadioViewModel

internal fun RadioSync.Failure.message(): Int = when (this) {
    RadioSync.Failure.WRONG_CREDENTIALS -> R.string.radio_err_wrong
    RadioSync.Failure.LOGIN_TAKEN -> R.string.radio_err_taken
    RadioSync.Failure.INVALID -> R.string.radio_err_invalid
    RadioSync.Failure.TOO_MANY -> R.string.radio_err_too_many
    RadioSync.Failure.UNAVAILABLE -> R.string.radio_err_unavailable
    RadioSync.Failure.NETWORK -> R.string.radio_err_network
}

/** The YAiPlay Radio account on the phone: sign in or create one; signed in — sync now or sign out. */
@Composable
internal fun RadioAccountPanel(vm: RadioViewModel, modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf("") }
    // The password is never kept: only the account's token is stored, like on the site.
    var password by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.radio_account), style = stageText(16f, 800), color = palette.text)
        Text(stringResource(R.string.radio_account_hint), style = stageText(13f, 500), color = palette.muted)
        val signedIn = account
        if (signedIn == null) {
            StageField(value = login, onValueChange = { login = it }, label = stringResource(R.string.radio_login), imeAction = ImeAction.Next, enabled = !busy)
            StageField(value = password, onValueChange = { password = it }, label = stringResource(R.string.radio_password), password = true, enabled = !busy,
                onImeDone = { vm.signIn(login, password, create = false) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = { vm.signIn(login, password, create = false) }, text = stringResource(R.string.radio_sign_in), style = StageButtonStyle.PRIMARY)
                StageButton(onClick = { vm.signIn(login, password, create = true) }, text = stringResource(R.string.radio_create_account),
                    style = StageButtonStyle.SECONDARY)
            }
        } else {
            Text(stringResource(R.string.radio_signed_in_as, signedIn), style = stageText(14f, 700), color = palette.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(onClick = vm::syncNow, text = stringResource(R.string.radio_sync_now), style = StageButtonStyle.PRIMARY)
                StageButton(onClick = { password = ""; vm.signOut() }, text = stringResource(R.string.radio_sign_out), style = StageButtonStyle.SECONDARY)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(stringResource(it.message()), style = stageText(13f, 600), color = palette.danger) }
    }
}
