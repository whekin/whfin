package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTransferSummaryTest {
    @Test
    fun bankToCashTransfer_usesCashAliasInsteadOfTruncatedSuffix() {
        val accounts = listOf(
            AccountEntity(
                id = 1,
                name = "Everyday",
                type = AccountType.BANK,
                currency = "GEL",
                iban = "GE00CD0000000000000001",
            ),
            AccountEntity(
                id = 2,
                name = "Pocket money",
                type = AccountType.CASH,
                currency = "GEL",
            ),
        )
        val transactions = listOf(
            transferLeg(id = 1, accountId = 1, amountMinor = -20_000),
            transferLeg(id = 2, accountId = 2, amountMinor = 20_000),
        )

        val item = buildBaseFeedItems(
            transactions = transactions,
            merchants = emptyList(),
            categories = emptyList(),
            accounts = accounts,
            masksByAccount = emptyMap(),
            zone = ZoneOffset.UTC,
        ).single()

        assertEquals("GEL •0001 → Pocket money", item.transferSummary)
    }

    private fun transferLeg(id: Long, accountId: Long, amountMinor: Long) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = amountMinor,
        currency = "GEL",
        occurredAt = 1_000,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
        transferGroupId = 1,
        isTransfer = true,
        externalKey = "demo-transfer-$id",
    )
}
