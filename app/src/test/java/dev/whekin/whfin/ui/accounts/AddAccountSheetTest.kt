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
    )

    private data class SavedWallet(
        val name: String?,
        val network: CryptoNetwork,
        val address: String,
    )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun show(
        initialType: AccountType,
        onSave: (Saved) -> Unit = {},
        onSaveWallet: (SavedWallet) -> Unit = {},
    ) {
        compose.setContent {
            WhfinTheme {
                AddAccountSheet(
                    onDismiss = {},
                    onImportStatement = {},
                    onConfirm = { name, type, currency, _ -> onSave(Saved(name, type, currency)) },
                    onConfirmWallet = { name, network, address ->
                        onSaveWallet(SavedWallet(name, network, address))
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
        show(AccountType.CASH, onSave = { saved = it })

        compose.onNodeWithText(context.getString(R.string.account_name)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsEnabled().performClick()

        assertEquals("Cash", saved?.name)
        assertEquals(AccountType.CASH, saved?.type)
        assertEquals("GEL", saved?.currency)
    }

    @Test
    fun theFormOpensOnCashBecauseBankAndWalletArriveOtherWays() {
        var saved: Saved? = null
        compose.setContent {
            WhfinTheme {
                AddAccountSheet(
                    onDismiss = {},
                    onImportStatement = {},
                    onConfirm = { name, type, currency, _ -> saved = Saved(name, type, currency) },
                )
            }
        }

        // No field touched: whatever the ledger already holds, the default must be cash in GEL.
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsEnabled().performClick()

        assertEquals(AccountType.CASH, saved?.type)
        assertEquals("GEL", saved?.currency)
    }

    @Test
    fun aWalletIsTrackedByAddressAndNetworkAlone() {
        var wallet: SavedWallet? = null
        show(AccountType.CRYPTO, onSaveWallet = { wallet = it })

        val track = compose.onNodeWithText(context.getString(R.string.crypto_wallet_track))
        // Nothing else is required, but an address on the wrong chain still is not one.
        track.assertIsNotEnabled()
        type(R.string.account_address, TRON_ADDRESS)
        track.assertIsNotEnabled()
        compose.onNodeWithText(
            context.getString(R.string.account_address_invalid, CryptoNetwork.ETHEREUM.displayName),
        ).assertExists()

        tap(CryptoNetwork.TRON.displayName)
        track.assertIsEnabled().performClick()

        assertEquals(CryptoNetwork.TRON, wallet?.network)
        assertEquals(TRON_ADDRESS, wallet?.address)
        // An unnamed wallet is named after its chain and address later, not with an empty string.
        assertNull(wallet?.name)
    }

    @Test
    fun theAssetsAreNotAskedOfThePerson() {
        show(AccountType.CRYPTO)

        // Which tokens sit on an address is a question for the chain: no asset rail, no tickers.
        compose.onNodeWithText(context.getString(R.string.account_asset)).assertDoesNotExist()
        compose.onNodeWithText("USDT").assertDoesNotExist()
        compose.onNodeWithText("TRX").assertDoesNotExist()
    }

    @Test
    fun unimplementedChainsAreNotOffered() {
        show(AccountType.CRYPTO)

        compose.onNodeWithText("BTC").assertDoesNotExist()
        compose.onNodeWithText("TON").assertDoesNotExist()
        compose.onNodeWithText("Bitcoin").assertDoesNotExist()
    }

    private companion object {
        /** Synthetic base58check address: the encoding of an all-zero Tron payload. */
        const val TRON_ADDRESS = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
    }
}
