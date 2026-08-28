package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
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

    @Test
    fun editorNamesTheUsualDayAndLatestDeadlineInsteadOfAnEvenWindow() {
        compose.setContent {
            WhfinTheme {
                IncomeSourceSheet(
                    source = null,
                    accounts = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _ -> },
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
