package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.AppLockPinStore
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
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
) {
    var setupTarget by remember { mutableStateOf<AppLockTimeout?>(null) }
    var changingCode by remember { mutableStateOf(false) }
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
                        if (option.enabled && !hasPin) setupTarget = option else onTimeoutChange(option)
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
                    onClick = { changingCode = true },
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
                            onBiometricEnabledChange(!biometricEnabled)
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
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var pinProblem by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                            pinProblem = context.getString(
                                R.string.app_lock_wrong_code,
                                result.attemptsRemaining,
                            )
                        }
                        is PinVerificationResult.Locked -> {
                            pin = ""
                            pinProblem = context.getString(
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
    val haptics = LocalHapticFeedback.current
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(top = 28.dp),
        ) {
            repeat(AppLockPinStore.PIN_LENGTH) { index ->
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = CircleShape,
                    color = if (index < pin.length) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (index < pin.length) null else BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline,
                    ),
                    content = {},
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            if (error != null) Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { digit ->
                        PinKey(digit.toString()) {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            onDigit(digit)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showBiometric) IconKey(
                    icon = Icons.Default.Fingerprint,
                    description = stringResource(R.string.app_lock_use_biometrics),
                    onClick = onBiometric,
                ) else Spacer(Modifier.size(68.dp))
                PinKey("0") {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onDigit('0')
                }
                IconKey(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    description = stringResource(R.string.app_lock_delete_digit),
                    onClick = onBackspace,
                )
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(68.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun IconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(68.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

@Preview(name = "Locked", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun AppLockGatePreview() {
    WhfinTheme {
        AppLockGate(
            biometricAvailable = true,
            problem = null,
            onVerifyPin = { PinVerificationResult.Invalid(4) },
            onBiometric = {},
        )
    }
}
