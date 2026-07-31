package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The sheet scrolls on a phone; give the test enough height to lay out every rail.
@Config(sdk = [35], qualifiers = "w420dp-h1600dp")
class AddAccountSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Saved(
        val name: String,
        val type: AccountType,
        val currency: String,
        val address: String?,
        val network: CryptoNetwork?,
    )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun show(initialType: AccountType, onSave: (Saved) -> Unit) {
        compose.setContent {
            WhfinTheme {
                AddAccountSheet(
                    onDismiss = {},
                    onImportStatement = {},
                    onConfirm = { name, type, currency, address, _, network ->
                        onSave(Saved(name, type, currency, address, network))
                    },
                    initialType = initialType,
                )
            }
        }
    }

    private fun tap(label: String) = compose.onNodeWithText(label).performScrollTo().performClick()

    private fun type(label: Int, text: String) =
        compose.onNodeWithContentDescription(context.getString(label)).performTextInput(text)

    @Test
    fun cashNameIsOptionalAndDefaultsToCash() {
        var saved: Saved? = null
        show(AccountType.CASH) { saved = it }

        compose.onNodeWithText(context.getString(R.string.account_name)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsEnabled().performClick()

        assertEquals("Cash", saved?.name)
        assertEquals(AccountType.CASH, saved?.type)
        assertEquals("GEL", saved?.currency)
        assertNull(saved?.network)
    }

    @Test
    fun theFormOpensOnCashBecauseBankAndWalletArriveOtherWays() {
        var saved: Saved? = null
        compose.setContent {
            WhfinTheme {
                AddAccountSheet(
                    onDismiss = {},
                    onImportStatement = {},
                    onConfirm = { name, type, currency, address, _, network ->
                        saved = Saved(name, type, currency, address, network)
                    },
                )
            }
        }

        // No field touched: whatever the ledger already holds, the default must be cash in GEL.
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsEnabled().performClick()

        assertEquals(AccountType.CASH, saved?.type)
        assertEquals("GEL", saved?.currency)
    }

    @Test
    fun cryptoRequiresAnAddressThatMatchesTheSelectedNetwork() {
        var saved: Saved? = null
        show(AccountType.CRYPTO) { saved = it }

        type(R.string.account_name, "Wallet")
        val save = compose.onNodeWithText(context.getString(R.string.action_save))
        save.assertIsNotEnabled()

        // A valid Tron address is not a valid Ethereum address: the chosen network decides.
        type(R.string.account_address, TRON_ADDRESS)
        save.assertIsNotEnabled()
        compose.onNodeWithText(
            context.getString(R.string.account_address_invalid, CryptoNetwork.ETHEREUM.displayName),
        ).assertExists()

        tap(CryptoNetwork.TRON.displayName)
        save.assertIsEnabled().performClick()

        assertEquals(CryptoNetwork.TRON, saved?.network)
        assertEquals(TRON_ADDRESS, saved?.address)
        assertEquals("TRX", saved?.currency)
    }

    @Test
    fun switchingNetworkKeepsAnAssetThatExistsOnBothChains() {
        var saved: Saved? = null
        show(AccountType.CRYPTO) { saved = it }

        type(R.string.account_name, "Wallet")
        tap("USDT")
        tap(CryptoNetwork.TRON.displayName)
        type(R.string.account_address, TRON_ADDRESS)
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()

        // USDT exists on both chains, so the ticker survives while the network changes.
        assertEquals("USDT", saved?.currency)
        assertEquals(CryptoNetwork.TRON, saved?.network)
    }

    @Test
    fun unimplementedChainsAreNotOffered() {
        show(AccountType.CRYPTO) {}

        compose.onNodeWithText("BTC").assertDoesNotExist()
        compose.onNodeWithText("TON").assertDoesNotExist()
        compose.onNodeWithText("Bitcoin").assertDoesNotExist()
    }

    private companion object {
        /** Synthetic base58check address: the encoding of an all-zero Tron payload. */
        const val TRON_ADDRESS = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
    }
}
