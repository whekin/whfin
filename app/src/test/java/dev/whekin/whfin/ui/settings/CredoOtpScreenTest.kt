package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                    appLockHasPin = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = { submitted = it },
                    onResendOtp = {},
                    onSync = {},
                    onDisconnect = {},
                    onDismissError = {},
                )
            }
        }

        listOf("1", "2", "3", "4").forEach { digit ->
            compose.onNodeWithText(digit).performScrollTo().performClick()
        }
        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 4, 4),
        ).assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_confirm))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals("1234", submitted)

        compose.onNodeWithText(context.getString(R.string.credo_sync_resend_otp)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(
            context.getString(R.string.credo_sync_otp_progress, 0, 4),
        ).assertExists()
    }
}
