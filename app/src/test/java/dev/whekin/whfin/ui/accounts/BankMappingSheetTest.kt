package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankMappingSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mixedCardsExposeIndependentTypeAndPrimaryControls() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BankMappingSheet(
                    account = AccountEntity(
                        id = 1,
                        name = "Everyday",
                        type = AccountType.BANK,
                        groupId = 1,
                        currency = "GEL",
                        iban = "GE00CD0000000000000001",
                    ),
                    existingCards = listOf("0001"),
                    existingVirtualCards = listOf("0002"),
                    existingPrimaryCard = "0001",
                    onDismiss = {},
                    onConfirm = { _, _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_card_label, "0001")).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_card_label, "0002")).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.account_card_physical)).assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.account_card_virtual)).assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.account_card_primary)).assertCountEquals(2)
    }
}
