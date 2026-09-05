package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.WeekendRule
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class IncomeSourceTimingLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun anAlreadyDeclaredSourceCanBeBoundToTheWalletWithoutLosingItsStart() {
        val source = IncomeSourceEntity(id = 1, label = "Pay", amountMinor = 180000, currency = "USD",
            expectedDayFrom = 5, expectedDayTo = 5, startedOn = 20500, createdAt = 0)
        var selected: Long? = null
        var savedCurrency = ""
        var savedStart = 0L
        compose.setContent {
            WhfinTheme {
                IncomeSourceSheet(source,
                    listOf(AccountEntity(id = 12, name = "Wallet", type = AccountType.CRYPTO, currency = "USDT")),
                    onDismiss = {}, onEnd = null, onDelete = null,
                    onSave = { _, _, currency, accountId, _, _, startedOn ->
                        selected = accountId; savedCurrency = currency; savedStart = startedOn
                    },
                )
            }
        }
        compose.onNodeWithText("Not added yet").performScrollTo().performClick()
        compose.onNodeWithText("Wallet · USDT").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(12L, selected)
        assertEquals("USDT", savedCurrency)
        assertEquals(source.startedOn, savedStart)
    }

    /**
     * One date and one weekend habit, not a range. A mandatory "latest by" would let the app call a
     * real payment late on a day the owner never agreed to.
     */
    @Test
    fun editorAsksForOneDateAndAWeekendHabit() {
        compose.setContent {
            WhfinTheme {
                IncomeSourceSheet(
                    source = null,
                    accounts = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _ -> },
                    onEnd = null,
                    onDelete = null,
                )
            }
        }

        compose.onNodeWithText("Payday").assertExists()
        compose.onNodeWithText("Usually on").assertExists()
        compose.onNodeWithText("If the date falls on a weekend").performScrollTo().assertExists()
        compose.onNodeWithText("Weekdays only · usually earlier").performScrollTo().assertExists()
        compose.onAllNodesWithText("Latest by").assertCountEquals(0)
    }

    /** The chosen habit is what gets saved; nothing else about timing is asked or stored. */
    @Test
    fun theChosenWeekendHabitIsSaved() {
        var saved: WeekendRule? = null
        compose.setContent {
            WhfinTheme {
                IncomeSourceSheet(
                    source = IncomeSourceEntity(id = 1, label = "Pay", amountMinor = 180000,
                        currency = "USD", accountId = 1, expectedDayFrom = 5, expectedDayTo = 5,
                        startedOn = 20500, createdAt = 0),
                    accounts = listOf(AccountEntity(id = 1, name = "Wallet",
                        type = AccountType.CRYPTO, currency = "USDT")),
                    onDismiss = {}, onEnd = null, onDelete = null,
                    onSave = { _, _, _, _, _, rule, _ -> saved = rule },
                )
            }
        }

        compose.onNodeWithText("Weekdays only · usually later").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(WeekendRule.LATER, saved)
    }
}
