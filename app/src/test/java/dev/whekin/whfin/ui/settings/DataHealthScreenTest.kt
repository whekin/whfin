package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.integrity.IntegrityIssue
import dev.whekin.whfin.data.integrity.IntegritySeverity
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataHealthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun healthyLedger_saysSoInsteadOfShowingAnEmptyList() {
        compose.setContent {
            WhfinTheme {
                DataHealthScreen(state = DataHealthViewModel.State.Checked(emptyList()))
            }
        }

        compose.onNodeWithText(context.getString(R.string.data_health_ok_title)).assertIsDisplayed()
    }

    @Test
    fun repeatedTransferIssues_areOneActionInsteadOfAWallOfInternalIds() {
        val issues = (1L..9L).map { id ->
            IntegrityIssue(
                code = "incomplete_transfer_group",
                severity = IntegritySeverity.ERROR,
                entity = "transfer_groups",
                entityId = id,
                message = "Transfer group has a single active leg instead of a pair.",
            )
        }
        var repaired = 0
        compose.setContent {
            WhfinTheme {
                DataHealthScreen(
                    state = DataHealthViewModel.State.Checked(issues),
                    onRepairTransfers = { repaired++ },
                )
            }
        }

        compose.onAllNodesWithText(context.getString(R.string.data_health_transfers_title)).assertCountEquals(1)
        compose.onNodeWithText("transfer_groups · #1 · incomplete_transfer_group").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.data_health_repair_action)).performClick()
        assertTrue(repaired == 1)
    }

    @Test
    fun technicalIds_areAvailableOnlyWhenAskedFor() {
        val issue = IntegrityIssue(
            code = "allocation_total_mismatch",
            severity = IntegritySeverity.ERROR,
            entity = "transactions",
            entityId = 42,
            message = "Allocation total does not equal the parent amount.",
        )
        compose.setContent {
            WhfinTheme { DataHealthScreen(state = DataHealthViewModel.State.Checked(listOf(issue))) }
        }

        val detail = "transactions · #42 · allocation_total_mismatch"
        compose.onNodeWithText(detail).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.data_health_show_details)).performClick()
        compose.onNodeWithText(detail).assertIsDisplayed()
    }

    @Test
    fun checkAgain_isOfferedOnceTheCheckFinished() {
        var checked = 0
        compose.setContent {
            WhfinTheme {
                DataHealthScreen(
                    state = DataHealthViewModel.State.Checked(emptyList()),
                    onCheck = { checked++ },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.data_health_check_action)).performClick()

        assertTrue(checked == 1)
    }

    @Test
    fun backupShortcut_explainsWhyItComesFirst() {
        var opened = false
        compose.setContent {
            WhfinTheme {
                DataHealthScreen(
                    state = DataHealthViewModel.State.Checked(emptyList()),
                    onOpenBackup = { opened = true },
                )
            }
        }

        // The screen is a lazy list, so the row has to be scrolled into composition before it exists.
        val summary = context.getString(R.string.data_health_backup_summary)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(summary))
        compose.onNodeWithText(summary).performClick()

        assertTrue(opened)
    }

    @Test
    fun aFindingNamesTheOperationAndOpensIt() {
        var opened: Long? = null
        val issue = IntegrityIssue(
            code = "duplicate_statement_row",
            severity = IntegritySeverity.ERROR,
            entity = "transactions",
            entityId = 4293,
            message = "An SMS row stands beside a statement row of the same money.",
        )
        compose.setContent {
            WhfinTheme {
                DataHealthScreen(
                    state = DataHealthViewModel.State.Checked(
                        issues = listOf(issue),
                        flagged = mapOf(4293L to "2026-09-05 · Courtyard Coffee · Everyday · -21.13 ₾"),
                    ),
                    onOpenTransaction = { opened = it },
                )
            }
        }

        // A finding used to be a database id. Nobody can find #4293 in a ledger, so the only way to
        // act on one was to scroll looking for something that looked doubled.
        compose.onNodeWithText("2026-09-05 · Courtyard Coffee · Everyday · -21.13 ₾")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(4293L, opened)
    }
}
