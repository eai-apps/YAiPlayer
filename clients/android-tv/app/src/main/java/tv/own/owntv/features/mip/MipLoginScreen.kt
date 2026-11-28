// miptv (operator edition of YAiPlayer). Original code. See docs/MIP_PLAYER.md.
package tv.own.owntv.features.mip

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.R as CoreR
import tv.own.owntv.core.mip.MipException
import tv.own.owntv.core.mip.MipService
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText

/** The box's own text dialog: D-pad and IME friendly on every Android TV (same as the account screen). */
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

private const val EMPTY = "—"
private const val MASK = "•"
private const val DEVICE = "TV"

/** The only way into miptv: the login and password the operator created in the panel. */
@Composable
fun MipLoginScreen(modifier: Modifier = Modifier) {
    val mip: MipService = koinInject()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val loginLabel = stringResource(CoreR.string.mip_login_label)
    val passLabel = stringResource(CoreR.string.mip_password_label)
    val errEmpty = stringResource(CoreR.string.mip_err_empty)
    val errNetwork = stringResource(CoreR.string.mip_err_network)
    val errFailed = stringResource(CoreR.string.mip_err_failed)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(900.dp).padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(tv.own.owntv.R.drawable.mip_logo), contentDescription = stringResource(CoreR.string.mip_app_name), modifier = Modifier.height(130.dp), contentScale = ContentScale.Fit)
            Text(stringResource(CoreR.string.mip_login_title), style = stageText(36, 800), color = StageColors.Text)
            Text(stringResource(CoreR.string.mip_login_hint), style = stageText(20, 500), color = StageColors.Body)
            // Owner 28.11: the login on the LEFT and the password on the RIGHT, side by side (not one under the other).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StageButton(stringResource(CoreR.string.mip_field_value, loginLabel, login.ifBlank { EMPTY }), modifier = Modifier.weight(1f).focusRequester(first),
                    onClick = { askText(ctx, loginLabel, false, login) { login = it.trim() } })
                StageButton(stringResource(CoreR.string.mip_field_value, passLabel, if (password.isEmpty()) EMPTY else MASK.repeat(password.length)), modifier = Modifier.weight(1f),
                    onClick = { askText(ctx, passLabel, true) { password = it } })
            }
            StageButton(stringResource(if (busy) CoreR.string.mip_signing_in else CoreR.string.mip_sign_in), modifier = Modifier.width(360.dp), tinted = true, onClick = {
                if (busy || login.isBlank() || password.isBlank()) { error = errEmpty; return@StageButton }
                busy = true; error = null
                scope.launch {
                    runCatching { mip.login(login, password, (android.os.Build.MODEL ?: DEVICE).take(60)) }
                        .onFailure {
                            error = if (it is MipException && it.network) errNetwork
                            else (it as? MipException)?.panelText?.ifBlank { null } ?: errFailed
                        }
                    busy = false
                }
            })
            error?.let { Text(it, style = stageText(22, 600), color = Color(0xFFEF4444)) }
        }
    }
}
