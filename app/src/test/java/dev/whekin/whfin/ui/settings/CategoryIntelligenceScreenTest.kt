package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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

    /**
     * The screen leads with the result and the size of what is left, not with the rows themselves.
     * A queue of hundreds cannot be the first thing on a screen that also has to show anything else.
     */
    @Test
    fun coverageAndTheSizeOfEachQueue_areShownWithoutTheQueuesThemselves() {
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 3),
                        unresolved = listOf(merchant),
                        counterparties = listOf(recipient),
                        categories = listOf(transport),
                    ),
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_intelligence_percent, 64))
            .assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_queue_merchants, 1, 1),
        ).assertIsDisplayed()
        val recipients = context.resources.getQuantityString(R.plurals.category_queue_recipients, 1, 1)
        compose.onNode(hasScrollAction(), useUnmergedTree = false).performScrollToNode(hasText(recipients))
        compose.onNodeWithText(recipients).assertIsDisplayed()
        compose.onNodeWithText("EXAMPLE TRADE LTD").assertDoesNotExist()
        compose.onNodeWithText("Example Person").assertDoesNotExist()
    }

    @Test
    fun openingAQueue_asksTheShellToNavigate() {
        var opened: CategoryQueue? = null
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = listOf(merchant),
                        categories = listOf(transport),
                    ),
                    onOpenQueue = { opened = it },
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.category_intelligence_review_title),
            ignoreCase = true,
        ).performClick()

        assertEquals(CategoryQueue.Merchants, opened)
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
                    queue = CategoryQueue.Merchants,
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
                    queue = CategoryQueue.Transfers,
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Example Person").assertIsDisplayed()
        compose.onNodeWithText("EXAMPLE TRADE LTD").assertDoesNotExist()
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
                    queue = CategoryQueue.Transfers,
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
                    queue = CategoryQueue.Transfers,
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

    /**
     * The answer for most recipients: nothing in common, so no rule. It has to be reachable without
     * choosing a category, because choosing one would be the wrong answer recorded permanently.
     */
    @Test
    fun aRecipientWithNothingInCommon_canBeRefusedWithoutChoosingACategory() {
        var dismissed: Pair<String, String>? = null
        var assigned = false
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        counterparties = listOf(recipient),
                        categories = listOf(transport),
                    ),
                    queue = CategoryQueue.Transfers,
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onAssignCounterparty = { _, _, _, _, _ -> assigned = true },
                    onDismissCounterparty = { iban, name -> dismissed = iban to name },
                )
            }
        }

        compose.onNodeWithText("Example Person").performClick()
        compose.onNodeWithText(context.getString(R.string.category_intelligence_not_a_rule))
            .performClick()

        assertEquals(recipient.iban to "Example Person", dismissed)
        assertEquals(false, assigned)
    }

    /** Recipients seen once are kept below the ones that repeat, not mixed in with them. */
    @Test
    fun aRecipientSeenOnce_isSeparatedFromTheOnesThatRepeat() {
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        counterparties = listOf(recipient),
                        counterpartiesOnce = listOf(
                            recipient.copy(
                                iban = "GE00WH0000000000000043",
                                displayName = "One Off",
                                transactionCount = 1,
                            ),
                        ),
                        categories = listOf(transport),
                    ),
                    queue = CategoryQueue.Transfers,
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.category_intelligence_once_title),
            ignoreCase = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("One Off").assertIsDisplayed()
    }

    /**
     * The gap that made a wrong rule permanent: it could be written, but never read back or changed.
     */
    @Test
    fun aRuleAlreadyWritten_canBeFoundAndCorrected() {
        var corrected: Pair<Long, Long>? = null
        val rule = CounterpartyRuleView(
            id = 3,
            iban = recipient.iban,
            displayName = "Example Person",
            categoryId = 99,
            categoryName = "Groceries",
            personName = null,
            transactionCount = 12,
            isDismissed = false,
        )
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        rules = listOf(rule),
                        categories = listOf(transport),
                    ),
                    queue = CategoryQueue.Rules,
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onUpdateRule = { changed, categoryId -> corrected = changed.id to categoryId },
                )
            }
        }

        compose.onNodeWithText("Example Person").performClick()
        compose.onNodeWithText("Transport").performClick()

        assertEquals(3L to 7L, corrected)
    }

    @Test
    fun deletingARule_saysHowMuchHistoryItWouldUnlabel() {
        var deleted = false
        val rule = CounterpartyRuleView(
            id = 3,
            iban = recipient.iban,
            displayName = "Example Person",
            categoryId = 99,
            categoryName = "Groceries",
            personName = null,
            transactionCount = 12,
            isDismissed = false,
        )
        compose.setContent {
            WhfinTheme {
                CategoryIntelligenceScreen(
                    state = CategoryIntelligenceState(
                        coverage = CategoryCoverage(100, 64, 0),
                        unresolved = emptyList(),
                        rules = listOf(rule),
                        categories = listOf(transport),
                    ),
                    queue = CategoryQueue.Rules,
                    onCheckLocalRules = {},
                    onAssignCategory = { _, _ -> },
                    onDeleteRule = { deleted = true },
                )
            }
        }

        compose.onNodeWithText("Example Person").performClick()
        compose.onNodeWithText(context.getString(R.string.category_rules_delete)).performClick()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.category_rules_delete_body, 12, 12),
        ).assertIsDisplayed()
        assertEquals(false, deleted)

        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        assertEquals(true, deleted)
    }

    private data class Assignment(
        val iban: String,
        val name: String,
        val categoryId: Long,
        val personId: Long?,
        val personName: String?,
    )
}
