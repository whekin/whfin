package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinCodeDots
import dev.whekin.whfin.core.ui.WhfinNumericKeypad
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.AppLockPinStore
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.data.security.LocalSensitiveActions
import dev.whekin.whfin.data.security.SensitiveAction
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun AppLockScreen(
    timeout: AppLockTimeout,
    hasPin: Boolean,
    biometricAvailability: BiometricAvailability,
    biometricEnabled: Boolean,
    onTimeoutChange: (AppLockTimeout) -> Unit,
    onPinCreated: (String, AppLockTimeout) -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onOpenBiometricSettings: () -> Unit,
    /** Non-settings flows can request a secure default and open code setup in one tap. */
    autoSetupTimeout: AppLockTimeout? = null,
) {
    var setupTarget by remember(autoSetupTimeout) { mutableStateOf(autoSetupTimeout) }
    var changingCode by remember { mutableStateOf(false) }
    // Changing the code or the delay is how every other protected action stops being protected,
    // so it answers to the same gate. With no code yet there is nothing to verify against and
    // `require` runs the change straight through.
    val sensitive = LocalSensitiveActions.current
    if (setupTarget != null || changingCode) {
        PinSetup(
            onComplete = { pin ->
                onPinCreated(pin, setupTarget ?: timeout)
                setupTarget = null
                changingCode = false
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.app_lock_intro_title),
            body = stringResource(R.string.app_lock_intro_body),
            icon = Icons.Default.Security,
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
        )

        WhfinSectionLabel(stringResource(R.string.app_lock_delay_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            AppLockTimeout.entries.forEachIndexed { index, option ->
                WhfinLedgerRow(
                    title = stringResource(option.labelResource()),
                    supportingText = option.supportingResource()?.let { stringResource(it) },
                    icon = if (option.enabled) Icons.Default.Lock else null,
                    trailing = { RadioButton(selected = timeout == option, onClick = null) },
                    onClick = {
                        if (option.enabled && !hasPin) {
                            setupTarget = option
                        } else {
                            sensitive.require(SensitiveAction.AppLockSettings) { onTimeoutChange(option) }
                        }
                    },
                    divider = index != AppLockTimeout.entries.lastIndex,
                )
            }
        }

        if (hasPin) {
            WhfinSectionLabel(stringResource(R.string.app_lock_access_section))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow(
                    title = stringResource(R.string.app_lock_change_code),
                    supportingText = stringResource(R.string.app_lock_change_code_body),
                    icon = Icons.Default.Key,
                    onClick = {
                        sensitive.require(SensitiveAction.AppLockSettings) { changingCode = true }
                    },
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.app_lock_biometric_title),
                    supportingText = stringResource(biometricAvailability.supportingResource()),
                    icon = Icons.Default.Fingerprint,
                    trailing = {
                        Switch(
                            checked = biometricEnabled && biometricAvailability == BiometricAvailability.Available,
                            onCheckedChange = null,
                            enabled = biometricAvailability == BiometricAvailability.Available,
                        )
                    },
                    onClick = {
                        if (biometricAvailability == BiometricAvailability.Available) {
                            // Turning this on adds a second way in, so it is a change to the lock.
                            sensitive.require(SensitiveAction.AppLockSettings) {
                                onBiometricEnabledChange(!biometricEnabled)
                            }
                        } else if (biometricAvailability == BiometricAvailability.EnrollmentRequired) {
                            onOpenBiometricSettings()
                        }
                    },
                    divider = false,
                )
            }
        }
    }
}

@Composable
private fun PinSetup(onComplete: (String) -> Unit) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    WhfinPinPad(
        title = stringResource(
            if (firstPin == null) R.string.app_lock_create_code_title
            else R.string.app_lock_repeat_code_title,
        ),
        body = stringResource(
            if (firstPin == null) R.string.app_lock_create_code_body
            else R.string.app_lock_repeat_code_body,
        ),
        pin = pin,
        error = error?.let { stringResource(it) },
        onDigit = { digit ->
            if (pin.length >= AppLockPinStore.PIN_LENGTH) return@WhfinPinPad
            error = null
            val next = pin + digit
            pin = next
            if (next.length == AppLockPinStore.PIN_LENGTH) {
                if (firstPin == null) {
                    firstPin = next
                    pin = ""
                } else if (firstPin == next) {
                    onComplete(next)
                } else {
                    pin = ""
                    firstPin = null
                    error = R.string.app_lock_codes_mismatch
                }
            }
        },
        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
    )
}

@Composable
fun AppLockGate(
    biometricAvailable: Boolean,
    problem: dev.whekin.whfin.data.security.AppLockProblem?,
    onVerifyPin: (String) -> PinVerificationResult,
    onBiometric: () -> Unit,
    onUseCode: () -> Unit = {},
) {
    val resources = LocalResources.current
    var pin by remember { mutableStateOf("") }
    var pinProblem by remember { mutableStateOf<String?>(null) }
    // The keypad stays hidden while the system biometric prompt owns the screen. Any prompt outcome —
    // including its "Use WHFIN code" button, which reports Cancelled — has to reveal it, otherwise a
    // dismissed prompt would leave the mark alone on screen with no way in.
    var showCode by remember { mutableStateOf(!biometricAvailable) }
    LaunchedEffect(biometricAvailable, problem) {
        if (!biometricAvailable || problem != null) {
            if (!showCode) onUseCode()
            showCode = true
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!showCode) {
            AppLockWaiting(
                onBiometric = onBiometric,
                onUseCode = {
                    showCode = true
                    onUseCode()
                },
            )
            return@Surface
        }
        WhfinPinPad(
            title = stringResource(R.string.app_lock_gate_title),
            body = stringResource(R.string.app_lock_gate_body),
            pin = pin,
            error = pinProblem ?: problem?.let { stringResource(it.messageResource()) },
            onDigit = { digit ->
                if (pin.length >= AppLockPinStore.PIN_LENGTH) return@WhfinPinPad
                pinProblem = null
                val next = pin + digit
                pin = next
                if (next.length == AppLockPinStore.PIN_LENGTH) {
                    when (val result = onVerifyPin(next)) {
                        PinVerificationResult.Success -> Unit
                        is PinVerificationResult.Invalid -> {
                            pin = ""
                            pinProblem = resources.getString(
                                R.string.app_lock_wrong_code,
                                result.attemptsRemaining,
                            )
                        }
                        is PinVerificationResult.Locked -> {
                            pin = ""
                            pinProblem = resources.getString(
                                R.string.app_lock_code_locked,
                                (result.retryAfterMillis / 1_000L).coerceAtLeast(1L),
                            )
                        }
                    }
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            showBiometric = biometricAvailable,
            onBiometric = onBiometric,
            applySystemInsets = true,
        )
    }
}

/**
 * Re-authentication for a single sensitive action, shown over the screen that asked for it so the
 * caller stays composed — a document picker launched after this must not lose its result to a
 * cancelled composition.
 *
 * The keypad is visible from the start rather than hidden behind the system prompt: the prompt is
 * dismissible here, and the action always has a way out through Cancel.
 */
@Composable
fun SensitiveActionGate(
    action: SensitiveAction,
    biometricAvailable: Boolean,
    problem: dev.whekin.whfin.data.security.AppLockProblem?,
    onVerifyPin: (String) -> PinVerificationResult,
    onBiometric: () -> Unit,
    onCancel: () -> Unit,
) {
    val resources = LocalResources.current
    var pin by remember(action) { mutableStateOf("") }
    var pinProblem by remember(action) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        WhfinPinPad(
            title = stringResource(R.string.sensitive_gate_title),
            body = stringResource(action.bodyResource()),
            pin = pin,
            error = pinProblem ?: problem?.let { stringResource(it.messageResource()) },
            onDigit = { digit ->
                if (pin.length >= AppLockPinStore.PIN_LENGTH) return@WhfinPinPad
                pinProblem = null
                val next = pin + digit
                pin = next
                if (next.length == AppLockPinStore.PIN_LENGTH) {
                    when (val result = onVerifyPin(next)) {
                        PinVerificationResult.Success -> Unit
                        is PinVerificationResult.Invalid -> {
                            pin = ""
                            pinProblem = resources.getString(
                                R.string.app_lock_wrong_code,
                                result.attemptsRemaining,
                            )
                        }
                        is PinVerificationResult.Locked -> {
                            pin = ""
                            pinProblem = resources.getString(
                                R.string.app_lock_code_locked,
                                (result.retryAfterMillis / 1_000L).coerceAtLeast(1L),
                            )
                        }
                    }
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            showBiometric = biometricAvailable,
            onBiometric = onBiometric,
            applySystemInsets = true,
        )
        // The way out sits in the corner rather than under the keypad: at font scale 1.5 the pad
        // already fills the screen, and a way out that scrolls off is not one.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            WhfinIconButton(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel),
                onClick = onCancel,
                outlined = false,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

internal fun SensitiveAction.bodyResource(): Int = when (this) {
    SensitiveAction.BackupExport -> R.string.sensitive_gate_backup_export
    SensitiveAction.BackupRestore -> R.string.sensitive_gate_backup_restore
    SensitiveAction.BankCredential -> R.string.sensitive_gate_bank_credential
    SensitiveAction.AppLockSettings -> R.string.sensitive_gate_app_lock
}

@Composable
private fun AppLockWaiting(
    onBiometric: () -> Unit,
    onUseCode: () -> Unit,
) {
    // The middle of the screen is not ours: One UI puts its fingerprint sheet there, and a centred
    // mark sat directly under it. The mark takes the top, the actions the bottom, and the prompt
    // covers the empty space between them.
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp).size(112.dp),
        )
        Text(
            stringResource(R.string.app_lock_gate_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.app_lock_prompt_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        WhfinButton(
            label = stringResource(R.string.app_lock_use_biometrics),
            onClick = onBiometric,
            style = WhfinActionStyle.Secondary,
            leadingIcon = Icons.Default.Fingerprint,
        )
        WhfinButton(
            label = stringResource(R.string.app_lock_use_code),
            onClick = onUseCode,
            style = WhfinActionStyle.Quiet,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun WhfinPinPad(
    title: String,
    body: String,
    pin: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    showBiometric: Boolean = false,
    onBiometric: () -> Unit = {},
    applySystemInsets: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (applySystemInsets) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        WhfinCodeDots(
            length = AppLockPinStore.PIN_LENGTH,
            filled = pin.length,
            modifier = Modifier.padding(top = 28.dp),
        )
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            if (error != null) Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        WhfinNumericKeypad(
            deleteContentDescription = stringResource(R.string.app_lock_delete_digit),
            onDigit = onDigit,
            onBackspace = onBackspace,
            leadingIcon = Icons.Default.Fingerprint.takeIf { showBiometric },
            leadingContentDescription = stringResource(R.string.app_lock_use_biometrics).takeIf { showBiometric },
            onLeadingAction = onBiometric.takeIf { showBiometric },
        )
    }
}

internal fun AppLockTimeout.labelResource(): Int = when (this) {
    AppLockTimeout.Disabled -> R.string.app_lock_off
    AppLockTimeout.Immediate -> R.string.app_lock_immediate
    AppLockTimeout.ThirtySeconds -> R.string.app_lock_30_seconds
    AppLockTimeout.OneMinute -> R.string.app_lock_1_minute
    AppLockTimeout.FiveMinutes -> R.string.app_lock_5_minutes
}

private fun AppLockTimeout.supportingResource(): Int? = when (this) {
    AppLockTimeout.Disabled -> R.string.app_lock_off_body
    AppLockTimeout.Immediate -> R.string.app_lock_immediate_body
    else -> null
}

private fun BiometricAvailability.supportingResource(): Int = when (this) {
    BiometricAvailability.Available -> R.string.app_lock_biometric_available
    BiometricAvailability.EnrollmentRequired -> R.string.app_lock_biometric_not_enrolled
    BiometricAvailability.TemporarilyUnavailable -> R.string.app_lock_biometric_temporarily_unavailable
    BiometricAvailability.Unsupported -> R.string.app_lock_biometric_unsupported
}

private fun dev.whekin.whfin.data.security.AppLockProblem.messageResource(): Int = when (this) {
    dev.whekin.whfin.data.security.AppLockProblem.Cancelled -> R.string.app_lock_cancelled
    dev.whekin.whfin.data.security.AppLockProblem.LockedOut -> R.string.app_lock_locked_out
    dev.whekin.whfin.data.security.AppLockProblem.Unavailable -> R.string.app_lock_prompt_unavailable
}

@Preview(name = "App lock settings light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "App lock settings dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "App lock settings font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    WhfinTheme {
        AppLockScreen(
            timeout = AppLockTimeout.OneMinute,
            hasPin = true,
            biometricAvailability = BiometricAvailability.Available,
            biometricEnabled = true,
            onTimeoutChange = {},
            onPinCreated = { _, _ -> },
            onBiometricEnabledChange = {},
            onOpenBiometricSettings = {},
        )
    }
}

@Preview(name = "Locked awaiting biometrics", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(
    name = "Locked awaiting biometrics dark",
    widthDp = 400,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Locked awaiting biometrics font 1.5",
    widthDp = 400,
    heightDp = 900,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun AppLockGateWaitingPreview() {
    WhfinTheme {
        AppLockGate(
            biometricAvailable = true,
            problem = null,
            onVerifyPin = { PinVerificationResult.Invalid(4) },
            onBiometric = {},
        )
    }
}

@Preview(name = "Sensitive action gate", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(
    name = "Sensitive action gate dark",
    widthDp = 400,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Sensitive action gate font 1.5",
    widthDp = 400,
    heightDp = 900,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun SensitiveActionGatePreview() {
    WhfinTheme {
        SensitiveActionGate(
            action = SensitiveAction.BackupExport,
            biometricAvailable = true,
            problem = null,
            onVerifyPin = { PinVerificationResult.Invalid(4) },
            onBiometric = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Locked", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun AppLockGatePreview() {
    WhfinTheme {
        AppLockGate(
            biometricAvailable = false,
            problem = null,
            onVerifyPin = { PinVerificationResult.Invalid(4) },
            onBiometric = {},
        )
    }
}
