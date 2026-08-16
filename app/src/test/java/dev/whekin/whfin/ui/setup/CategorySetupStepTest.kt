package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Setup used to finish without ever mentioning categories; this is the step that changed that. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategorySetupStepTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val bike = CategoryCatalog.all.single { it.icon == "PedalBike" }

    @Test
    fun proposalsAreOffered_withTheHistoryThatEarnedThem() {
        compose.setContent {
            WhfinTheme {
                CategorySetupStep(
                    proposals = listOf(CategoryProposals.Proposal(bike, transactionCount = 18)),
                    onAccept = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(bike.en).assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_proposals_evidence, 18, 18),
        ).assertIsDisplayed()
    }

    /** Nothing is created until the user says so: an onboarding step must not decide for them. */
    @Test
    fun continuing_createsNothing() {
        var accepted: List<String>? = null
        var continued = false
        compose.setContent {
            WhfinTheme {
                CategorySetupStep(
                    proposals = listOf(CategoryProposals.Proposal(bike, transactionCount = 18)),
                    onAccept = { accepted = it.map { definition -> definition.icon } },
                    onContinue = { continued = true },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_setup_skip)).performClick()

        assertEquals(null, accepted)
        assertEquals(true, continued)
    }

    @Test
    fun acceptingAll_createsExactlyWhatWasProposed() {
        var accepted: List<String>? = null
        compose.setContent {
            WhfinTheme {
                CategorySetupStep(
                    proposals = listOf(CategoryProposals.Proposal(bike, transactionCount = 18)),
                    onAccept = { accepted = it.map { definition -> definition.icon } },
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_proposals_accept_all))
            .performClick()

        assertEquals(listOf("PedalBike"), accepted)
    }

    /** A ledger that earned nothing new still needs a way forward, not an empty list. */
    @Test
    fun aLedgerWithNothingToPropose_stillOffersAWayOn() {
        var continued = false
        compose.setContent {
            WhfinTheme {
                CategorySetupStep(
                    proposals = emptyList(),
                    onAccept = {},
                    onContinue = { continued = true },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_setup_none)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.personal_setup_continue_action))
            .performClick()

        assertEquals(true, continued)
    }
}
