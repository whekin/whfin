package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.ui.theme.WhfinTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        compose.onNodeWithText(context.getString(R.string.category_setup_continue)).performClick()

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

    @Test
    fun acceptingAllLeavesAClearWayForwardEvenWhenOptionalPacksRemain() {
        var continued = false
        compose.setContent {
            var proposals by remember { mutableStateOf(listOf(CategoryProposals.Proposal(bike, 18))) }
            WhfinTheme {
                CategorySetupStep(
                    proposals = proposals,
                    packs = listOf(CategoryPacks.all.first()),
                    onAccept = { proposals = emptyList() },
                    onContinue = { continued = true },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_proposals_accept_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.category_setup_continue))
            .assertIsDisplayed().performClick()
        assertEquals(true, continued)
    }

    @Test fun optionalPacksCanBeSelectedAndWrittenTogetherWithoutAddingUnchosenInterests() {
        val packs = CategoryPacks.all.take(3)
        var added: List<String>? = null
        compose.setContent { WhfinTheme {
            CategorySetupStep(proposals = emptyList(), packs = packs, onAccept = {},
                onAddPacks = { added = it.map(CategoryPacks.Pack::id) },
                onContinue = {}, onBack = {})
        } }

        compose.onNodeWithText(packs[0].en).performScrollTo().performClick()
        compose.onNodeWithText(packs[2].en).performScrollTo().performClick()
        assertEquals(null, added)
        compose.onNodeWithText(context.getString(R.string.category_packs_add_selected, 2)).performClick()
        assertEquals(listOf(packs[0].id, packs[2].id), added)
    }

    @Test fun optionalPackListsOnlyCategoriesThatWouldActuallyBeAdded() {
        val online = CategoryPacks.all.single { it.id == "online" }
        compose.setContent { WhfinTheme {
            CategorySetupStep(proposals = emptyList(), packs = listOf(online),
                existingCategoryKeys = setOf("Subscriptions" to bike.kind),
                onAccept = {}, onContinue = {}, onBack = {})
        } }

        compose.onNodeWithText("Subscriptions", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tech", substring = true).assertExists()
    }

    @Test fun pendingBankHistoryDoesNotClaimTheCurrentSuggestionsAreFinal() {
        compose.setContent { WhfinTheme {
            CategorySetupStep(proposals = emptyList(), packs = emptyList(), onAccept = {},
                onContinue = {}, onBack = {}, bankHistoryPending = true)
        } }
        compose.onNodeWithText(context.getString(R.string.category_setup_pending_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.category_setup_none)).assertDoesNotExist()
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
        compose.onNodeWithText(context.getString(R.string.category_setup_continue))
            .performClick()

        assertEquals(true, continued)
    }
}
