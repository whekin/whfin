package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.UncategorizedMerchant
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryIntelligenceScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val transport = CategoryEntity(
        id = 7,
        name = "Transport",
        kind = CategoryKind.EXPENSE,
        icon = "DirectionsBus",
        color = 0xff5d7f91.toInt(),
    )
    private val merchant = UncategorizedMerchant(42, "MERCURY LTD", 61, 0)

    @Test
    fun coverageAndUnknownMerchant_areExplainedTogether() {
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 3),
                        unresolved = listOf(merchant),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_intelligence_percent, 64))
            .assertIsDisplayed()
        compose.onNodeWithText("MERCURY LTD").assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_intelligence_transactions, 61, 61),
        ).assertIsDisplayed()
    }

    @Test
    fun choosingCategory_appliesMerchantRule() {
        var assignment: Pair<Long, Long>? = null
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = listOf(merchant),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { merchantId, categoryId -> assignment = merchantId to categoryId },
                )
            }
        }

        compose.onNodeWithText("MERCURY LTD").performClick()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_intelligence_apply_body, 61, 61),
        ).assertIsDisplayed()
        compose.onNodeWithText("Transport").performClick()

        assertEquals(42L to 7L, assignment)
    }
}
