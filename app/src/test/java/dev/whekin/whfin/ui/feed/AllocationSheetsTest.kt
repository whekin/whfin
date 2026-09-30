package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.ExpenseBeneficiary
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class AllocationSheetsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val people = listOf(PersonEntity(id = 1, name = "Mira", color = 0))
    private val item = FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL",
        occurredAt = 0, status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null, day = LocalDate.of(2026, 9, 1))

    @Test fun explicitPersonAndValidAmountAreRequiredWithoutClamping() {
        var saved: ExpenseBeneficiary? = null
        compose.setContent { WhfinTheme { SplitSheet(item, people, {}, { value, _ -> saved = value }) } }
        val save = compose.onNodeWithText(context.getString(R.string.action_save))
        save.assertIsNotEnabled()
        compose.onNodeWithText("Mira").performClick()
        compose.onNodeWithText(context.getString(R.string.split_custom)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("99")
        save.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.split_amount_limit, "21.13 ₾")).assertExists()
        assertNull(saved)
        compose.onNode(hasSetTextAction()).performTextReplacement("21.13")
        save.performClick()
        assertEquals(2113L, saved?.shareMinor)
        assertEquals(1L, saved?.personId)
    }

    @Test fun newNameRemainsADraftUntilSave() {
        var saved: ExpenseBeneficiary? = null
        compose.setContent { WhfinTheme { SplitSheet(item, people, {}, { value, _ -> saved = value }) } }
        compose.onNodeWithText(context.getString(R.string.debt_new_person)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("New friend")
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        assertEquals("New friend", saved?.newPersonName)
        assertNull(saved?.personId)
        assertEquals(1056L, saved?.shareMinor)
    }

    @Test fun severalSavedParticipantsCannotBeOverwrittenByTheCompactEditor() {
        var writes = 0
        compose.setContent { WhfinTheme { SplitSheet(item.copy(splitOnPeople = listOf("Mira" to 500L, "Kai" to 500L)), people,
            {}, { _, _ -> writes++ }) } }
        compose.onNodeWithText(context.getString(R.string.action_save)).assertDoesNotExist()
        compose.onNodeWithText("Mira").assertExists()
        compose.onNodeWithText("Kai").assertExists()
        assertEquals(0, writes)
    }
}
