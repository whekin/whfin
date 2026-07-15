package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinSwitch
import dev.whekin.whfin.core.ui.WhfinCodeDots
import dev.whekin.whfin.core.ui.WhfinNumericKeypad
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun CredoSyncRoute(
    appLockHasPin: Boolean,
    onOpenAppLock: () -> Unit,
    viewModel: CredoSyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    CredoSyncScreen(
        state = state,
        appLockHasPin = appLockHasPin,
        onOpenAppLock = onOpenAppLock,
        onConnect = viewModel::connect,
        onSubmitOtp = viewModel::submitOtp,
        onResendOtp = viewModel::resendOtp,
        onSync = viewModel::sync,
        onDisconnect = viewModel::disconnect,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
fun CredoSyncScreen(
    state: CredoSyncUiState,
    appLockHasPin: Boolean,
    onOpenAppLock: () -> Unit,
    onConnect: (String, String, Boolean) -> Unit,
    onSubmitOtp: (String) -> Unit,
    onResendOtp: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onDismissError: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(state.savedUsername.orEmpty()) }
    var credential by rememberSaveable { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var rememberPassword by rememberSaveable { mutableStateOf(appLockHasPin) }

    LaunchedEffect(state.savedUsername) {
        if (username.isBlank()) username = state.savedUsername.orEmpty()
    }
    LaunchedEffect(appLockHasPin) {
        if (!appLockHasPin) rememberPassword = false
    }
    LaunchedEffect(state.stage) {
        if (state.stage != CredoSyncStage.AwaitingOtp) otp = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.credo_sync_experimental_title),
            body = stringResource(R.string.credo_sync_experimental_body),
            icon = Icons.Default.Security,
            kind = WhfinNoticeKind.Attention,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!appLockHasPin && state.stage == CredoSyncStage.Disconnected) {
            WhfinNotice(
                title = stringResource(R.string.credo_sync_lock_required_title),
                body = stringResource(R.string.credo_sync_lock_required_body),
                icon = Icons.Default.Lock,
                kind = WhfinNoticeKind.Unavailable,
                actionLabel = stringResource(R.string.credo_sync_open_app_lock),
                onAction = onOpenAppLock,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.errorCode?.let { errorCode ->
            WhfinNotice(
                title = stringResource(R.string.credo_sync_error_title),
                body = credoErrorMessage(errorCode),
                kind = WhfinNoticeKind.Error,
                actionLabel = stringResource(R.string.action_dismiss),
                onAction = onDismissError,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (state.stage) {
            CredoSyncStage.Disconnected,
            CredoSyncStage.Connecting,
            -> LoginContent(
                username = username,
                onUsernameChange = { username = it },
                credential = credential,
                onCredentialChange = { credential = it },
                hasSavedPassword = state.hasSavedPassword,
                rememberPassword = rememberPassword,
                onRememberPasswordChange = { rememberPassword = it },
                canRememberPassword = appLockHasPin,
                loading = state.stage == CredoSyncStage.Connecting,
                onConnect = { onConnect(username, credential, rememberPassword) },
            )

            CredoSyncStage.AwaitingOtp -> OtpContent(
                mobileHint = state.mobileHint,
                otp = otp,
                onOtpChange = { value -> otp = value.filter(Char::isDigit).take(4) },
                onSubmit = { onSubmitOtp(otp) },
                onResend = {
                    otp = ""
                    onResendOtp()
                },
                loading = state.isBusy,
            )

            CredoSyncStage.Connected,
            CredoSyncStage.Syncing,
            -> ConnectedContent(
                state = state,
                onSync = onSync,
                onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
private fun LoginContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    credential: String,
    onCredentialChange: (String) -> Unit,
    hasSavedPassword: Boolean,
    rememberPassword: Boolean,
    onRememberPasswordChange: (Boolean) -> Unit,
    canRememberPassword: Boolean,
    loading: Boolean,
    onConnect: () -> Unit,
) {
    WhfinSectionLabel(stringResource(R.string.credo_sync_sign_in_section))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinField(
            value = username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.credo_sync_username),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Default.AccountBalance,
        )
        WhfinField(
            value = credential,
            onValueChange = onCredentialChange,
            label = stringResource(R.string.credo_sync_password),
            placeholder = if (hasSavedPassword) stringResource(R.string.credo_sync_password_saved) else null,
            supportingText = if (hasSavedPassword && credential.isBlank()) {
                stringResource(R.string.credo_sync_password_saved_body)
            } else null,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.credo_sync_remember_password),
                supportingText = stringResource(
                    if (canRememberPassword) R.string.credo_sync_remember_password_body
                    else R.string.credo_sync_remember_password_unavailable,
                ),
                icon = Icons.Default.Lock,
                trailing = {
                    WhfinSwitch(
                        checked = rememberPassword,
                        onCheckedChange = onRememberPasswordChange,
                        contentDescription = stringResource(R.string.credo_sync_remember_password),
                        enabled = canRememberPassword,
                    )
                },
            )
        }
        WhfinButton(
            label = stringResource(if (loading) R.string.credo_sync_connecting else R.string.credo_sync_connect),
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && username.isNotBlank() && (credential.isNotBlank() || hasSavedPassword),
            leadingIcon = if (loading) null else Icons.Default.CloudSync,
        )
        if (loading) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun OtpContent(
    mobileHint: String?,
    otp: String,
    onOtpChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    loading: Boolean,
) {
    WhfinSectionLabel(stringResource(R.string.credo_sync_otp_section))
    Text(
        text = if (mobileHint.isNullOrBlank()) stringResource(R.string.credo_sync_otp_body)
        else stringResource(R.string.credo_sync_otp_body_with_phone, mobileHint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinCodeDots(
            length = 4,
            filled = otp.length,
            contentDescription = stringResource(R.string.credo_sync_otp_progress, otp.length, 4),
        )
        WhfinNumericKeypad(
            deleteContentDescription = stringResource(R.string.credo_sync_delete_digit),
            onDigit = { digit -> if (otp.length < 4) onOtpChange(otp + digit) },
            onBackspace = { if (otp.isNotEmpty()) onOtpChange(otp.dropLast(1)) },
            enabled = !loading,
        )
    }
    WhfinButton(
        label = stringResource(if (loading) R.string.credo_sync_confirming else R.string.credo_sync_confirm),
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = otp.length == 4 && !loading,
    )
    WhfinButton(
        label = stringResource(R.string.credo_sync_resend_otp),
        onClick = onResend,
        modifier = Modifier.fillMaxWidth(),
        style = WhfinActionStyle.Quiet,
        enabled = !loading,
    )
}

@Composable
private fun ConnectedContent(
    state: CredoSyncUiState,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val syncing = state.stage == CredoSyncStage.Syncing
    WhfinSectionLabel(stringResource(R.string.credo_sync_accounts_section))
    Text(
        stringResource(R.string.credo_sync_accounts_found, state.accounts.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
        state.accounts.forEachIndexed { index, account ->
            WhfinLedgerRow(
                title = account.maskedLabel,
                supportingText = stringResource(R.string.credo_sync_statement_period),
                icon = Icons.Default.AccountBalance,
                divider = index != state.accounts.lastIndex,
            )
        }
    }
    if (syncing) {
        WhfinNotice(
            title = stringResource(
                R.string.credo_sync_progress_title,
                state.currentAccount,
                state.accounts.size,
            ),
            body = stringResource(state.currentPhase.phaseLabel()),
            icon = Icons.Default.CloudSync,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    WhfinButton(
        label = stringResource(if (syncing) R.string.credo_sync_syncing else R.string.credo_sync_now),
        onClick = onSync,
        modifier = Modifier.fillMaxWidth(),
        enabled = !syncing,
        leadingIcon = Icons.Default.CloudSync,
    )

    if (state.results.isNotEmpty()) {
        WhfinSectionLabel(stringResource(R.string.credo_sync_result_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            state.results.forEachIndexed { index, file ->
                WhfinLedgerRow(
                    title = file.accountLabel,
                    supportingText = file.result?.let { result ->
                        stringResource(
                            R.string.credo_sync_result_success,
                            result.inserted,
                            result.duplicates,
                            result.reconciled,
                        )
                    } ?: credoErrorMessage(file.errorCode ?: "UNKNOWN_ERROR"),
                    icon = Icons.Default.AccountBalance,
                    iconTint = if (file.errorCode == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    supportingMaxLines = 3,
                    divider = index != state.results.lastIndex,
                )
            }
        }
    }

    WhfinButton(
        label = stringResource(R.string.credo_sync_disconnect),
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth(),
        enabled = !syncing,
        style = WhfinActionStyle.DestructiveSecondary,
    )
}

@Composable
private fun credoErrorMessage(code: String): String = when (code) {
    "CREDENTIALS_REQUIRED", "INVALID_INPUT_DATA" -> stringResource(R.string.credo_sync_error_credentials)
    "INVALID_OTP" -> stringResource(R.string.credo_sync_error_otp)
    "USER_IS_BLOCKED", "USER_OTP_BLOCKED" -> stringResource(R.string.credo_sync_error_blocked)
    "UNAUTHORIZED", "LOGIN_EXPIRED", "SESSION_EXPIRED" -> stringResource(R.string.credo_sync_error_expired)
    "NETWORK_ERROR" -> stringResource(R.string.credo_sync_error_network)
    "HTTP_403", "HTTP_429" -> stringResource(R.string.credo_sync_error_protection)
    "NO_ACCOUNTS" -> stringResource(R.string.credo_sync_error_no_accounts)
    "EMPTY_STATEMENT", "INVALID_STATEMENT" -> stringResource(R.string.credo_sync_error_statement)
    else -> stringResource(R.string.credo_sync_error_generic, code)
}

private fun StatementImporter.Phase?.phaseLabel(): Int = when (this) {
    StatementImporter.Phase.READING, null -> R.string.statements_phase_reading
    StatementImporter.Phase.IMPORTING -> R.string.statements_phase_importing
    StatementImporter.Phase.RECONCILING -> R.string.statements_phase_reconciling
    StatementImporter.Phase.VERIFYING -> R.string.statements_phase_verifying
}

private val previewAccounts = listOf(
    CredoRemoteAccount("GE00XX0000000000000001", "GEL", 1, "Current account", "ACCOUNT"),
    CredoRemoteAccount("GE00XX0000000000000001", "USD", 2, "Current account", "ACCOUNT"),
    CredoRemoteAccount("GE00XX0000000000000002", "GEL", 3, "Saving deposit", "DEPOSIT"),
)

@Preview(name = "Credo disconnected light", widthDp = 400, heightDp = 840, showBackground = true)
@Preview(name = "Credo disconnected dark", widthDp = 400, heightDp = 840, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Credo disconnected font 1.5", widthDp = 400, heightDp = 980, fontScale = 1.5f)
@Preview(name = "Credo disconnected compact", widthDp = 400, heightDp = 520)
@Composable
private fun CredoDisconnectedPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(savedUsername = "demo", hasSavedPassword = true),
                appLockHasPin = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo OTP", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun CredoOtpPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp, mobileHint = "+995 *** ** 42"),
                appLockHasPin = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo connected", widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun CredoConnectedPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(stage = CredoSyncStage.Connected, accounts = previewAccounts),
                appLockHasPin = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo unavailable", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun CredoUnavailablePreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(errorCode = "NETWORK_ERROR"),
                appLockHasPin = false,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo protocol error", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun CredoProtocolErrorPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(errorCode = "INVALID_API_RESPONSE"),
                appLockHasPin = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}
