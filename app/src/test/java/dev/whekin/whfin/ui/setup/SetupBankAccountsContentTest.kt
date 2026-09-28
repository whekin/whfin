package dev.whekin.whfin.ui.setup

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sms.BankSmsBank
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h850dp")
class SetupBankAccountsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bankRowsExposeProductReserveAndCardFactsAndOpenDirectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val credo = AccountEntity(id = 1, name = "Credo GEL", type = AccountType.BANK,
            currency = "GEL", groupId = 1, iban = "GE00CD0000000000000001",
            bankProduct = BankProduct.DEMAND_DEPOSIT, fundRole = FundRole.RESERVE)
        val tbc = AccountEntity(id = 2, name = "TBC GEL", type = AccountType.BANK,
            currency = "GEL", groupId = 2, iban = "GE00TB0000000000000002")
        val containers = listOf(
            SetupBankContainer("credo", BankSmsBank.CREDO, listOf(credo), listOf(
                PaymentInstrumentEntity(id = 7, groupId = 1, type = PaymentInstrumentType.PHYSICAL_CARD,
                    last4 = "1234", isPrimary = true))),
            SetupBankContainer("tbc", BankSmsBank.TBC, listOf(tbc), emptyList()),
        )
        val overview = SetupOverview(emptyList(), emptyMap(), emptySet(), 0, 0, 0, 0, 0, 0,
            bankContainers = containers)
        var opened: String? = null
        compose.setContent { WhfinTheme {
            SetupBankAccountsContent(SetupOverviewState.Ready(overview), {}, { opened = it })
        } }
        compose.onNodeWithText(context.getString(R.string.account_product_demand_deposit), substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_purpose_reserve), substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.setup_bank_product_missing), substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.setup_bank_account_label, "Credo", "0001"))
            .performClick()
        assertEquals("credo", opened)
    }
}
