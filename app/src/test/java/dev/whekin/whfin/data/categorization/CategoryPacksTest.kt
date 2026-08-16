package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pack describes a whole interest, so the shape of that interest has to survive being created.
 */
class CategoryPacksTest {

    @Test
    fun `every pack names categories the catalog actually has`() {
        CategoryPacks.all.forEach { pack ->
            pack.icons.forEach { icon ->
                assertNotNull(
                    "Pack ${pack.id} names an unknown icon $icon",
                    CategoryCatalog.all.firstOrNull { it.icon == icon },
                )
            }
        }
    }

    /**
     * Creation resolves a parent among the categories that already exist, so a child listed first
     * would land at the top level and the group would be silently lost.
     */
    @Test
    fun `a pack lists every parent before the children that name it`() {
        CategoryPacks.all.forEach { pack ->
            val definitions = CategoryPacks.definitions(pack)
            definitions.forEachIndexed { index, definition ->
                val parentIcon = definition.parentIcon ?: return@forEachIndexed
                val parentIndex = definitions.indexOfFirst { it.icon == parentIcon }
                assertTrue(
                    "In ${pack.id}, ${definition.en} is created before its parent $parentIcon",
                    parentIndex == -1 || parentIndex < index,
                )
            }
        }
    }

    /** The bike group is the case that motivated grouping: one hobby, several rhythms of spending. */
    @Test
    fun `the outdoor pack delivers bike spending already grouped`() {
        val outdoor = CategoryPacks.all.single { it.id == "outdoor" }
        val definitions = CategoryPacks.definitions(outdoor)

        val children = definitions.filter { it.parentIcon == "PedalBike" }.map { it.en }

        assertEquals(listOf("Lifts and shuttles", "Bike service", "Bike rental"), children)
        assertEquals("PedalBike", definitions.first().icon)
    }

    /**
     * A parent named by a pack must exist somewhere, or the child quietly becomes a root and the
     * pack promises a group it cannot deliver.
     */
    @Test
    fun `a parent is either in the same pack or already on every ledger`() {
        val baseIcons = CategoryCatalog.base.map { it.icon }.toSet()
        CategoryPacks.all.forEach { pack ->
            CategoryPacks.definitions(pack).forEach { definition ->
                val parentIcon = definition.parentIcon ?: return@forEach
                assertTrue(
                    "${definition.en} in ${pack.id} names a parent nothing creates",
                    parentIcon in baseIcons || pack.icons.contains(parentIcon),
                )
            }
        }
    }

    /** One level is the whole contract of the tree; a pack must not describe a second. */
    @Test
    fun `no pack describes a grandchild`() {
        CategoryCatalog.all.forEach { definition ->
            val parentIcon = definition.parentIcon ?: return@forEach
            val parent = CategoryCatalog.byIcon(parentIcon, definition.kind)
            assertNotNull("${definition.en} names a parent outside the catalog", parent)
            assertEquals(
                "${definition.en} would be a grandchild",
                null,
                parent?.parentIcon,
            )
        }
    }

    /** Proposals come one merchant at a time and cannot know what they belong to. */
    @Test
    fun `a category proposed from history is never proposed as part of a group`() {
        val proposals = CategoryProposals.from(
            merchants = listOf(
                dev.whekin.whfin.data.db.MerchantEntity(id = 1, normalizedKey = "bike24", displayName = "Bike24"),
            ),
            usageByMerchantId = mapOf(1L to 4),
            existing = emptyList(),
        )

        assertEquals(1, proposals.size)
        assertEquals(CategoryKind.EXPENSE, proposals.single().definition.kind)
        assertEquals("PedalBike", proposals.single().definition.icon)
    }
}
