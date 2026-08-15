package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.UncategorizedCounterparty
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
    private val merchant = UncategorizedMerchant(42, "EXAMPLE TRADE LTD", 61, 0)
    private val recipient = UncategorizedCounterparty(
        iban = "GE00WH0000000000000042",
        displayName = "Example Person",
        transactionCount = 12,
        totalMinor = -84_000,
        currency = "GEL",
        latestAt = 0,
    )

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
        compose.onNodeWithText("EXAMPLE TRADE LTD").assertIsDisplayed()
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

        compose.onNodeWithText("EXAMPLE TRADE LTD").performClick()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_intelligence_apply_body, 61, 61),
        ).assertIsDisplayed()
        compose.onNodeWithText("Transport").performClick()

        assertEquals(42L to 7L, assignment)
    }

    @Test
    fun transfersToPeople_areListedApartFromMerchants() {
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = listOf(merchant),
                        counterparties = listOf(recipient),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        // The section label is set in caps by the design system, so the assertion reads the words.
        compose.onNodeWithText(
            context.getString(R.string.category_intelligence_transfers_title),
            ignoreCase = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("Example Person").assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_intelligence_transactions, 12, 12),
        ).assertIsDisplayed()
    }

    @Test
    fun namingARecipient_isOfferedButNeverRequired() {
        var assignment: Assignment? = null
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        counterparties = listOf(recipient),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onAssignCounterparty = { iban, name, categoryId, personId, personName ->
                        assignment = Assignment(iban, name, categoryId, personId, personName)
                    },
                )
            }
        }

        compose.onNodeWithText("Example Person").performClick()
        compose.onNodeWithText("Transport").performClick()

        assertEquals(
            Assignment(recipient.iban, "Example Person", 7L, null, null),
            assignment,
        )
    }

    @Test
    fun addingTheRecipientAsAPerson_travelsWithTheCategory() {
        var assignment: Assignment? = null
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        counterparties = listOf(recipient),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onAssignCounterparty = { iban, name, categoryId, personId, personName ->
                        assignment = Assignment(iban, name, categoryId, personId, personName)
                    },
                )
            }
        }

        compose.onNodeWithText("Example Person").performClick()
        compose.onNodeWithText(
            context.getString(R.string.category_intelligence_person_create, "Example Person"),
        ).performClick()
        compose.onNodeWithText("Transport").performClick()

        assertEquals(
            Assignment(recipient.iban, "Example Person", 7L, null, "Example Person"),
            assignment,
        )
    }

    @Test
    fun aProposalShowsTheHistoryThatEarnedIt_andCreatesOnlyWhatWasAccepted() {
        var created: List<String>? = null
        val bike = CategoryCatalog.all.single { it.icon == "PedalBike" }
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        categories = listOf(transport),
                        proposals = listOf(CategoryProposals.Proposal(bike, transactionCount = 18)),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onCreateCategories = { created = it.map { definition -> definition.icon } },
                )
            }
        }

        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_proposals_evidence, 18, 18),
        ).assertIsDisplayed()
        compose.onNodeWithText(bike.en).performClick()

        assertEquals(listOf("PedalBike"), created)
    }

    @Test
    fun anInterestPackIsOfferedButNeverAddsItself() {
        var added: String? = null
        val outdoor = CategoryPacks.all.single { it.id == "outdoor" }
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        categories = listOf(transport),
                        packs = listOf(outdoor),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onAddPack = { added = it.id },
                )
            }
        }

        assertEquals(null, added)
        compose.onNodeWithText(outdoor.en).performClick()
        assertEquals("outdoor", added)
    }

    private data class Assignment(
        val iban: String,
        val name: String,
        val categoryId: Long,
        val personId: Long?,
        val personName: String?,
    )
}
