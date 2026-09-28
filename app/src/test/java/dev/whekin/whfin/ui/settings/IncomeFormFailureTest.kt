package dev.whekin.whfin.ui.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.FormSaveState
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class IncomeFormFailureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun walletHistoryExplainsMissingTronWalletAndOpensAccounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var opened = false
        var read = false
        compose.setContent { WhfinTheme {
            IncomeSourcesScreen(IncomeSourcesState(emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 9),
                walletCount = 1), onSave = { _, _, _, _, _, _, _, _ -> }, onEnd = {}, onDelete = {},
                onOpenAccounts = { opened = true }, onRefresh = { read = true })
        } }
        compose.onNodeWithText(context.getString(R.string.income_sources_add_tron_wallet)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, opened); assertFalse(read) }
    }

    @Test fun connectedTronWalletStartsHistoryOnlyOnTap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var reads = 0
        compose.setContent { WhfinTheme {
            IncomeSourcesScreen(IncomeSourcesState(emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 9),
                tronWalletCount = 1, walletCount = 1), onSave = { _, _, _, _, _, _, _, _ -> },
                onEnd = {}, onDelete = {}, onRefresh = { reads++ })
        } }
        compose.runOnIdle { assertEquals(0, reads) }
        compose.onNodeWithText(context.getString(R.string.income_sources_recheck)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, reads) }
    }
    @Test fun failedWritePreservesTypedValuesUntilSuccessfulRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val form = mutableStateOf(FormSaveState())
        var attempts = 0
        compose.setContent { WhfinTheme {
            IncomeSourcesScreen(IncomeSourcesState(emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 9)),
                onSave = { _, label, amount, _, _, _, _, _ ->
                    assertEquals("My salary", label); assertEquals(12345L, amount)
                    attempts++
                    form.value = if (attempts == 1) FormSaveState(failed = true) else FormSaveState(completed = 1)
                }, onEnd = {}, onDelete = {}, formState = form.value)
        } }
        compose.onNodeWithText(context.getString(R.string.income_sources_add)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("My salary")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("123.45")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.onNodeWithText(context.getString(R.string.form_save_failed)).assertExists()
        compose.onNodeWithText("My salary").assertExists()
        compose.onNodeWithText("123.45").assertExists()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).assertDoesNotExist()
        assertEquals(2, attempts)
    }
}
