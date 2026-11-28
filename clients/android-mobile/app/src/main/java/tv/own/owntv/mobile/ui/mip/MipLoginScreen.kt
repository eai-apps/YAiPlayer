// MIP Player (operator edition of YAiPlayer). Original code. See docs/MIP_PLAYER.md.
package tv.own.owntv.mobile.ui.mip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.mip.MipException
import tv.own.owntv.core.mip.MipService
import tv.own.owntv.mobile.R

/** The only way into MIP Player: the login and password the operator created in the panel. */
@Composable
fun MipLoginScreen(modifier: Modifier = Modifier) {
    val mip: MipService = koinInject()
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        if (login.isBlank() || password.isBlank()) { error = "Введите логин и пароль"; return }
        busy = true; error = null
        scope.launch {
            runCatching { mip.login(login, password, (android.os.Build.MODEL ?: "Phone").take(60)) }
                .onFailure {
                    error = if (it is MipException && it.network) "Нет связи с сервером. Проверьте интернет."
                    else (it as? MipException)?.message ?: "Не удалось войти"
                }
            busy = false
        }
    }

    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.mipmap.mip_icon), contentDescription = "MIP Player", modifier = Modifier.size(96.dp))
            Text("MIP Player", style = MaterialTheme.typography.headlineMedium)
            Text("Введите логин и пароль, которые вам выдал оператор", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = login, onValueChange = { login = it.trim() }, singleLine = true,
                label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Входим…" else "Войти")
            }
        }
    }
}
