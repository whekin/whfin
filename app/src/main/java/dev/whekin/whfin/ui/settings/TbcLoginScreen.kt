package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.launch
import dev.whekin.whfin.data.sms.SmsOtpConsent
import dev.whekin.whfin.data.sms.registerTbcOtpReceiver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.security.LocalSensitiveActions
import dev.whekin.whfin.data.security.SensitiveAction
import dev.whekin.whfin.data.importer.TbcHistorySync
import dev.whekin.whfin.data.tbc.TbcAccount
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun TbcLoginRoute(canStoreSession: Boolean, demoMode: Boolean, onOpenStatements: () -> Unit = {}, routineSyncRequestKey: Int = 0, onRoutineSyncConsumed: () -> Unit = {}, viewModelOverride: TbcLoginViewModel? = null) {
    if (demoMode) {
        Text(stringResource(R.string.demo_mode_live_import_unavailable), Modifier.padding(20.dp))
        return
    }
    val vm: TbcLoginViewModel = viewModelOverride ?: (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.whekin.whfin.WhfinApp).bankSync.tbc
    val activity = androidx.compose.ui.platform.LocalContext.current as? dev.whekin.whfin.MainActivity
    DisposableEffect(activity) {
        activity?.protectBankScreen(true)
        onDispose { activity?.protectBankScreen(false) }
    }
    val state by vm.state.collectAsState()
    val sensitive = LocalSensitiveActions.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val otpInbox = remember(context) { (context.applicationContext as dev.whekin.whfin.WhfinApp).tbcOtpInbox }
    val scope = rememberCoroutineScope()
    var incomingOtp by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var preparation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var otpKey by remember { mutableIntStateOf(0) }
    var consentSince by remember { mutableLongStateOf(0) }
    val smsConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && consentSince != 0L && consentSince == otpInbox.challengeSince) result.data
            ?.getStringExtra(com.google.android.gms.auth.api.phone.SmsRetriever.EXTRA_SMS_MESSAGE)?.let(otpInbox::acceptConsented)
        consentSince = 0
    }
    // Register before any bank request. The inbox ignores everything outside its active window.
    DisposableEffect(context, otpInbox) {
        val broadcast = runCatching { registerTbcOtpReceiver(context, otpInbox) }.getOrNull()
        val consent = runCatching { SmsOtpConsent.register(context) { intent ->
            if (otpInbox.challengeSince != 0L && !otpInbox.hasCode) {
                consentSince = otpInbox.challengeSince
                runCatching { smsConsent.launch(intent) }
            }
        } }.getOrNull()
        onDispose { broadcast?.close(); consent?.close(); otpInbox.endChallenge() }
    }
    fun beginOtp(action: () -> Unit) {
        if (preparing || state.stage == TbcLoginStage.Working) return
        otpInbox.beginChallenge(); incomingOtp = null; otpKey++
        preparing = true
        val generation = otpKey
        preparation = scope.launch {
            try {
                SmsOtpConsent.prepare(context)
                if (generation == otpKey && otpInbox.challengeSince != 0L) action()
            } finally { if (generation == otpKey) preparing = false }
        }
    }
    LaunchedEffect(otpInbox) {
        otpInbox.codes.collect { incomingOtp = it; otpInbox.clearBufferedCode() }
    }
    LaunchedEffect(otpKey) {
        val since = otpInbox.challengeSince
        if (since == 0L || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        val reader = dev.whekin.whfin.data.sms.SmsHistoryReader(context.contentResolver)
        while (otpInbox.challengeSince == since && System.currentTimeMillis() - since < 5 * 60_000L) {
            val message = runCatching { reader.tbcLoginCodeSince(since) }.getOrNull()
            if (message != null && otpInbox.accept(message.body, "TBCSMS", message.receivedAt)) break
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(state.stage, state.error, state.syncProgress, state.otpApp, state.sessionVerified, preparing) {
        if (!preparing && (state.sessionVerified || state.stage == TbcLoginStage.Connected || state.syncProgress != null ||
                (state.stage == TbcLoginStage.Login && state.error != null) || (state.stage == TbcLoginStage.Code && state.otpApp))) {
            otpInbox.endChallenge(); incomingOtp = null
        }
    }
    LaunchedEffect(canStoreSession, routineSyncRequestKey) {
        vm.storageAllowed(canStoreSession)
        if (routineSyncRequestKey > 0) {
            onRoutineSyncConsumed()
            if (state.hasSaved && canStoreSession) sensitive.require(SensitiveAction.BankCredential) { beginOtp { vm.restore() } }
        }
    }
    TbcLoginScreen(if (preparing) state.copy(stage = TbcLoginStage.Working) else state, canStoreSession,
        { username, password -> beginOtp { vm.login(username, password) } }, vm::confirm, vm::setRemember,
        onRestore = { sensitive.require(SensitiveAction.BankCredential) { beginOtp { vm.restore() } } },
        onRefresh = vm::syncTransactions, onForget = vm::forget,
        onCancel = { otpKey++; preparation?.cancel(); preparing = false; otpInbox.endChallenge(); incomingOtp = null; vm.leave() },
        onOpenStatements = onOpenStatements, onConfirmBalance = vm::confirmBalance,
        incomingOtp = incomingOtp, onOtpConsumed = { incomingOtp = null })

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
    onConfirmBalance: (String, Long) -> Unit = { _, _ -> },
    incomingOtp: String? = null,
    onOtpConsumed: () -> Unit = {},
) {
    // Deliberately not rememberSaveable: neither secret belongs in instance state.
    var username by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var usePassword by remember(state.hasSaved) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.stage, incomingOtp, state.error) {
        if (state.stage != TbcLoginStage.Code || state.error == "OTP") code = ""
        if (state.stage == TbcLoginStage.Code && !state.otpApp && incomingOtp != null) {
            val delivered = incomingOtp.takeIf { it.matches(Regex("[0-9]{4,8}")) }
            if (delivered != null) code = delivered
            onOtpConsumed()
            // Same rule as Credo: a code that arrived by itself was already agreed to once, by the
            // consent dialog or by the message reaching this phone. Asking the owner to confirm
            // four digits they did not type is asking them to agree to their own agreement.
            if (delivered != null) { onCode(delivered); code = "" }
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    if (state.stage == TbcLoginStage.Code) {
        LaunchedEffect(Unit) { keyboard?.hide() }
        TbcOtpContent(state, code, { code = it }, { onCode(code); code = "" }, onCancel)
        return
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        dev.whekin.whfin.ui.banks.BankBrand("TBC")
        if (state.error != null) {
            WhfinNotice(title = stringResource(R.string.credo_sync_error_title), body = stringResource(tbcErrorText(state.error)),
                kind = WhfinNoticeKind.Info, modifier = Modifier.fillMaxWidth())
        }
        when (state.stage) {
            TbcLoginStage.Login -> {
                if (state.hasSaved && canStoreSession && !usePassword) {
                    Text(stringResource(R.string.tbc_saved_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (state.hasSavedCredentials) R.string.tbc_saved_body else R.string.tbc_saved_legacy_body), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WhfinButton(stringResource(R.string.tbc_resume), onRestore, Modifier.fillMaxWidth())
                    WhfinButton(stringResource(R.string.tbc_use_password), { usePassword = true }, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
                    WhfinButton(stringResource(R.string.tbc_forget), onForget, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
                } else {
                    Text(stringResource(R.string.tbc_login_intro), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WhfinField(username, { username = it }, stringResource(R.string.credo_sync_username),
                        keyboardType = KeyboardType.Ascii, modifier = Modifier.fillMaxWidth())
                    WhfinField(credential, { credential = it }, stringResource(R.string.credo_sync_password),
                        keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (canStoreSession) {
                        WhfinLedgerRow(title = stringResource(R.string.tbc_remember),
                            supportingText = stringResource(R.string.tbc_remember_detail), supportingMaxLines = 3,
                            modifier = Modifier.toggleable(value = state.remember, role = Role.Switch, onValueChange = {
                                haptics.performHapticFeedback(WhfinHaptics.toggle(it)); onRemember(it)
                            }),
                            trailing = { WhfinSwitch(state.remember, null, contentDescription = stringResource(R.string.tbc_remember)) })
                    } else {
                        Text(stringResource(R.string.tbc_lock_needed), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WhfinButton(stringResource(R.string.tbc_login), {
                        onLogin(username, credential); credential = ""; keyboard?.hide()
                    }, Modifier.fillMaxWidth(), enabled = username.isNotBlank() && credential.isNotEmpty())
                    if (state.hasSaved && canStoreSession) WhfinButton(stringResource(R.string.tbc_back_to_saved),
                        { usePassword = false; credential = "" }, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
                }
            }
            TbcLoginStage.Working -> {
                WhfinLoadingIndicator(Modifier.size(36.dp))
                Text(if (state.syncProgress != null) stringResource(R.string.tbc_sync_progress, state.syncProgress.first, state.syncProgress.second)
                    else stringResource(R.string.tbc_working))
                WhfinButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            }
            TbcLoginStage.Code -> Unit // Dedicated keypad surface above.
            TbcLoginStage.Connected -> TbcConnectedContent(state, onRefresh, onForget, onOpenStatements,
                onConfirmBalance, { keyboard?.hide() })
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * What the sync did, read in the order the owner needs it.
 *
 * The accounts that want an answer come first and carry their own action; everything else is one
 * list of accounts, each saying what happened to it. The technical read — pages, bank rows, holds —
 * stays behind one disclosure, because it answers "why is this number what it is", not "what now".
 * A failure belongs to the row it happened on rather than to a separate paragraph of red text.
 */
@Composable
private fun ColumnScope.TbcConnectedContent(
    state: TbcLoginState,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
    onOpenStatements: () -> Unit,
    onConfirmBalance: (String, Long) -> Unit,
    onHideKeyboard: () -> Unit,
) {
    val result = state.syncResult
    val waiting = result?.needsStatement.orEmpty()
    val reports = result?.reports.orEmpty()
    val failures = result?.errors.orEmpty().associate {
        it.substringBeforeLast(":").trim() to it.substringAfterLast(":").trim()
    }
    val unreported = failures.filterKeys { label -> reports.none { it.label == label } }
    var showReadDetails by remember(result) { mutableStateOf(false) }

    Text(stringResource(R.string.tbc_connected), style = MaterialTheme.typography.titleLarge)
    if (state.hasSaved) Text(stringResource(R.string.tbc_saved_title), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (result != null && (result.inserted > 0 || result.matched > 0 || result.unchanged > 0 || waiting.isEmpty()))
        Text(stringResource(R.string.tbc_sync_result, result.inserted, result.matched),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (waiting.isNotEmpty() || unreported.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhfinSectionLabel(stringResource(R.string.tbc_section_attention))
            if (waiting.isNotEmpty()) {
                Text(stringResource(R.string.tbc_initial_statement), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                var explainBalance by remember { mutableStateOf(false) }
                WhfinButton(stringResource(R.string.tbc_balance_help), { explainBalance = !explainBalance },
                    style = WhfinActionStyle.Quiet)
                if (explainBalance) Text(stringResource(R.string.tbc_balance_help_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    waiting.forEachIndexed { index, remote ->
                        val initial = result?.initialHistories?.singleOrNull { it.remote.key == remote.key }
                        WhfinLedgerRow(title = remote.label, supportingText = stringResource(R.string.tbc_row_needs_balance),
                            icon = Icons.Default.AccountBalance, divider = index < waiting.lastIndex)
                        if (initial != null) {
                            // The bank prints a figure for the account; what it means is not documented,
                            // so it is an offer to check rather than an anchor.
                            val suggested = remote.balanceMinor?.let(::formatBookedBalance)
                            var balance by remember(remote.key, initial.readAt) { mutableStateOf(suggested.orEmpty()) }
                            val parsed = parseBookedBalance(balance)
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                WhfinField(balance, { balance = it }, stringResource(R.string.tbc_booked_balance_field),
                                    keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
                                if (suggested != null) Text(stringResource(R.string.tbc_balance_prefilled),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                WhfinButton(stringResource(R.string.tbc_confirm_balance), {
                                    parsed?.let { onConfirmBalance(remote.key, it) }; onHideKeyboard()
                                }, Modifier.fillMaxWidth(), enabled = parsed != null)
                            }
                        }
                    }
                }
                WhfinButton(stringResource(R.string.statements_upload), onOpenStatements,
                    Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            }
            // A product family that could not be listed is its own failure, not part of the form above.
            if (unreported.isNotEmpty()) WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                unreported.entries.forEachIndexed { index, (label, code) ->
                    WhfinLedgerRow(title = if (label == TbcHistorySync.DEPOSITS_LABEL)
                        stringResource(R.string.tbc_deposits_family) else label,
                        supportingText = stringResource(tbcErrorText(code)),
                        supportingMaxLines = 4, icon = Icons.Default.AccountBalance,
                        iconTint = MaterialTheme.colorScheme.error, divider = index < unreported.size - 1)
                }
            }
        }
    }

    val synced = reports.filterNot { it.waitingForBalance }
    val listed = state.accounts.filter { account -> waiting.none { it.iban == account.iban && it.currency == account.currency } }
    if (synced.isEmpty() && listed.isEmpty() && waiting.isEmpty() && state.error == null) {
        Text(stringResource(R.string.tbc_no_accounts))
    } else if (synced.isNotEmpty() || listed.isNotEmpty()) {
        WhfinSectionLabel(stringResource(R.string.tbc_section_accounts))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            if (synced.isNotEmpty()) synced.forEachIndexed { index, report ->
                val failure = report.error ?: failures[report.label]
                WhfinLedgerRow(
                    title = report.label,
                    supportingText = buildString {
                        if (failure != null) append(stringResource(tbcErrorText(failure))) else {
                            val outcome = buildList {
                                if (report.inserted > 0) add(stringResource(R.string.tbc_row_new, report.inserted))
                                if (report.matched > 0) add(stringResource(R.string.tbc_row_matched, report.matched))
                                if (isEmpty()) add(stringResource(R.string.tbc_row_unchanged))
                            }
                            append(outcome.joinToString(" · "))
                        }
                        if (report.bankBalanceDiffers) { append("\n"); append(stringResource(R.string.tbc_read_deposit_gap)) }
                        if (report.readAsLedger) { append("\n"); append(stringResource(R.string.tbc_read_deposit_as_ledger)) }
                        if (showReadDetails) {
                            append("\n"); append(stringResource(R.string.tbc_read_counts, report.received, report.alreadyKnown))
                            append("\n"); append(stringResource(R.string.tbc_pending_count, report.pending))
                            append("\n"); append(stringResource(if (report.fullHistory) R.string.tbc_read_all else R.string.tbc_read_recent))
                            report.stats?.let { stats ->
                                append("\n"); append(stringResource(R.string.tbc_read_pages, stats.pages, stats.parsed, stats.blocked))
                                if (stats.firstPageEmpty) { append("\n"); append(stringResource(R.string.tbc_read_empty)) }
                            }
                        }
                    },
                    supportingMaxLines = if (showReadDetails) 10 else 4,
                    icon = Icons.Default.AccountBalance,
                    iconTint = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trailing = if (report.inserted > 0) {
                        { Text("+${report.inserted}", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    divider = index < synced.lastIndex,
                )
            } else listed.forEachIndexed { index, account ->
                WhfinLedgerRow(title = "${account.currency} · •${account.iban.takeLast(4)}",
                    supportingText = account.name.takeIf { it.isNotBlank() }, icon = Icons.Default.AccountBalance,
                    divider = index < listed.lastIndex)
            }
        }
        if (synced.isNotEmpty()) WhfinButton(stringResource(R.string.tbc_read_details),
            { showReadDetails = !showReadDetails }, style = WhfinActionStyle.Quiet)
    }

    WhfinButton(stringResource(R.string.tbc_sync_action), onRefresh, Modifier.fillMaxWidth(),
        style = if (waiting.isEmpty()) WhfinActionStyle.Primary else WhfinActionStyle.Secondary)
    WhfinButton(stringResource(R.string.tbc_forget), onForget, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
}

/** A booked balance can be zero or negative; transaction amount validation is different. */
internal fun parseBookedBalance(text: String): Long? = runCatching {
    text.replace(" ", "").replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
}.getOrNull()

/** Plain editable digits, not a localized money label: the field parses what it shows. */
internal fun formatBookedBalance(minor: Long): String =
    java.math.BigDecimal.valueOf(minor, 2).toPlainString()

@Composable
private fun TbcOtpContent(state: TbcLoginState, code: String, onChange: (String) -> Unit,
    onSubmit: () -> Unit, onCancel: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
        val compact = maxHeight < 680.dp
        Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.tbc_code), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(if (state.otpApp) R.string.tbc_code_app else R.string.tbc_code_sms),
                    modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (!state.otpApp) Text(stringResource(R.string.tbc_otp_autofill),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                state.error?.let { Text(stringResource(tbcErrorText(it)), color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
                WhfinCodeDots(length = maxOf(4, code.length), filled = code.length,
                    contentDescription = stringResource(R.string.tbc_otp_progress, code.length),
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
            WhfinNumericKeypad(deleteContentDescription = stringResource(R.string.credo_sync_delete_digit),
                onDigit = { if (code.length < 8) onChange(code + it) },
                onBackspace = { onChange(code.dropLast(1)) })
            WhfinButton(stringResource(R.string.tbc_confirm), onSubmit, Modifier.fillMaxWidth(), enabled = code.length in 4..8)
            WhfinButton(stringResource(R.string.tbc_restart), onCancel, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
        }
    }
}

@Preview(showBackground = true, name = "TBC OTP")
@Preview(showBackground = true, name = "TBC OTP dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "TBC OTP compact large", fontScale = 1.5f, heightDp = 500)
@Composable
private fun TbcOtpPreview() = WhfinTheme { androidx.compose.material3.Surface {
    TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Code), true)
} }

internal fun tbcErrorText(code: String): Int = when (code) {
    "SYNC_INTERRUPTED" -> R.string.bank_sync_interrupted
    "LOGIN" -> R.string.tbc_error_login
    "OTP" -> R.string.tbc_error_code
    "SESSION" -> R.string.tbc_error_session
    "STORAGE" -> R.string.tbc_error_storage
    "PROTECTION", "RATE_LIMIT" -> R.string.tbc_error_protection
    "NETWORK" -> R.string.tbc_error_network
    "HISTORY_CONFLICT" -> R.string.tbc_history_conflict
    "HISTORY_PAGE", "HISTORY_FORMAT", "HISTORY_ACCOUNT", "HISTORY_HOLD" -> R.string.tbc_history_format
    "HISTORY_CHANGED" -> R.string.tbc_history_changed
    "DEPOSIT_FORMAT", "DEPOSIT_ACCOUNT", "DEPOSIT_CHAIN" -> R.string.tbc_deposit_format
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

@Preview(showBackground = true, name = "TBC saved")
@Preview(showBackground = true, name = "TBC saved dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "TBC saved large", fontScale = 1.5f, heightDp = 400)
@Composable
private fun TbcSavedPreview() = WhfinTheme {
    androidx.compose.material3.Surface { TbcLoginScreen(TbcLoginState(hasSaved = true, remember = true), true) }
}
