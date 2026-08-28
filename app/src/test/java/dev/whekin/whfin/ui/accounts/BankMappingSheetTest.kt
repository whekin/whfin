package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BankMappingSheetTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * One IBAN is one account across every currency under it, so the fund role is answered here
     * next to the IBAN it applies to — not in a per-currency form the owner has to go looking for.
     */
    @Test
    fun accountSheetSavesNameAndFundRole() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var savedName: String? = null
        var savedRole: FundRole? = null
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
                        fundRole = FundRole.AVAILABLE,
                    ),
                    existingCards = emptyList(),
                    existingVirtualCards = emptyList(),
                    onDismiss = {},
                    onConfirm = { name, role, _, _, _, _, _ ->
                        savedName = name
                        savedRole = role
                    },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_fund_role)).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_purpose_reserve))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        assertEquals("Everyday", savedName)
        assertEquals(FundRole.RESERVE, savedRole)
    }

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
                    onConfirm = { _, _, _, _, _, _, _ -> },
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
                    onConfirm = { _, _, _, _, savedPhysical, savedVirtual, _ ->
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
                    onConfirm = { _, _, _, _, _, _, savedPrimary -> primary = savedPrimary },
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
                    onConfirm = { _, _, iban, product, _, _, _ ->
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

    @Test
    fun aCardIsAddedByItsFourDigitsRatherThanByEditingAList() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var physical = emptyList<String>()
        compose.setContent {
            WhfinTheme {
                BankMappingSheet(
                    account = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL"),
                    existingCards = listOf("0001"),
                    existingVirtualCards = emptyList(),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, savedPhysical, _, _ -> physical = savedPhysical },
                )
            }
        }

        // Three digits are not a card yet, so there is nothing to add.
        val cardField = context.getString(R.string.account_card_last4)
        compose.onNodeWithContentDescription(cardField).performScrollTo().performTextInput("000")
        compose.onNodeWithTag("card-add").assertIsNotEnabled()
        compose.onNodeWithContentDescription(cardField).performTextInput("2")
        compose.onNodeWithTag("card-add").performClick()

        compose.onNodeWithText(context.getString(R.string.account_card_label, "0002")).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals(listOf("0001", "0002"), physical) }
    }

    @Test
    fun removingACardTakesItsPrimaryMarkWithIt() {
        var physical = listOf("keep")
        var primary: String? = "unset"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BankMappingSheet(
                    account = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL"),
                    existingCards = listOf("0001", "0002"),
                    existingVirtualCards = emptyList(),
                    existingPrimaryCard = "0002",
                    onDismiss = {},
                    onConfirm = { _, _, _, _, savedPhysical, _, savedPrimary ->
                        physical = savedPhysical
                        primary = savedPrimary
                    },
                )
            }
        }

        compose.onNodeWithTag("card-0002-remove").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.account_card_label, "0002")).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle {
            assertEquals(listOf("0001"), physical)
            // A primary card that no longer exists must not survive as a dangling mask.
            assertEquals(null, primary)
        }
    }
}
