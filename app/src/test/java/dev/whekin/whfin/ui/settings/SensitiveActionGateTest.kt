package dev.whekin.whfin.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.LocalSensitiveActions
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.data.security.SensitiveAction
import dev.whekin.whfin.data.security.SensitiveActionController
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SensitiveActionGateTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun gate_namesTheActionItIsProtecting() {
        compose.setContent {
            WhfinTheme {
                SensitiveActionGate(
                    action = SensitiveAction.BackupExport,
                    biometricAvailable = false,
                    problem = null,
                    onVerifyPin = { PinVerificationResult.Success },
                    onBiometric = {},
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sensitive_gate_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensitive_gate_backup_export)).assertIsDisplayed()
    }

    @Test
    fun cancel_leavesTheActionUndone() {
        var cancelled = 0
        var verified = 0
        compose.setContent {
            WhfinTheme {
                SensitiveActionGate(
                    action = SensitiveAction.BackupRestore,
                    biometricAvailable = false,
                    problem = null,
                    onVerifyPin = {
                        verified++
                        PinVerificationResult.Success
                    },
                    onBiometric = {},
                    onCancel = { cancelled++ },
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.action_cancel)).performClick()

        assertEquals(1, cancelled)
        assertEquals(0, verified)
    }

    @Test
    fun wrongCode_saysHowManyAttemptsAreLeftAndClears() {
        compose.setContent {
            WhfinTheme {
                SensitiveActionGate(
                    action = SensitiveAction.BankCredential,
                    biometricAvailable = false,
                    problem = null,
                    onVerifyPin = { PinVerificationResult.Invalid(4) },
                    onBiometric = {},
                    onCancel = {},
                )
            }
        }

        "1234".forEach { compose.onNodeWithText(it.toString()).performClick() }

        compose.onNodeWithText(context.getString(R.string.app_lock_wrong_code, 4)).assertIsDisplayed()
    }

    /**
     * The change is what the gate protects, so it must not reach the caller until the gate says so —
     * asking after the fact would only describe a change that already happened.
     */
    @Test
    fun protectedTimeoutChange_doesNotReachTheCallerBeforeTheAnswer() {
        var applied: AppLockTimeout? = null
        val controller = SensitiveActionController(hasPin = { true }, elapsedRealtime = { 0L })
        compose.setContent {
            WhfinTheme {
                CompositionLocalProvider(LocalSensitiveActions provides controller) {
                    AppLockScreen(
                        timeout = AppLockTimeout.Disabled,
                        hasPin = true,
                        biometricAvailability = BiometricAvailability.Unsupported,
                        biometricEnabled = false,
                        onTimeoutChange = { applied = it },
                        onPinCreated = { _, _ -> },
                        onBiometricEnabledChange = {},
                        onOpenBiometricSettings = {},
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_1_minute)).performScrollTo().performClick()

        assertNull(applied)
        assertEquals(SensitiveAction.AppLockSettings, controller.pending)

        controller.allow()
        assertEquals(AppLockTimeout.OneMinute, applied)
    }

    /** With no code yet, the first setup cannot be gated by the code it is about to create. */
    @Test
    fun firstSetup_isNotGatedByACodeThatDoesNotExist() {
        var savedPin: String? = null
        val controller = SensitiveActionController(hasPin = { false }, elapsedRealtime = { 0L })
        compose.setContent {
            WhfinTheme {
                CompositionLocalProvider(LocalSensitiveActions provides controller) {
                    AppLockScreen(
                        timeout = AppLockTimeout.Disabled,
                        hasPin = false,
                        biometricAvailability = BiometricAvailability.Unsupported,
                        biometricEnabled = false,
                        onTimeoutChange = {},
                        onPinCreated = { pin, _ -> savedPin = pin },
                        onBiometricEnabledChange = {},
                        onOpenBiometricSettings = {},
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_immediate)).performScrollTo().performClick()
        repeat(2) { "1357".forEach { compose.onNodeWithText(it.toString()).performClick() } }

        assertEquals("1357", savedPin)
        assertNull(controller.pending)
    }
}
