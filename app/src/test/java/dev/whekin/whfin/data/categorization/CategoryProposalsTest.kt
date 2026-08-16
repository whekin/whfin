package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryProposalsTest {

    private fun merchant(id: Long, key: String) =
        MerchantEntity(id = id, normalizedKey = key, displayName = key)

    private fun category(id: Long, icon: String, kind: CategoryKind = CategoryKind.EXPENSE) =
        CategoryEntity(id = id, name = icon, kind = kind, icon = icon, color = 0)

    private val merchants = listOf(
        merchant(1, "nikora"),
        merchant(2, "spar"),
        merchant(3, "bus_tbilisi"),
        merchant(4, "bike24"),
        merchant(5, "totally unknown shop"),
    )
    private val usage = mapOf(1L to 400, 2L to 300, 3L to 800, 4L to 4, 5L to 90)

    @Test
    fun `a category is offered with the history that earned it`() {
        val proposals = CategoryProposals.from(merchants, usage, existing = emptyList())

        assertEquals(
            listOf("Transport" to 800, "Groceries" to 700, "Bike" to 4),
            proposals.map { it.definition.en to it.transactionCount },
        )
    }

    @Test
    fun `a category the ledger already has is not offered again`() {
        val proposals = CategoryProposals.from(
            merchants,
            usage,
            existing = listOf(category(1, "ShoppingCart"), category(2, "DirectionsBus")),
        )

        assertEquals(listOf("Bike"), proposals.map { it.definition.en })
    }

    /** An unrecognized merchant is not evidence of anything; it is a question for the user. */
    @Test
    fun `an unknown merchant proposes nothing, however much was spent there`() {
        val proposals = CategoryProposals.from(
            listOf(merchant(5, "totally unknown shop")),
            mapOf(5L to 900),
            existing = emptyList(),
        )

        assertEquals(emptyList<String>(), proposals.map { it.definition.en })
    }

    @Test
    fun `a single stray transaction can be held back`() {
        val proposals = CategoryProposals.from(merchants, usage, emptyList(), minimumTransactions = 5)

        assertEquals(listOf("Transport", "Groceries"), proposals.map { it.definition.en })
    }

    @Test
    fun `nothing is proposed for an empty ledger`() {
        assertEquals(
            emptyList<CategoryProposals.Proposal>(),
            CategoryProposals.from(emptyList(), emptyMap(), emptyList()),
        )
    }

    @Test
    fun `an untouched category is reported, and the system one is left alone`() {
        val groceries = category(1, "ShoppingCart")
        val unusedBike = category(2, "PedalBike")
        val system = category(3, "HelpOutline").copy(isSystem = true)

        val unused = CategoryProposals.unused(
            listOf(groceries, unusedBike, system),
            usageByCategoryId = mapOf(1L to 903),
        )

        assertEquals(listOf(unusedBike.id), unused.map { it.id })
    }

    /**
     * A category the bank fills by itself is seeded only on a fresh ledger, because re-seeding the
     * base into an existing one would resurrect whatever the user deleted. Without an offer, an
     * install made before the rule shipped would never get the category and the rule would silently
     * do nothing there — which is exactly what happened when bill payments started being filed.
     */
    @Test
    fun `a category the bank fills is offered to a ledger that predates the rule`() {
        val proposals = CategoryProposals.from(
            merchants = emptyList(),
            usageByMerchantId = emptyMap(),
            existing = emptyList(),
            operationEvidence = mapOf(("ReceiptLong" to CategoryKind.EXPENSE) to 37),
        )

        assertEquals(1, proposals.size)
        assertEquals("ReceiptLong", proposals.single().definition.icon)
        assertEquals(37, proposals.single().transactionCount)
    }

    @Test
    fun `a category that already exists is never offered again`() {
        val bills = CategoryEntity(
            id = 8,
            name = "Bills & charges",
            kind = CategoryKind.EXPENSE,
            icon = "ReceiptLong",
            color = 0,
        )

        val proposals = CategoryProposals.from(
            merchants = emptyList(),
            usageByMerchantId = emptyMap(),
            existing = listOf(bills),
            operationEvidence = mapOf(("ReceiptLong" to CategoryKind.EXPENSE) to 37),
        )

        assertEquals(emptyList<CategoryProposals.Proposal>(), proposals)
    }

    @Test
    fun `the base a fresh ledger starts with stays small and universal`() {
        assertEquals(10, CategoryCatalog.base.size)
        assertTrue(
            "everything in the base must be in the catalog",
            CategoryCatalog.base.all { it in CategoryCatalog.all },
        )
    }

    /**
     * The base may only grow for categories nothing else can produce.
     *
     * Anything the user could be asked about waits to be earned — from history, or from a pack they
     * pick. The exceptions are the ones the bank fills by itself: no evidence for them ever arrives
     * unless the category is already there to receive it.
     */
    @Test
    fun `the base only grows for categories the bank fills by itself`() {
        val filledByTheBank = setOf("AccountBalance", "Percent", "ReceiptLong")
        val universal = setOf(
            "ShoppingCart", "Restaurant", "DirectionsBus", "Home", "Bolt", "MedicalServices",
            "Payments",
        )

        CategoryCatalog.base.forEach { definition ->
            assertTrue(
                "${definition.en} is in the base without being universal or bank-filled",
                definition.icon in filledByTheBank || definition.icon in universal,
            )
        }
    }

    /** A pack that names an icon the catalog lost would silently create fewer categories. */
    @Test
    fun `every pack resolves to real definitions`() {
        CategoryPacks.all.forEach { pack ->
            assertEquals(
                "pack ${pack.id}",
                pack.icons.size,
                CategoryPacks.definitions(pack).size,
            )
        }
    }
}
