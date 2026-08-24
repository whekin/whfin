package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
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
                    onConfirm = { _, _, _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_card_label, "0001")).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_card_label, "0002")).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.account_card_physical)).assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.account_card_virtual)).assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.account_card_primary)).assertCountEquals(2)
    }

    @Test
    fun physicalCardCanBeChangedToVirtualAndSaved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var physical = emptyList<String>()
        var virtual = emptyList<String>()
        compose.setContent {
            WhfinTheme {
                BankMappingSheet(
                    account = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL"),
                    existingCards = listOf("0001"),
                    existingVirtualCards = emptyList(),
                    onDismiss = {},
                    onConfirm = { _, _, savedPhysical, savedVirtual, _ ->
                        physical = savedPhysical
                        virtual = savedVirtual
                    },
                )
            }
        }

        compose.onNodeWithTag("card-0001-virtual").performScrollTo().performClick()
        compose.onNodeWithTag("card-0001-virtual").assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), physical)
            assertEquals(listOf("0001"), virtual)
        }
    }

    @Test
    fun selectingAnotherPrimaryCardReplacesThePreviousOne() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var primary: String? = null
        compose.setContent {
            WhfinTheme {
                BankMappingSheet(
                    account = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL"),
                    existingCards = listOf("0001", "0002"),
                    existingVirtualCards = emptyList(),
                    existingPrimaryCard = "0001",
                    onDismiss = {},
                    onConfirm = { _, _, _, _, savedPrimary -> primary = savedPrimary },
                )
            }
        }

        compose.onNodeWithTag("card-0002-primary").performScrollTo().performClick()
        compose.onNodeWithTag("card-0002-primary").assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals("0002", primary) }
    }

    @Test
    fun bankProductCanBeSetFromBankDetailsAndSavedWithCardMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var savedProduct: BankProduct? = null
        var savedIban: String? = null

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
                    existingVirtualCards = emptyList(),
                    onDismiss = {},
                    onConfirm = { iban, product, _, _, _ ->
                        savedIban = iban
                        savedProduct = product
                    },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_bank_product)).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_product_term_deposit))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()

        compose.runOnIdle {
            assertEquals("GE00CD0000000000000001", savedIban)
            assertEquals(BankProduct.TERM_DEPOSIT, savedProduct)
        }
    }
}
