package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountActivityContentTest {
    @get:Rule val compose = createComposeRule()
    @Test fun importedAccountNumberIsNamedOnce() {
        val account = AccountEntity(id = 1, name = "TBC GEL •0001", type = AccountType.BANK,
            currency = "GEL", iban = "GE00TB0000000000000001")
        compose.setContent { WhfinTheme {
            AccountTransactionsContent(account, 12300, emptyList(), AccountWithBalance(account, 12300, emptyList(), groupName = "TBC"),
                {}, AccountActivityCallbacks({}, {}, {}, {}), empty = true)
        } }
        compose.onAllNodes(hasText("0001", substring = true)).assertCountEquals(1)
    }    @Test fun correctionAcceptsZeroButNeverSavesWithoutExplicitAction() {
        var saved: Long? = null
        compose.setContent { WhfinTheme { UserOpeningCorrectionSheet("GEL", 12300,
            onDismiss = {}, onConfirm = { saved = it }) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        org.junit.Assert.assertNull(saved)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(dev.whekin.whfin.R.string.action_save)).performClick()
        org.junit.Assert.assertEquals(0L, saved)
    }

}
