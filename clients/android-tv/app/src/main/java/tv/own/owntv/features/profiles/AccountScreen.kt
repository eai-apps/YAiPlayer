// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.features.profiles

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.R
import tv.own.owntv.core.account.AccountConfig
import tv.own.owntv.core.account.AccountErrors
import tv.own.owntv.core.account.AccountService
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText

/**
 * The main account on the TV box (owner 13.11): sign in by a code/QR confirmed on the phone or site (default — no
 * typing with the remote), or by login and password, or sign up right here. Signed in: the account's profiles,
 * «New profile», sync, unlocking the source passwords, sign out. Text is typed in the box's own keyboard dialog, which
 * works with any remote.
 */
@Composable
fun AccountScreen(profiles: List<ProfileEntity>, onEnter: (ProfileEntity) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val account: AccountService = koinInject()
    val session by account.session.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 96.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.account_title), style = stageText(44, 800), color = StageColors.Text)
            if (session == null) SignedOutTv(account) else SignedInTv(account, session!!.login, profiles, onEnter, onBack)
        }
    }
}

/** The box's own text dialog: D-pad and IME friendly on every Android TV. */
private fun askText(ctx: Context, title: String, password: Boolean, initial: String = "", onResult: (String) -> Unit) {
    val input = EditText(ctx).apply {
        setText(initial)
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine()
    }
    AlertDialog.Builder(ctx).setTitle(title).setView(input)
        .setPositiveButton(android.R.string.ok) { _, _ -> onResult(input.text.toString()) }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    input.requestFocus()
}

private enum class TvMode { CODE, SIGN_IN, REGISTER }

@Composable
private fun SignedOutTv(account: AccountService) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(TvMode.CODE) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf<AccountService.Pairing?>(null) }
    val first = remember { FocusRequester() }
    val deviceName = (android.os.Build.MODEL ?: "TV").take(60)

    recovery?.let { rc ->
        Text(stringResource(R.string.account_code_title), style = stageText(30, 700), color = StageColors.Text)
        Text(stringResource(R.string.account_code_text), style = stageText(22, 500), color = StageColors.Body)
        Text(rc, style = stageText(36, 700).copy(fontFamily = FontFamily.Monospace), color = StageColors.Text,
            modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(24.dp))
        StageButton(stringResource(R.string.account_code_saved), onClick = { recovery = null }, modifier = Modifier.focusRequester(first))
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        return
    }

    Text(stringResource(R.string.account_intro), style = stageText(22, 500), color = StageColors.Body)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StageButton(stringResource(R.string.account_by_code), onClick = { mode = TvMode.CODE; error = null }, tinted = mode == TvMode.CODE,
            modifier = Modifier.focusRequester(first))
        StageButton(stringResource(R.string.account_sign_in), onClick = { mode = TvMode.SIGN_IN; error = null }, tinted = mode == TvMode.SIGN_IN)
        StageButton(stringResource(R.string.account_register), onClick = { mode = TvMode.REGISTER; error = null }, tinted = mode == TvMode.REGISTER)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    when (mode) {
        TvMode.CODE -> {
            // A fresh code on entry and whenever one expires; poll every 3 s (docs/SYNC_API.md §6).
            LaunchedEffect(mode) {
                while (true) {
                    val p = runCatching { account.startPairing(deviceName) }.onFailure { error = ctx.getString(AccountErrors.messageRes(it)) }.getOrNull()
                    if (p == null) { delay(10_000); continue }
                    pairing = p; error = null
                    val until = System.currentTimeMillis() + p.expiresInSec * 1000L
                    var status = "waiting"
                    while (status == "waiting" && System.currentTimeMillis() < until) {
                        delay(3_000)
                        status = runCatching { account.pollPairing(p) }.getOrDefault("waiting")
                    }
                    if (status == "ok") return@LaunchedEffect
                }
            }
            pairing?.let { p ->
                val qr = remember(p.url) { CompanionLink.renderQr(p.url, 360)?.asImageBitmap() }
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    qr?.let { Image(it, contentDescription = null, modifier = Modifier.size(240.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) }
                    Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.code, style = stageText(72, 800).copy(fontFamily = FontFamily.Monospace), color = StageColors.Text)
                        Text(stringResource(R.string.account_code_screen_text, AccountConfig.SITE_URL.removePrefix("https://")),
                            style = stageText(22, 500), color = StageColors.Body)
                        Text(stringResource(R.string.account_code_waiting), style = stageText(20, 500), color = StageColors.Muted)
                    }
                }
            }
        }
        TvMode.SIGN_IN, TvMode.REGISTER -> {
            val loginLabel = stringResource(R.string.account_login)
            val passLabel = stringResource(R.string.account_password)
            val pass2Label = stringResource(R.string.account_password2)
            StageButton("$loginLabel: ${login.ifBlank { "—" }}", onClick = { askText(ctx, loginLabel, false, login) { login = it.trim().lowercase() } })
            StageButton("$passLabel: ${if (password.isEmpty()) "—" else "•".repeat(password.length)}", onClick = { askText(ctx, passLabel, true) { password = it } })
            if (mode == TvMode.REGISTER) {
                StageButton("$pass2Label: ${if (password2.isEmpty()) "—" else "•".repeat(password2.length)}", onClick = { askText(ctx, pass2Label, true) { password2 = it } })
                Text(stringResource(R.string.account_login_hint) + " · " + stringResource(R.string.account_password_hint), style = stageText(18, 500), color = StageColors.Muted)
            }
            StageButton(
                stringResource(if (mode == TvMode.REGISTER) R.string.account_register else R.string.account_sign_in),
                tinted = true,
                onClick = {
                    if (busy || login.isBlank() || password.isBlank()) return@StageButton
                    if (mode == TvMode.REGISTER && password != password2) { error = ctx.getString(R.string.account_err_mismatch); return@StageButton }
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            if (mode == TvMode.REGISTER) recovery = account.register(login, password, deviceName, "tv")
                            else account.login(login, password, deviceName, "tv")
                        }.onFailure { error = ctx.getString(AccountErrors.messageRes(it)) }
                        busy = false
                    }
                },
            )
        }
    }
    error?.let { Text(it, style = stageText(22, 600), color = Color(0xFFEF4444)) }
}

@Composable
private fun SignedInTv(account: AccountService, login: String, profiles: List<ProfileEntity>, onEnter: (ProfileEntity) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(account.unlocked) }
    var linked by remember { mutableStateOf(account.accountProfileIds()) }
    val first = remember { FocusRequester() }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; status = null
        scope.launch {
            runCatching { block() }.onFailure { status = ctx.getString(AccountErrors.messageRes(it)) }
            linked = account.accountProfileIds(); unlocked = account.unlocked; busy = false
        }
    }
    LaunchedEffect(Unit) { run { account.sync() } }

    Text(login, style = stageText(28, 700), color = tv.own.owntv.ui.theme.stageAccent.accent)
    Text(stringResource(R.string.account_link_hint, AccountConfig.SITE_URL.removePrefix("https://")), style = stageText(18, 500), color = StageColors.Muted)
    if (!unlocked) {
        Text(stringResource(R.string.account_locked_title), style = stageText(24, 700), color = Color(0xFFF59E0B))
        Text(stringResource(R.string.account_locked_text), style = stageText(20, 500), color = StageColors.Body)
        val passLabel = stringResource(R.string.account_password)
        val codeLabel = stringResource(R.string.account_recovery_code)
        val wrong = stringResource(R.string.account_err_wrong_unlock)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StageButton(stringResource(R.string.account_unlock), tinted = true, onClick = {
                askText(ctx, passLabel, true) { v -> run { if (!account.unlock(v, false)) status = wrong } }
            })
            StageButton(stringResource(R.string.account_use_code), onClick = {
                askText(ctx, codeLabel, false) { v -> run { if (!account.unlock(v, true)) status = wrong } }
            })
        }
    }
    Text(stringResource(R.string.account_profiles), style = stageText(30, 700), color = StageColors.Text, modifier = Modifier.padding(top = 8.dp))
    val mine = profiles.filter { it.id in linked }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        mine.forEachIndexed { i, p ->
            StageButton(p.name, onClick = { onEnter(p) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
        }
        val nameLabel = stringResource(R.string.account_profile_name)
        StageButton("＋ " + stringResource(R.string.account_new_profile), onClick = {
            askText(ctx, nameLabel, false) { name -> if (name.isNotBlank()) run { account.createProfile(name) } }
        })
    }
    LaunchedEffect(mine.isNotEmpty()) { if (mine.isNotEmpty()) runCatching { first.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StageButton(stringResource(R.string.account_sync), onClick = {
            run { val r = account.sync(); status = ctx.getString(R.string.account_synced, r.added, r.updated, r.removed, r.pushed) }
        })
        StageButton(stringResource(R.string.account_sign_out), onClick = { run { account.signOut(); onBack() } })
    }
    Text(stringResource(R.string.account_sign_out_text), style = stageText(18, 500), color = StageColors.Muted)
    status?.let { Text(it, style = stageText(22, 600), color = StageColors.Body) }
}
