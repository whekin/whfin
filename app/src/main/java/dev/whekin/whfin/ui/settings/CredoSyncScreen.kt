package dev.whekin.whfin.ui.settings

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinIconButton
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
import dev.whekin.whfin.data.sms.SmsHistoryReader
import dev.whekin.whfin.data.sms.registerCredoOtpReceiver
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Fast enough to feel automatic, slow enough not to poll the inbox for nothing. */
private const val OTP_INBOX_POLL_MILLIS = 1_000L

private fun hasSmsReadPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

enum class CredoOriginalExportOutcome { Saved, Error }

private data class PendingOriginalExport(val token: String, val fileName: String)

/**
 * Memory-only sign-in draft. The route owner keeps it across the App Lock detour, while the bank
 * credential still never enters saved-instance state or any persistent store.
 */
class CredoLoginDraft(
    username: String = "",
    credential: String = "",
) {
    var username by mutableStateOf(username)
    var credential by mutableStateOf(credential)
}

@Composable
fun CredoSyncRoute(
    appLockEnabled: Boolean,
    onOpenAppLock: () -> Unit,
    routineSyncRequestKey: Int = 0,
    onRoutineSyncRequestConsumed: () -> Unit = {},
    showCredentialManagement: Boolean = false,
    viewModel: CredoSyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // SAF backgrounds the Activity. With immediate App Lock this composable is then removed, and a
    // composition-scoped write would be cancelled halfway: the selected file stayed empty and the
    // one action that explains a failed statement silently did nothing.
    val scope = (context as ComponentActivity).lifecycleScope
    var pendingOriginalExport by remember { mutableStateOf<PendingOriginalExport?>(null) }
    var originalExportOutcome by remember { mutableStateOf<CredoOriginalExportOutcome?>(null) }
    var incomingOtp by remember { mutableStateOf<String?>(null) }
    // Bumped whenever a challenge is opened, so a resend restarts the inbox watch: the previous
    // attempt's code is still in the inbox and must not be filled in for the new one.
    var otpChallengeKey by remember { mutableIntStateOf(0) }
    val otpInbox = remember(context) { (context.applicationContext as WhfinApp).credoOtpInbox }
    val createOriginalStatement = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME),
    ) { uri ->
        val pending = pendingOriginalExport
        pendingOriginalExport = null
        if (uri != null && pending != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        viewModel.writeDownloadedStatement(pending.token, output)
                    } ?: false
                }.getOrDefault(false)
            }
            originalExportOutcome = if (saved) {
                CredoOriginalExportOutcome.Saved
            } else {
                CredoOriginalExportOutcome.Error
            }
        }
    }
    LaunchedEffect(appLockEnabled) {
        if (appLockEnabled) viewModel.revealSavedUsername() else viewModel.forgetSavedCredentials()
    }
    LaunchedEffect(routineSyncRequestKey, appLockEnabled) {
        if (routineSyncRequestKey > 0) {
            onRoutineSyncRequestConsumed()
            if (appLockEnabled) {
                otpInbox.beginChallenge()
                otpChallengeKey += 1
                viewModel.syncLatest()
            }
        }
    }
    LaunchedEffect(otpInbox) {
        otpInbox.codes.collect { code ->
            incomingOtp = code
            otpInbox.clearBufferedCode()
        }
    }
    // The broadcast is the fast path, not the only one: an OEM that quietly leaves this app out of
    // an SMS delivery it holds the permission for is indistinguishable from a bank taking its time.
    // While a challenge is open the inbox is checked too, for the login template alone.
    LaunchedEffect(state.stage, otpChallengeKey) {
        val waiting = state.stage == CredoSyncStage.Connecting ||
            state.stage == CredoSyncStage.AwaitingOtp
        val since = otpInbox.challengeSince
        if (!waiting || since == 0L || !hasSmsReadPermission(context)) return@LaunchedEffect
        val reader = SmsHistoryReader(context.contentResolver)
        while (true) {
            val message = runCatching { reader.loginCodeSince(since) }.getOrNull()
            if (message != null && otpInbox.accept(message.body)) return@LaunchedEffect
            delay(OTP_INBOX_POLL_MILLIS)
        }
    }
    LaunchedEffect(state.stage) {
        when (state.stage) {
            CredoSyncStage.Connecting,
            CredoSyncStage.AwaitingOtp,
            -> otpInbox.ensureChallenge()

            CredoSyncStage.Connected -> otpInbox.endChallenge()
            else -> Unit
        }
    }
    DisposableEffect(otpInbox) {
        onDispose { otpInbox.endChallenge() }
    }
    DisposableEffect(context, otpInbox, state.stage) {
        val registration = if (
            state.stage == CredoSyncStage.Connecting || state.stage == CredoSyncStage.AwaitingOtp
        ) {
            runCatching { registerCredoOtpReceiver(context, otpInbox) }.getOrNull()
        } else {
            null
        }
        onDispose { registration?.close() }
    }
    CredoSyncScreen(
        state = state,
        appLockEnabled = appLockEnabled,
        loginDraft = viewModel.loginDraft,
        incomingOtp = incomingOtp,
        onIncomingOtpConsumed = { incomingOtp = null },
        onOpenAppLock = onOpenAppLock,
        onConnect = { username, credential, rememberPassword ->
            otpInbox.beginChallenge()
            otpChallengeKey += 1
            viewModel.connect(username, credential, rememberPassword && appLockEnabled)
        },
        onSubmitOtp = viewModel::submitOtp,
        onResendOtp = {
            otpInbox.beginChallenge()
            otpChallengeKey += 1
            viewModel.resendOtp()
        },
        onSync = viewModel::sync,
        onLoadHistory = viewModel::loadHistory,
        onDisconnect = viewModel::disconnect,
        onDismissError = viewModel::dismissError,
        originalExportOutcome = originalExportOutcome,
        onDismissOriginalExportOutcome = { originalExportOutcome = null },
        onSaveOriginalStatement = { token, fileName ->
            originalExportOutcome = null
            pendingOriginalExport = PendingOriginalExport(token, fileName)
            createOriginalStatement.launch(fileName)
        },
        showCredentialManagement = showCredentialManagement,
    )
}

@Composable
fun CredoSyncScreen(
    state: CredoSyncUiState,
    appLockEnabled: Boolean,
    loginDraft: CredoLoginDraft? = null,
    incomingOtp: String? = null,
    onIncomingOtpConsumed: () -> Unit = {},
    onOpenAppLock: () -> Unit,
    onConnect: (String, String, Boolean) -> Unit,
    onSubmitOtp: (String) -> Unit,
    onResendOtp: () -> Unit,
    onSync: () -> Unit,
    onLoadHistory: () -> Unit,
    onDisconnect: () -> Unit,
    onDismissError: () -> Unit,
    originalExportOutcome: CredoOriginalExportOutcome? = null,
    onDismissOriginalExportOutcome: () -> Unit = {},
    onSaveOriginalStatement: (String, String) -> Unit = { _, _ -> },
    showCredentialManagement: Boolean = false,
) {
    val usableSavedPassword = appLockEnabled && state.hasSavedPassword
    // A direct preview/test gets a composition-local draft. The real route passes a ViewModel-owned
    // draft so App Lock navigation cannot erase the form, without ever saving the password.
    val draft = loginDraft ?: remember {
        CredoLoginDraft(username = if (appLockEnabled) state.savedUsername.orEmpty() else "")
    }
    var otp by remember { mutableStateOf("") }
    var rememberPassword by rememberSaveable(state.hasSavedPassword, appLockEnabled) {
        mutableStateOf(usableSavedPassword)
    }

    LaunchedEffect(state.savedUsername, appLockEnabled) {
        if (appLockEnabled && draft.username.isBlank()) draft.username = state.savedUsername.orEmpty()
    }
    LaunchedEffect(appLockEnabled) {
        if (!appLockEnabled) rememberPassword = false
    }
    LaunchedEffect(state.stage) {
        if (state.stage != CredoSyncStage.AwaitingOtp) otp = ""
        if (state.stage == CredoSyncStage.AwaitingOtp || state.stage == CredoSyncStage.Connected) {
            draft.credential = ""
        }
    }
    LaunchedEffect(state.errorCode) {
        if (state.stage == CredoSyncStage.AwaitingOtp && state.errorCode == "INVALID_OTP") otp = ""
    }
    LaunchedEffect(state.stage, incomingOtp) {
        if (state.stage == CredoSyncStage.AwaitingOtp && incomingOtp != null) {
            otp = incomingOtp.filter(Char::isDigit).take(4)
            onIncomingOtpConsumed()
        }
    }

    if (state.stage == CredoSyncStage.AwaitingOtp) {
        OtpContent(
            mobileHint = state.mobileHint,
            error = state.errorCode?.let { credoErrorMessage(it) },
            otp = otp,
            onOtpChange = { value -> otp = value.filter(Char::isDigit).take(4) },
            onSubmit = { onSubmitOtp(otp) },
            onResend = {
                otp = ""
                onResendOtp()
            },
            loading = state.isBusy,
        )
        return
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
        val savedProfile = usableSavedPassword && state.stage in setOf(
            CredoSyncStage.Disconnected,
            CredoSyncStage.Connecting,
        )
        if (!savedProfile && state.stage in setOf(
                CredoSyncStage.Disconnected,
                CredoSyncStage.Connecting,
            )
        ) {
            WhfinNotice(
                title = stringResource(R.string.credo_sync_experimental_title),
                body = stringResource(R.string.credo_sync_experimental_body),
                icon = Icons.Default.Security,
                kind = WhfinNoticeKind.Info,
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
            -> if (savedProfile) {
                SavedProfileContent(
                    loading = state.stage == CredoSyncStage.Connecting,
                    ready = draft.username.isNotBlank(),
                    showCredentialManagement = showCredentialManagement,
                    onSync = { onConnect(draft.username, "", true) },
                    onForget = onDisconnect,
                )
            } else {
                LoginContent(
                    username = draft.username,
                    onUsernameChange = { draft.username = it },
                    credential = draft.credential,
                    onCredentialChange = { draft.credential = it },
                    hasSavedPassword = false,
                    rememberPassword = rememberPassword,
                    onRememberPasswordChange = { rememberPassword = it },
                    canRememberPassword = appLockEnabled,
                    onOpenAppLock = onOpenAppLock,
                    loading = state.stage == CredoSyncStage.Connecting,
                    onConnect = { onConnect(draft.username, draft.credential, rememberPassword) },
                )
            }

            CredoSyncStage.AwaitingOtp -> Unit // handled by the fixed keypad surface above

            CredoSyncStage.Connected,
            CredoSyncStage.Syncing,
            -> ConnectedContent(
                state = state,
                onSync = onSync,
                onLoadHistory = onLoadHistory,
                onDisconnect = onDisconnect,
                originalExportOutcome = originalExportOutcome,
                onDismissOriginalExportOutcome = onDismissOriginalExportOutcome,
                onSaveOriginalStatement = onSaveOriginalStatement,
                showCredentialManagement = showCredentialManagement,
            )
        }
    }
}

@Composable
private fun SavedProfileContent(
    loading: Boolean,
    ready: Boolean,
    showCredentialManagement: Boolean,
    onSync: () -> Unit,
    onForget: () -> Unit,
) {
    WhfinSectionLabel(stringResource(R.string.credo_sync_saved_profile_title))
    Text(
        stringResource(R.string.credo_sync_saved_profile_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    WhfinButton(
        label = stringResource(
            if (loading) R.string.credo_sync_connecting else R.string.credo_sync_now,
        ),
        onClick = onSync,
        modifier = Modifier.fillMaxWidth(),
        enabled = ready && !loading,
        leadingIcon = if (loading) null else Icons.Default.CloudSync,
    )
    if (showCredentialManagement) {
        WhfinButton(
            label = stringResource(R.string.credo_sync_disconnect),
            onClick = onForget,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            style = WhfinActionStyle.Quiet,
        )
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
    onOpenAppLock: () -> Unit,
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
                supportingMaxLines = 3,
                icon = Icons.Default.Lock,
                trailing = {
                    if (canRememberPassword) {
                        WhfinSwitch(
                            checked = rememberPassword,
                            onCheckedChange = onRememberPasswordChange,
                            contentDescription = stringResource(R.string.credo_sync_remember_password),
                        )
                    } else {
                        WhfinIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.credo_sync_protect_action),
                            onClick = onOpenAppLock,
                            outlined = false,
                        )
                    }
                },
                onClick = if (canRememberPassword) null else onOpenAppLock,
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
    error: String?,
    otp: String,
    onOtpChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    loading: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WhfinSectionLabel(stringResource(R.string.credo_sync_otp_section))
            Text(
                text = buildString {
                    append(
                        if (mobileHint.isNullOrBlank()) stringResource(R.string.credo_sync_otp_body)
                        else stringResource(R.string.credo_sync_otp_body_with_phone, mobileHint),
                    )
                    append('\n').append(stringResource(R.string.credo_sync_otp_autofill_hint))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (error != null) Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            WhfinCodeDots(
                length = 4,
                filled = otp.length,
                contentDescription = stringResource(R.string.credo_sync_otp_progress, otp.length, 4),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        WhfinNumericKeypad(
            deleteContentDescription = stringResource(R.string.credo_sync_delete_digit),
            onDigit = { digit -> if (otp.length < 4) onOtpChange(otp + digit) },
            onBackspace = { if (otp.isNotEmpty()) onOtpChange(otp.dropLast(1)) },
            enabled = !loading,
        )
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
}

@Composable
private fun ConnectedContent(
    state: CredoSyncUiState,
    onSync: () -> Unit,
    onLoadHistory: () -> Unit,
    onDisconnect: () -> Unit,
    originalExportOutcome: CredoOriginalExportOutcome?,
    onDismissOriginalExportOutcome: () -> Unit,
    onSaveOriginalStatement: (String, String) -> Unit,
    showCredentialManagement: Boolean,
) {
    val syncing = state.stage == CredoSyncStage.Syncing
    WhfinSectionLabel(stringResource(R.string.credo_sync_saved_profile_title))
    Text(
        stringResource(R.string.credo_sync_accounts_found, state.accounts.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (syncing) {
        WhfinNotice(
            title = stringResource(
                R.string.credo_sync_progress_title,
                state.currentAccount,
                state.currentAccountTotal.takeIf { it > 0 } ?: state.accounts.size,
            ),
            body = when {
                state.valuedDays > 0 -> stringResource(R.string.credo_sync_valuing, state.valuedDays)
                state.currentChunk > 0 -> stringResource(
                    R.string.credo_sync_history_progress,
                    state.currentChunk,
                )
                else -> stringResource(state.currentPhase.phaseLabel())
            },
            icon = Icons.Default.CloudSync,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    WhfinButton(
        label = stringResource(
            when {
                syncing -> R.string.credo_sync_syncing
                state.retryableFailures > 0 -> R.string.credo_sync_retry_failed
                else -> R.string.credo_sync_now
            },
        ),
        onClick = onSync,
        modifier = Modifier.fillMaxWidth(),
        enabled = !syncing,
        leadingIcon = Icons.Default.CloudSync,
    )
    // Reaching past the year a sync covers is a deliberate, one-off request, not a faster sync.
    WhfinButton(
        label = stringResource(R.string.credo_sync_history_action),
        onClick = onLoadHistory,
        modifier = Modifier.fillMaxWidth(),
        enabled = !syncing,
        style = WhfinActionStyle.Secondary,
        leadingIcon = Icons.Default.History,
    )
    Text(
        stringResource(R.string.credo_sync_history_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.results.isNotEmpty() || state.unchanged > 0) {
        WhfinSectionLabel(
            stringResource(
                if (state.resultsAreRetained) {
                    R.string.credo_sync_result_section_retained
                } else {
                    R.string.credo_sync_result_section
                },
            ),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            state.results.forEachIndexed { index, file ->
                val hasFailure = file.errorCode != null || file.detail != null
                WhfinLedgerRow(
                    title = file.accountLabel,
                    supportingText = buildString {
                        if (file.errorCode == null) {
                            append(
                                stringResource(
                                    R.string.credo_sync_result_success,
                                    file.inserted,
                                    file.duplicates,
                                    file.reconciled,
                                ),
                            )
                            if (file.unmappedOperationNames.isNotEmpty()) {
                                append('\n')
                                append(
                                    pluralStringResource(
                                        R.plurals.statements_unmapped_operations,
                                        file.unmappedOperationNames.size,
                                        file.unmappedOperationNames.size,
                                    ),
                                )
                                append('\n')
                                append(
                                    stringResource(
                                        R.string.statements_unmapped_labels,
                                        file.unmappedOperationNames.sorted().joinToString(),
                                    ),
                                )
                            }
                        } else {
                            append(
                                if (file.errorCode == "NETWORK_ERROR") {
                                    stringResource(R.string.credo_sync_statement_network_error)
                                } else {
                                    credoErrorMessage(file.errorCode)
                                },
                            )
                        }
                        // A history walk may report both imported rows and the later rule/window
                        // that stopped it. The detail is always WHFIN's wording, never bank data.
                        file.detail?.let { append('\n').append(it) }
                        if (hasFailure && file.askedFrom != null && file.askedTo != null) {
                            append('\n')
                            append(
                                stringResource(
                                    R.string.credo_sync_error_window,
                                    file.askedFrom,
                                    file.askedTo,
                                ),
                            )
                        }
                        if (file.originalStatementToken != null) {
                            append('\n').append(stringResource(R.string.credo_sync_original_available))
                        }
                    },
                    icon = Icons.Default.AccountBalance,
                    iconTint = if (hasFailure) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    trailing = if (
                        file.originalStatementToken != null && file.originalStatementFileName != null
                    ) {
                        {
                            WhfinIconButton(
                                icon = Icons.Default.SaveAlt,
                                contentDescription = stringResource(R.string.credo_sync_original_save),
                                onClick = {
                                    onSaveOriginalStatement(
                                        file.originalStatementToken,
                                        file.originalStatementFileName,
                                    )
                                },
                            )
                        }
                    } else null,
                    supportingMaxLines = if (file.originalStatementToken != null) 6 else 5,
                    divider = index != state.results.lastIndex,
                )
            }
        }
        // A quiet account is not a result of its own — the run says it once.
        if (state.unchanged > 0) Text(
            pluralStringResource(R.plurals.credo_sync_unchanged, state.unchanged, state.unchanged),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    originalExportOutcome?.let { outcome ->
        WhfinNotice(
            title = stringResource(
                if (outcome == CredoOriginalExportOutcome.Saved) {
                    R.string.credo_sync_original_saved_title
                } else {
                    R.string.credo_sync_original_error_title
                },
            ),
            body = stringResource(
                if (outcome == CredoOriginalExportOutcome.Saved) {
                    R.string.credo_sync_original_saved_body
                } else {
                    R.string.credo_sync_original_error_body
                },
            ),
            kind = if (outcome == CredoOriginalExportOutcome.Saved) {
                WhfinNoticeKind.Info
            } else {
                WhfinNoticeKind.Error
            },
            actionLabel = stringResource(R.string.action_dismiss),
            onAction = onDismissOriginalExportOutcome,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showCredentialManagement) {
        WhfinButton(
            label = stringResource(R.string.credo_sync_disconnect),
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !syncing,
            style = WhfinActionStyle.Quiet,
        )
    }
}

@Composable
private fun credoErrorMessage(code: String): String = when (code) {
    "CREDENTIALS_REQUIRED", "INVALID_INPUT_DATA" -> stringResource(R.string.credo_sync_error_credentials)
    "INVALID_OTP" -> stringResource(R.string.credo_sync_error_otp)
    "OTP_NOT_SENT" -> stringResource(R.string.credo_sync_error_otp_not_sent)
    "USER_IS_BLOCKED", "USER_OTP_BLOCKED" -> stringResource(R.string.credo_sync_error_blocked)
    "UNAUTHORIZED", "LOGIN_EXPIRED", "SESSION_EXPIRED" -> stringResource(R.string.credo_sync_error_expired)
    "NETWORK_ERROR" -> stringResource(R.string.credo_sync_error_network)
    "HTTP_403", "HTTP_429" -> stringResource(R.string.credo_sync_error_protection)
    "NO_ACCOUNTS" -> stringResource(R.string.credo_sync_error_no_accounts)
    "EMPTY_STATEMENT" -> stringResource(R.string.credo_sync_error_empty)
    "INVALID_STATEMENT" -> stringResource(R.string.credo_sync_error_download)
    "STATEMENT_UNREADABLE", "STATEMENT_REJECTED" -> stringResource(R.string.credo_sync_error_statement)
    "UNSUPPORTED_STATEMENT" -> stringResource(R.string.statements_unsupported)
    "AMBIGUOUS_LEDGER" -> stringResource(R.string.credo_sync_error_ambiguous)
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
                appLockEnabled = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo OTP filled light", widthDp = 400, heightDp = 700, showBackground = true)
@Preview(
    name = "Credo OTP filled dark",
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Credo OTP filled font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f)
@Preview(name = "Credo OTP filled compact", widthDp = 400, heightDp = 640)
@Composable
private fun CredoOtpPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp, mobileHint = "+995 *** ** 42"),
                appLockEnabled = true,
                incomingOtp = "4821",
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
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
                appLockEnabled = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo rejected XLSX light", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(
    name = "Credo rejected XLSX dark",
    widthDp = 400,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Credo rejected XLSX font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f)
@Preview(name = "Credo rejected XLSX compact", widthDp = 400, heightDp = 640)
@Composable
private fun CredoRejectedStatementPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(
                    stage = CredoSyncStage.Connected,
                    accounts = previewAccounts.take(1),
                    results = listOf(
                        CredoSyncFileResult(
                            accountLabel = "Current account · •0001 · GEL",
                            errorCode = "STATEMENT_REJECTED",
                            detail = "Statement balance summary is incomplete.",
                            askedFrom = "2025-08-11",
                            askedTo = "2026-08-11",
                            originalStatementToken = "memory-only-preview",
                            originalStatementFileName = "mycredo_gel_0001.xlsx",
                        ),
                    ),
                ),
                appLockEnabled = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}

@Preview(name = "Credo partial retry light", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(
    name = "Credo partial retry dark",
    widthDp = 400,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Credo partial retry font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f)
@Preview(name = "Credo partial retry compact", widthDp = 400, heightDp = 640)
@Composable
private fun CredoPartialRetryPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CredoSyncScreen(
                state = CredoSyncUiState(
                    stage = CredoSyncStage.Connected,
                    accounts = previewAccounts,
                    retryableFailures = 2,
                    unchanged = 7,
                    results = listOf(
                        CredoSyncFileResult(
                            accountLabel = "Current account · •0001 · EUR",
                            errorCode = "NETWORK_ERROR",
                            askedFrom = "2025-08-12",
                            askedTo = "2026-08-12",
                        ),
                        CredoSyncFileResult(
                            accountLabel = "Deposit · •0002 · GEL",
                            errorCode = "NETWORK_ERROR",
                            askedFrom = "2025-08-12",
                            askedTo = "2026-08-12",
                        ),
                    ),
                ),
                appLockEnabled = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
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
                appLockEnabled = false,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
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
                appLockEnabled = true,
                onOpenAppLock = {}, onConnect = { _, _, _ -> }, onSubmitOtp = {}, onResendOtp = {},
                onSync = {}, onLoadHistory = {}, onDisconnect = {}, onDismissError = {},
            )
        }
    }
}
