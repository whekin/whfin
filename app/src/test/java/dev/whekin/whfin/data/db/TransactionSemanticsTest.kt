package dev.whekin.whfin.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSemanticsTest {
    @Test
    fun openingClassifierCoversManualAndStatementAnchors() {
        assertTrue(anchor(externalKey = null).isOpeningBalanceAnchor())
        assertTrue(anchor(externalKey = "opening|GE00TEST|GEL|2026-01-01").isOpeningBalanceAnchor())
    }

    @Test
    fun openingClassifierDoesNotHideRegularCorrections() {
        assertFalse(anchor(isTransfer = false).isOpeningBalanceAnchor())
        assertFalse(anchor(externalKey = "correction|42", isVoided = true).isOpeningBalanceAnchor())
    }

    private fun anchor(
        externalKey: String? = null,
        isTransfer: Boolean = true,
        isVoided: Boolean = false,
    ) = TransactionEntity(
        accountId = 1,
        amountMinor = 10_000,
        currency = "GEL",
        occurredAt = 1_000,
        status = TxStatus.CONFIRMED,
        source = TxSource.ADJUSTMENT,
        isTransfer = isTransfer,
        externalKey = externalKey,
        isVoided = isVoided,
    )
}
