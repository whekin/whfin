package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class SetupSmsConsentTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun oneActionEnablesSmsForAllSupportedBanks() {
        var calls = 0
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Sms, emptyList(), {}, {},
                footerAction = SetupAction(context.getString(R.string.setup_sms_all_enable)) { calls++ },
                content = { SetupSmsConsent(false, false) })
        } }
        compose.onNodeWithText(context.getString(R.string.setup_sms_all_enable))
            .assertIsDisplayed().performClick()
        assertEquals(1, calls)
    }

    @Test fun selectedBanksStillAskForAndroidPermission() {
        compose.setContent { WhfinTheme { SetupSmsConsent(true, false) } }
        compose.onNodeWithText(context.getString(R.string.setup_sms_all_permission)).assertIsDisplayed()
    }

    @Test fun fullyEnabledConsentDoesNotAskAgain() {
        compose.setContent { WhfinTheme { SetupSmsConsent(true, true) } }
        compose.onNodeWithText(context.getString(R.string.setup_sms_all_on)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_sms_all_enable)).assertDoesNotExist()
    }
}
