package dev.whekin.whfin.ui.settings

import dev.whekin.whfin.data.db.StatementImportEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementImportRemovalTest {
    @Test
    fun `only imports with no ledger or review effect can be removed`() {
        assertTrue(statement().canRemoveFromHistory)
        assertFalse(statement(inserted = 1).canRemoveFromHistory)
        assertFalse(statement(reconciled = 1).canRemoveFromHistory)
        assertFalse(statement(reviewCount = 1).canRemoveFromHistory)
    }

    private fun statement(
        inserted: Int = 0,
        reconciled: Int = 0,
        reviewCount: Int = 0,
    ) = StatementImportEntity(
        accountId = 1,
        periodFrom = null,
        periodTo = null,
        openingBalanceMinor = null,
        closingBalanceMinor = null,
        totalRows = 2,
        inserted = inserted,
        duplicates = 2,
        reconciled = reconciled,
        reviewCount = reviewCount,
        importedAt = 0,
    )
}
