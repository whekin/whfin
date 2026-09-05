package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.IncomeSourceEntity
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithText
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
            expectedDayFrom = 5, expectedDayTo = 10, startedOn = 20500, createdAt = 0)
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

    @Test
    fun editorNamesTheUsualDayAndLatestDeadlineInsteadOfAnEvenWindow() {
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

        compose.onNodeWithText("Payday timing").assertExists()
        compose.onNodeWithText("Usually on").assertExists()
        compose.onNodeWithText("Latest by").assertExists()
    }
}
