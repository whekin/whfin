package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.SavingsMode
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditAccountSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bankAccountEditsPurposeWithoutPretendingCurrencyIsContainerMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var savedMode: SavingsMode? = null
        compose.setContent {
            WhfinTheme {
                EditAccountSheet(
                    account = AccountEntity(
                        id = 1,
                        name = "Hot deposit",
                        type = AccountType.BANK,
                        groupId = 1,
                        currency = "GEL",
                        iban = "GE00CD1",
                        savingsMode = SavingsMode.FLEXIBLE_RESERVE,
                    ),
                    onDismiss = {},
                    onConfirm = { _, _, _, mode -> savedMode = mode },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_purpose)).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_currency)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.account_purpose_deposit)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        assertEquals(SavingsMode.TERM_DEPOSIT, savedMode)
    }
}
