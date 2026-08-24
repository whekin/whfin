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
    fun openingBalanceIsNotUserFacingAccountActivity_butLaterAdjustmentIs() {
        val account = AccountEntity(
            id = 1,
            name = "Pocket money",
            type = AccountType.CASH,
            currency = "GEL",
        )
        val opening = TransactionEntity(
            id = 1,
            accountId = account.id,
            amountMinor = 30_000,
            currency = "GEL",
            occurredAt = 1_000,
            status = TxStatus.CONFIRMED,
            source = TxSource.ADJUSTMENT,
            isTransfer = true,
        )
        val laterAdjustment = TransactionEntity(
            id = 2,
            accountId = account.id,
            amountMinor = -500,
            currency = "GEL",
            occurredAt = 2_000,
            status = TxStatus.MANUAL,
            source = TxSource.ADJUSTMENT,
        )

        val items = buildBaseFeedItems(
            transactions = listOf(opening, laterAdjustment),
            merchants = emptyList(),
            categories = emptyList(),
            accounts = listOf(account),
            masksByAccount = emptyMap(),
            zone = ZoneOffset.UTC,
        )

        assertEquals(listOf(laterAdjustment.id), items.map { it.tx.id })
    }

    @Test
    fun bankToCashTransfer_usesAccountNamesWithIbanTail() {
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

        // Своё имя счёта впереди: валюта уже видна по сумме строки, хвост IBAN остаётся различителем.
        assertEquals("Everyday •0001 → Pocket money", item.transferSummary)
    }

    @Test
    fun bankAccountWithoutName_fallsBackToCurrencyAndIbanTail() {
        val accounts = listOf(
            AccountEntity(
                id = 1,
                name = "",
                type = AccountType.BANK,
                currency = "GEL",
                iban = "GE00CD0000000000000002",
            ),
            AccountEntity(
                id = 2,
                name = "Pocket money",
                type = AccountType.CASH,
                currency = "GEL",
            ),
        )
        val transactions = listOf(
            transferLeg(id = 1, accountId = 1, amountMinor = -5_000),
            transferLeg(id = 2, accountId = 2, amountMinor = 5_000),
        )

        val item = buildBaseFeedItems(
            transactions = transactions,
            merchants = emptyList(),
            categories = emptyList(),
            accounts = accounts,
            masksByAccount = emptyMap(),
            zone = ZoneOffset.UTC,
        ).single()

        assertEquals("GEL •0002 → Pocket money", item.transferSummary)
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
