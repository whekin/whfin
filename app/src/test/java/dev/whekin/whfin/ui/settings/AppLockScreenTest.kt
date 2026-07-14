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
    fun lockedGate_acceptsCodeAndOffersBiometrics() {
        var verified: String? = null
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
                    onBiometric = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_gate_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.app_lock_use_biometrics)).fetchSemanticsNode()
        "1234".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.runOnIdle {
            assertEquals("1234", verified)
        }
    }
}
