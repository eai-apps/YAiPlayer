// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.mobile.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.account.AccountConfig
import tv.own.owntv.core.account.AccountErrors
import tv.own.owntv.core.account.AccountService
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText

private enum class Mode { SIGN_IN, REGISTER, RECOVER }

/**
 * The main account on the phone (owner 13.11): sign in / sign up / recover; when signed in — the account's profiles
 * (entering one opens the app in it), «New profile», sync, unlocking the source passwords, confirming a TV by its code,
 * and signing out. Next to it on the chooser stay Guest (no server), Radio and Remote.
 */
@Composable
fun AccountScreen(
    profiles: List<ProfileEntity>,
    onEnter: (ProfileEntity) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val account: AccountService = koinInject()
    val session by account.session.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    Column(
        Modifier.fillMaxSize().stageBackground().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.account_back), color = palette.muted) }
        Text(stringResource(R.string.account_title), style = stageText(26f, 800), color = palette.text)
        if (session == null) SignedOut(account) else SignedIn(account, session!!.login, profiles, onEnter)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, hint: String? = null) {
    val palette = LocalStagePalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Ascii, autoCorrectEnabled = false),
        supportingText = hint?.let { { Text(it, color = palette.muted) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignedOut(account: AccountService) {
    val palette = LocalStagePalette.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(Mode.SIGN_IN) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recovery by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val deviceName = android.os.Build.MODEL ?: "Phone"

    recovery?.let { rc ->
        Text(stringResource(R.string.account_code_title), style = stageText(18f, 700), color = palette.text)
        Text(stringResource(R.string.account_code_text), style = stageText(14f, 500), color = palette.body)
        Text(rc, fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = palette.text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.ink.copy(alpha = 0.08f)).padding(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text(stringResource(R.string.account_code_saved), color = palette.text)
        }
        Button(onClick = { recovery = null }, enabled = saved, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_continue)) }
        return
    }

    Text(stringResource(R.string.account_intro), style = stageText(14f, 500), color = palette.body)
    Field(login, { login = it.trim() }, stringResource(R.string.account_login), hint = if (mode == Mode.REGISTER) stringResource(R.string.account_login_hint) else null)
    if (mode == Mode.RECOVER) Field(code, { code = it }, stringResource(R.string.account_recovery_code))
    Field(password, { password = it }, stringResource(if (mode == Mode.RECOVER) R.string.account_new_password else R.string.account_password), password = true,
        hint = if (mode != Mode.SIGN_IN) stringResource(R.string.account_password_hint) else null)
    if (mode == Mode.REGISTER) Field(password2, { password2 = it }, stringResource(R.string.account_password2), password = true)
    error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFEF4444)) }
    Button(
        enabled = !busy && login.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (mode == Mode.REGISTER && password != password2) { error = ctx.getString(R.string.account_err_mismatch); return@Button }
            busy = true; error = null
            scope.launch {
                runCatching {
                    when (mode) {
                        Mode.SIGN_IN -> account.login(login, password, deviceName, "phone")
                        Mode.REGISTER -> recovery = account.register(login, password, deviceName, "phone")
                        Mode.RECOVER -> account.recover(login, code, password, deviceName, "phone")
                    }
                }.onFailure { error = ctx.getString(AccountErrors.messageRes(it)) }
                busy = false
            }
        },
    ) { Text(stringResource(when (mode) { Mode.SIGN_IN -> R.string.account_sign_in; Mode.REGISTER -> R.string.account_register; Mode.RECOVER -> R.string.account_continue })) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mode != Mode.SIGN_IN) TextButton(onClick = { mode = Mode.SIGN_IN; error = null }) { Text(stringResource(R.string.account_sign_in)) }
        if (mode != Mode.REGISTER) TextButton(onClick = { mode = Mode.REGISTER; error = null }) { Text(stringResource(R.string.account_register)) }
        if (mode != Mode.RECOVER) TextButton(onClick = { mode = Mode.RECOVER; error = null }) { Text(stringResource(R.string.account_recover)) }
    }
}

@Composable
private fun SignedIn(account: AccountService, login: String, profiles: List<ProfileEntity>, onEnter: (ProfileEntity) -> Unit) {
    val palette = LocalStagePalette.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(account.unlocked) }
    var unlockText by remember { mutableStateOf("") }
    var unlockByCode by remember { mutableStateOf(false) }
    var newProfile by remember { mutableStateOf("") }
    var tvCode by remember { mutableStateOf("") }
    var linkedIds by remember { mutableStateOf(account.accountProfileIds()) }
    val mine = profiles.filter { it.id in linkedIds }

    fun run(block: suspend () -> Unit) {
        busy = true; status = null
        scope.launch {
            runCatching { block() }.onFailure { status = ctx.getString(AccountErrors.messageRes(it)) }
            linkedIds = account.accountProfileIds(); unlocked = account.unlocked
            busy = false
        }
    }

    Text(login, style = stageText(16f, 700), color = tv.own.owntv.mobile.ui.stage.stageAccent.accent)
    Text(stringResource(R.string.account_link_hint, AccountConfig.SITE_URL.removePrefix("https://")), style = stageText(13f, 500), color = palette.muted)

    if (!unlocked) {
        Text(stringResource(R.string.account_locked_title), style = stageText(16f, 700), color = androidx.compose.ui.graphics.Color(0xFFF59E0B))
        Text(stringResource(R.string.account_locked_text), style = stageText(13f, 500), color = palette.body)
        Field(unlockText, { unlockText = it }, stringResource(if (unlockByCode) R.string.account_recovery_code else R.string.account_password), password = !unlockByCode)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = unlockByCode, onCheckedChange = { unlockByCode = it })
            Text(stringResource(R.string.account_use_code), color = palette.text)
        }
        Button(enabled = !busy && unlockText.isNotBlank(), onClick = {
            run { if (!account.unlock(unlockText, unlockByCode)) status = ctx.getString(R.string.account_err_wrong_unlock) else unlockText = "" }
        }) { Text(stringResource(R.string.account_unlock)) }
    }

    Text(stringResource(R.string.account_profiles), style = stageText(18f, 700), color = palette.text, modifier = Modifier.padding(top = 8.dp))
    mine.forEach { p ->
        OutlinedButton(onClick = { onEnter(p) }, modifier = Modifier.fillMaxWidth()) { Text(p.name, color = palette.text) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { Field(newProfile, { newProfile = it }, stringResource(R.string.account_profile_name)) }
        Button(enabled = !busy && newProfile.isNotBlank(), onClick = { run { account.createProfile(newProfile); newProfile = "" } }) {
            Text(stringResource(R.string.account_new_profile))
        }
    }
    OutlinedButton(enabled = !busy, onClick = {
        run { val r = account.sync(); status = ctx.getString(R.string.account_synced, r.added, r.updated, r.removed, r.pushed) }
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_sync)) }

    Text(stringResource(R.string.account_confirm_tv), style = stageText(18f, 700), color = palette.text, modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.account_confirm_tv_hint), style = stageText(13f, 500), color = palette.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { Field(tvCode, { tvCode = it.uppercase() }, "ABC-123") }
        Button(enabled = !busy && tvCode.length >= 6, onClick = {
            run { account.confirmPairing(tvCode, null); tvCode = ""; status = ctx.getString(R.string.account_confirmed) }
        }) { Text(stringResource(R.string.account_confirm)) }
    }

    status?.let { Text(it, color = palette.body) }

    Text(stringResource(R.string.account_sign_out_text), style = stageText(13f, 500), color = palette.muted, modifier = Modifier.padding(top = 12.dp))
    OutlinedButton(enabled = !busy, onClick = { run { account.signOut() } }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_out), color = androidx.compose.ui.graphics.Color(0xFFEF4444))
    }
}
