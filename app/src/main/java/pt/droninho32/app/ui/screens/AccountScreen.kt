package pt.droninho32.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.droninho32.app.R
import pt.droninho32.app.ui.components.LabelValueRow
import pt.droninho32.app.ui.components.SectionCard
import pt.droninho32.app.viewmodel.AuthViewModel
import pt.droninho32.app.viewmodel.ControlViewModel

/**
 * Aba "Conta" — login OPCIONAL.
 *
 * O controlo do drone NÃO precisa de conta (basta o WiFi do drone). A conta serve só
 * para guardar/ver voos no backend, quando há Internet. Por isso este ecrã vive numa
 * aba e nunca bloqueia a app: mostra o formulário quando deslogado e o perfil quando
 * com sessão. Login/registo só funcionam com ligação ao backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    vm: AuthViewModel,
    controlVm: ControlViewModel,
    onOpenDrones: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctrl by controlVm.state.collectAsStateWithLifecycle()

    // Ao ficar com sessão, tenta sincronizar os voos guardados localmente.
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) controlVm.syncPending()
    }

    var isRegister by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Depois de registar, volta ao modo login.
    LaunchedEffect(state.justRegistered) {
        if (state.justRegistered) {
            isRegister = false
            vm.consumeJustRegistered()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.account_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loggedIn) {
                // ---- Sessão iniciada: perfil ----
                SectionCard(title = stringResource(R.string.account_logged_in)) {
                    LabelValueRow(stringResource(R.string.login_username), state.username ?: "—")
                    LabelValueRow(stringResource(R.string.login_backend_url), state.backendUrl)
                    LabelValueRow(stringResource(R.string.account_pending), ctrl.pendingFlights.toString())
                }
                Button(
                    onClick = { controlVm.syncPending() },
                    enabled = ctrl.pendingFlights > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.account_sync, ctrl.pendingFlights))
                }
                Button(onClick = onOpenDrones, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_my_drones))
                }
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.drones_logout))
                }
            } else {
                // ---- Sem sessão: explicação + formulário (opcional) ----
                Text(
                    stringResource(R.string.account_explain),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ctrl.pendingFlights > 0) {
                    Text(
                        stringResource(R.string.account_pending_offline, ctrl.pendingFlights),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = state.backendUrl,
                    onValueChange = vm::onBackendUrlChange,
                    label = { Text(stringResource(R.string.login_backend_url)) },
                    placeholder = { Text(stringResource(R.string.login_backend_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isRegister) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.login_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                Button(
                    onClick = {
                        if (isRegister) vm.register(username, email, password)
                        else vm.login(username, password)
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text(
                            stringResource(
                                if (isRegister) R.string.login_register_action else R.string.login_action
                            )
                        )
                    }
                }
                TextButton(onClick = { isRegister = !isRegister; vm.clearMessages() }) {
                    Text(
                        stringResource(
                            if (isRegister) R.string.login_switch_to_login else R.string.login_switch_to_register
                        )
                    )
                }
            }
        }
    }
}
