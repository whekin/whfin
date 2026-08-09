package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
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
    fun issues_areNamedInPlainLanguageWithTheirRule() {
        val issues = listOf(
            IntegrityIssue(
                code = "allocation_total_mismatch",
                severity = IntegritySeverity.ERROR,
                entity = "transactions",
                entityId = 42,
                message = "Allocation total does not equal the parent amount.",
            ),
        )
        compose.setContent {
            WhfinTheme { DataHealthScreen(state = DataHealthViewModel.State.Checked(issues)) }
        }

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.data_health_issues_title, 1, 1)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.data_health_family_allocations)).assertIsDisplayed()
        // The rule name stays visible: it is what makes a report actionable in a bug thread.
        compose.onNodeWithText("transactions · #42 · allocation_total_mismatch").assertIsDisplayed()
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
}
