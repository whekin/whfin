package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.AppLockProblem
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLockScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun existingCode_allowsSelectingTimeout() {
        var selected: AppLockTimeout? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockScreen(
                    timeout = AppLockTimeout.Disabled,
                    hasPin = true,
                    biometricAvailability = BiometricAvailability.Available,
                    biometricEnabled = true,
                    onTimeoutChange = { selected = it },
                    onPinCreated = { _, _ -> },
                    onBiometricEnabledChange = {},
                    onOpenBiometricSettings = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_1_minute))
            .performScrollTo()
            .performClick()
        assertEquals(AppLockTimeout.OneMinute, selected)
    }

    @Test
    fun noCode_setsACodeWithoutTurningOnTheLockScreen() {
        var savedPin: String? = null
        var savedTimeout: AppLockTimeout? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockScreen(
                    timeout = AppLockTimeout.Disabled,
                    hasPin = false,
                    biometricAvailability = BiometricAvailability.Unsupported,
                    biometricEnabled = false,
                    onTimeoutChange = {},
                    onPinCreated = { pin, timeout ->
                        savedPin = pin
                        savedTimeout = timeout
                    },
                    onBiometricEnabledChange = {},
                    onOpenBiometricSettings = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_set_code))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.app_lock_create_code_title)).assertIsDisplayed()
        repeat(2) { "1234".forEach { compose.onNodeWithText(it.toString()).performClick() } }

        assertEquals("1234", savedPin)
        assertEquals(AppLockTimeout.Disabled, savedTimeout)
    }

    @Test
    fun existingCode_offersAChangeInsteadOfASecondSetup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockScreen(
                    timeout = AppLockTimeout.Disabled,
                    hasPin = true,
                    biometricAvailability = BiometricAvailability.Available,
                    biometricEnabled = false,
                    onTimeoutChange = {},
                    onPinCreated = { _, _ -> },
                    onBiometricEnabledChange = {},
                    onOpenBiometricSettings = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_set_code)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.app_lock_change_code)).assertIsDisplayed()
        // "Off" now means the ledger opens, not that nothing is protected.
        compose.onNodeWithText(context.getString(R.string.app_lock_off_body_code))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun firstEnable_requiresMatchingWhfinCode() {
        var savedPin: String? = null
        var savedTimeout: AppLockTimeout? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockScreen(
                    timeout = AppLockTimeout.Disabled,
                    hasPin = false,
                    biometricAvailability = BiometricAvailability.Unsupported,
                    biometricEnabled = false,
                    onTimeoutChange = {},
                    onPinCreated = { pin, timeout ->
                        savedPin = pin
                        savedTimeout = timeout
                    },
                    onBiometricEnabledChange = {},
                    onOpenBiometricSettings = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_immediate))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.app_lock_create_code_title)).assertIsDisplayed()
        repeat(2) { "1234".forEach { compose.onNodeWithText(it.toString()).performClick() } }

        assertEquals("1234", savedPin)
        assertEquals(AppLockTimeout.Immediate, savedTimeout)
    }

    @Test
    fun setupDetour_canOpenCodeEntryWithAnActiveImmediateDefault() {
        var savedTimeout: AppLockTimeout? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockScreen(
                    timeout = AppLockTimeout.Immediate,
                    hasPin = false,
                    biometricAvailability = BiometricAvailability.Unsupported,
                    biometricEnabled = false,
                    onTimeoutChange = {},
                    onPinCreated = { _, timeout -> savedTimeout = timeout },
                    onBiometricEnabledChange = {},
                    onOpenBiometricSettings = {},
                    autoSetupTimeout = AppLockTimeout.Immediate,
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_create_code_title)).assertIsDisplayed()
        repeat(2) { "2468".forEach { compose.onNodeWithText(it.toString()).performClick() } }
        assertEquals(AppLockTimeout.Immediate, savedTimeout)
    }

    @Test
    fun lockedGate_withBiometrics_hidesKeypadUntilCodeRequested() {
        var verified: String? = null
        var biometricRequested = 0
        var codeRequested = 0
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockGate(
                    biometricAvailable = true,
                    problem = null,
                    onVerifyPin = {
                        verified = it
                        PinVerificationResult.Success
                    },
                    onBiometric = { biometricRequested++ },
                    onUseCode = { codeRequested++ },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_gate_title)).assertIsDisplayed()
        compose.onNodeWithText("1").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.app_lock_use_biometrics)).performClick()
        compose.runOnIdle { assertEquals(1, biometricRequested) }

        compose.onNodeWithText(context.getString(R.string.app_lock_use_code)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.app_lock_use_biometrics)).fetchSemanticsNode()
        "1234".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.runOnIdle {
            assertEquals("1234", verified)
            assertEquals(1, codeRequested)
        }
    }

    @Test
    fun lockedGate_withoutBiometrics_showsKeypadImmediately() {
        var verified: String? = null
        compose.setContent {
            WhfinTheme {
                AppLockGate(
                    biometricAvailable = false,
                    problem = null,
                    onVerifyPin = {
                        verified = it
                        PinVerificationResult.Success
                    },
                    onBiometric = {},
                )
            }
        }

        "1234".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.runOnIdle { assertEquals("1234", verified) }
    }

    @Test
    fun lockedGate_promptProblem_revealsKeypad() {
        var codeRequested = 0
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AppLockGate(
                    biometricAvailable = true,
                    problem = AppLockProblem.Cancelled,
                    onVerifyPin = { PinVerificationResult.Success },
                    onBiometric = {},
                    onUseCode = { codeRequested++ },
                )
            }
        }

        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.app_lock_cancelled)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, codeRequested) }
    }
}
