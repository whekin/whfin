package dev.whekin.whfin.ui.settings

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.sms.BankSmsBank
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h850dp")
class ConnectionDestinationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun commonMessagesEntryNamesItsDestination() = entry("connections", null)
    @Test fun bankMessagesEntryKeepsItsBankFilter() = entry("bank:CREDO", BankSmsBank.CREDO)
    @Test fun rootCanEnableSmsForAllBanksWithOneAction() {
        var selected: Boolean? = null
        compose.setContent { WhfinTheme {
            val navigation = rememberSettingsSearchState().apply { page = "connections" }
            ConnectionsSettings(ConnectionSettingsState(accounts = emptyMap(), sms = mapOf(
                BankSmsBank.CREDO to true, BankSmsBank.TBC to false)), navigation,
                ConnectionSettingsActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                    smsAll = { selected = it }), false)
        } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_all_bank_sms_action))
            .performScrollTo().performClick()
        assertEquals(true, selected)
    }
    private fun entry(page: String, expected: BankSmsBank?) {
        var called = false
        var bank: BankSmsBank? = null
        compose.setContent { WhfinTheme {
            val navigation = rememberSettingsSearchState().apply { this.page = page }
            ConnectionsSettings(ConnectionSettingsState(accounts = emptyMap(), remembered = setOf(BankSmsBank.CREDO)), navigation,
                ConnectionSettingsActions({}, {}, { _, _ -> }, {}, {}, {}, { called = true; bank = it }, {}, {}, {}), false)
        } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithText(context.getString(R.string.sms_diagnostics_title)).performScrollTo().performClick()
        assertTrue(called)
        assertEquals(expected, bank)
        compose.onNodeWithText("Cards and accounts").assertDoesNotExist()
    }
}
