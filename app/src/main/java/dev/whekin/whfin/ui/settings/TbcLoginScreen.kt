package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.security.LocalSensitiveActions
import dev.whekin.whfin.data.security.SensitiveAction
import dev.whekin.whfin.data.tbc.TbcAccount
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun TbcLoginRoute(canStoreSession: Boolean, demoMode: Boolean, onOpenStatements: () -> Unit = {}) {
    if (demoMode) {
        Text(stringResource(R.string.demo_mode_live_import_unavailable), Modifier.padding(20.dp))
        return
    }
    val activity = androidx.compose.ui.platform.LocalContext.current as? dev.whekin.whfin.MainActivity
    DisposableEffect(activity) {
        activity?.protectBankScreen(true)
        onDispose { activity?.protectBankScreen(false) }
    }
    val vm: TbcLoginViewModel = viewModel()
    val state by vm.state.collectAsState()
    val sensitive = LocalSensitiveActions.current
    LaunchedEffect(canStoreSession) { vm.storageAllowed(canStoreSession) }
    DisposableEffect(vm) { onDispose { vm.leave() } }
    TbcLoginScreen(state, canStoreSession, vm::login, vm::confirm, vm::setRemember,
        onRestore = { sensitive.require(SensitiveAction.BankCredential) { vm.restore() } },
        onRefresh = vm::syncTransactions, onForget = vm::forget, onCancel = vm::leave, onOpenStatements = onOpenStatements)
}

@Composable
internal fun TbcLoginScreen(
    state: TbcLoginState,
    canStoreSession: Boolean,
    onLogin: (String, String) -> Unit = { _, _ -> },
    onCode: (String) -> Unit = {},
    onRemember: (Boolean) -> Unit = {},
    onRestore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onForget: () -> Unit = {},
    onCancel: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
) {
    // Deliberately not rememberSaveable: neither secret belongs in instance state.
    var username by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tbc_login_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null) {
            Text(stringResource(tbcErrorText(state.error)), color = MaterialTheme.colorScheme.error)
        }
        when (state.stage) {
            TbcLoginStage.Login -> {
                if (state.hasSaved && canStoreSession) {
                    WhfinButton(stringResource(R.string.tbc_resume), onRestore, Modifier.fillMaxWidth())
                    WhfinButton(stringResource(R.string.tbc_forget), onForget, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
                }
                WhfinField(username, { username = it }, stringResource(R.string.credo_sync_username),
                    keyboardType = KeyboardType.Ascii, modifier = Modifier.fillMaxWidth())
                WhfinField(credential, { credential = it }, stringResource(R.string.credo_sync_password),
                    keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (canStoreSession) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.tbc_remember), Modifier.weight(1f))
                        WhfinSwitch(state.remember, onRemember, contentDescription = stringResource(R.string.tbc_remember))
                    }
                } else {
                    Text(stringResource(R.string.tbc_lock_needed), style = MaterialTheme.typography.bodySmall)
                }
                WhfinButton(stringResource(R.string.tbc_login), {
                    onLogin(username, credential); credential = ""; keyboard?.hide()
                }, Modifier.fillMaxWidth(), enabled = username.isNotBlank() && credential.isNotEmpty())
            }
            TbcLoginStage.Working -> {
                WhfinLoadingIndicator(Modifier.size(36.dp))
                Text(if (state.syncProgress != null) stringResource(R.string.tbc_sync_progress, state.syncProgress.first, state.syncProgress.second)
                    else stringResource(R.string.tbc_working))
                WhfinButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            }
            TbcLoginStage.Code -> {
                Text(stringResource(if (state.otpApp) R.string.tbc_code_app else R.string.tbc_code_sms))
                WhfinField(code, { code = it.filter(Char::isDigit).take(8) }, stringResource(R.string.tbc_code),
                    keyboardType = KeyboardType.NumberPassword, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                WhfinButton(stringResource(R.string.tbc_confirm), { onCode(code); code = ""; keyboard?.hide() },
                    Modifier.fillMaxWidth(), enabled = code.length in 4..8)
                WhfinButton(stringResource(R.string.tbc_restart), onCancel, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            }
            TbcLoginStage.Connected -> {
                Text(stringResource(R.string.tbc_connected), style = MaterialTheme.typography.titleLarge)
                state.syncResult?.let { result ->
                    Text(stringResource(R.string.tbc_sync_result, result.inserted, result.matched, result.unchanged))
                    if (result.needsStatement.isNotEmpty()) {
                        Text(stringResource(R.string.tbc_initial_statement))
                        result.needsStatement.forEach { Text(it.label) }
                        WhfinButton(stringResource(R.string.statements_upload), onOpenStatements, Modifier.fillMaxWidth())
                    }
                    result.errors.forEach { error ->
                        Text(error.substringBefore(":") + ": " + stringResource(tbcErrorText(error.substringAfterLast(":").trim())),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                if (state.accounts.isEmpty() && state.error == null) Text(stringResource(R.string.tbc_no_accounts))
                state.accounts.filter { account -> state.syncResult?.needsStatement.orEmpty().none {
                    it.iban == account.iban && it.currency == account.currency
                } }.forEach { account ->
                    WhfinLedgerRow(title = "${account.currency} · •${account.iban.takeLast(4)}",
                        supportingText = account.name, icon = Icons.Default.AccountBalance)
                }
                WhfinButton(stringResource(R.string.tbc_sync_action), onRefresh, Modifier.fillMaxWidth(),
                    style = if (state.syncResult?.needsStatement.orEmpty().isEmpty()) WhfinActionStyle.Primary else WhfinActionStyle.Secondary)
                WhfinButton(stringResource(R.string.tbc_forget), onForget, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun tbcErrorText(code: String): Int = when (code) {
    "LOGIN" -> R.string.tbc_error_login
    "OTP" -> R.string.tbc_error_code
    "SESSION" -> R.string.tbc_error_session
    "PROTECTION", "RATE_LIMIT" -> R.string.tbc_error_protection
    "NETWORK" -> R.string.tbc_error_network
    "HISTORY_CONFLICT" -> R.string.tbc_history_conflict
    "HISTORY_PAGE", "HISTORY_FORMAT", "HISTORY_ACCOUNT" -> R.string.tbc_history_format
    "HISTORY_CHANGED" -> R.string.tbc_history_changed
    "OTP_METHOD", "BANK_ACTION" -> R.string.tbc_error_bank_action
    else -> R.string.tbc_error_response
}

@Preview(showBackground = true, name = "TBC login")
@Preview(showBackground = true, name = "TBC dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "TBC large", fontScale = 1.5f, heightDp = 500)
@Composable
private fun TbcLoginPreview() = WhfinTheme { androidx.compose.material3.Surface { TbcLoginScreen(TbcLoginState(), true) } }

@Preview(showBackground = true, name = "TBC confirmed")
@Composable
private fun TbcConnectedPreview() = WhfinTheme {
    androidx.compose.material3.Surface {
    TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected,
        accounts = listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Everyday"))), true)
    }
}
