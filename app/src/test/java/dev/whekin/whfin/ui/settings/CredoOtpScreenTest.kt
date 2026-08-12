package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoOtpScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun otpUsesLocalKeypadAndClearsCodeWhenResent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var submitted: String? = null
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp),
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = { submitted = it },
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        listOf("1", "2", "3", "4").forEach { digit ->
            compose.onNodeWithText(digit).performClick()
        }
        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 4, 4),
        ).assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_confirm))
            .assertIsEnabled()
            .performClick()
        assertEquals("1234", submitted)

        compose.onNodeWithText(context.getString(R.string.credo_sync_resend_otp)).performClick()
        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 0, 4),
        ).assertExists()
    }

    @Test
    fun incomingLoginOtpFillsTheLocalCodeAndConfirmsIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var submitted: String? = null
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp),
                    appLockEnabled = true,
                    incomingOtp = "4821",
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = { submitted = it },
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 4, 4),
        ).assertExists()
        compose.onNodeWithText(
            context.getString(R.string.credo_sync_otp_autofill_hint),
            substring = true,
        ).assertExists()
        // A code that arrived on its own was already agreed to — by the consent dialog, or by the
        // message reaching this phone at all. Asking for Confirm on four digits the user did not
        // type is asking them to agree to their own agreement.
        assertEquals("4821", submitted)
    }

    @Test
    fun rejectedOtpClearsAllDigitsForTheNextAttempt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var state by mutableStateOf(CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp))
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = state,
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }
        listOf("1", "2", "3", "4").forEach { compose.onNodeWithText(it).performClick() }

        compose.runOnIdle { state = state.copy(errorCode = "INVALID_OTP") }

        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 0, 4),
        ).assertExists()
    }

    @Test
    fun otpNotSentKeepsTheChallengeAndOffersResend() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(
                        stage = CredoSyncStage.AwaitingOtp,
                        errorCode = "OTP_NOT_SENT",
                    ),
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.credo_sync_error_otp_not_sent)).assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_resend_otp)).assertExists()
    }

    @Test
    fun otpKeypadAndConfirmationNeverLiveInAScrollContainer() {
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(stage = CredoSyncStage.AwaitingOtp),
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    @Test
    fun appLockDetourKeepsTheLoginDraftInMemory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val draft = CredoLoginDraft()
        var showCredo by mutableStateOf(true)
        var connectedWith: Triple<String, String, Boolean>? = null

        compose.setContent {
            WhfinTheme {
                if (showCredo) {
                    CredoSyncScreen(
                        state = CredoSyncUiState(),
                        appLockEnabled = false,
                        loginDraft = draft,
                        onOpenAppLock = { showCredo = false },
                        onConnect = { username, credential, remember ->
                            connectedWith = Triple(username, credential, remember)
                        },
                        onSubmitOtp = {},
                        onResendOtp = {},
                        onSync = {},
                        onLoadHistory = {},
                        onDisconnect = {},
                        onDismissError = {},
                    )
                } else {
                    androidx.compose.material3.Text("App Lock")
                }
            }
        }

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("owner")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("secret")
        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_protect_action))
            .performScrollTo().performClick()
        compose.runOnIdle { showCredo = true }
        compose.onNodeWithText(context.getString(R.string.credo_sync_connect))
            .performScrollTo().assertIsEnabled().performClick()

        assertEquals(Triple("owner", "secret", false), connectedWith)
    }

    @Test
    fun loginExplainsSecurity_andRememberingIsOptInBehindAppLock() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var openedAppLock = false
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(),
                    appLockEnabled = false,
                    onOpenAppLock = { openedAppLock = true },
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.credo_sync_experimental_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_experimental_body)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_protect_action))
            .performScrollTo()
            .performClick()
        assertEquals(true, openedAppLock)
    }

    @Test
    fun rememberingLoginDefaultsOffWhenAppLockIsAvailable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(),
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_remember_password))
            .performScrollTo()
            .assertIsOff()
    }

    @Test
    fun savedProfileIsACompactSyncActionWithoutCredentialFields() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = CredoSyncUiState(
                        stage = CredoSyncStage.Disconnected,
                        savedUsername = "saved-user",
                        hasSavedPassword = true,
                    ),
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.credo_sync_now)).assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_experimental_body)).assertDoesNotExist()
    }
}
