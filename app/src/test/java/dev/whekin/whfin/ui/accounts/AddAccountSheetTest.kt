package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddAccountSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cashNameIsOptionalAndDefaultsToCash() {
        var saved: Triple<String, AccountType, String>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AddAccountSheet(
                    onDismiss = {},
                    onImportStatement = {},
                    onConfirm = { name, type, currency, _, _ -> saved = Triple(name, type, currency) },
                    initialType = AccountType.CASH,
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.account_name)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsEnabled().performClick()
        assertEquals(Triple("Cash", AccountType.CASH, "GEL"), saved)
    }
}
