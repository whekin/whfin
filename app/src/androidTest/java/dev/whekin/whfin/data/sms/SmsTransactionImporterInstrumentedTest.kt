package dev.whekin.whfin.data.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsTransactionImporterInstrumentedTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var groupId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        importer = SmsTransactionImporter(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unknownCard_isVisible_thenMappingImportsAndFutureRetryIsDuplicate() = runBlocking {
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )

        val first = importer.import(CARD_PAYMENT, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, first.outcome)
        assertEquals(SmsDiagnosticReason.NO_CARD_MAPPING, first.reason)
        val diagnosticId = requireNotNull(first.diagnosticId)
        assertEquals(0, transactionCount())
        assertEquals(diagnosticId, db.smsDiagnosticDao().observeUnrouted().first().single().id)

        val resolved = importer.resolveDiagnostic(
            diagnosticId,
            accountId,
            PaymentInstrumentType.PHYSICAL_CARD,
        )
        assertEquals(SmsDiagnosticOutcome.IMPORTED, resolved.outcome)
        assertNotNull(resolved.transactionId)
        assertEquals(accountId, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        assertTrue(db.smsDiagnosticDao().observeUnrouted().first().isEmpty())
        val resolvedTransaction = db.transactionDao().byId(requireNotNull(resolved.transactionId))!!
        assertEquals(TxStatus.CONFIRMED, resolvedTransaction.status)
        assertEquals(
            listOf(resolvedTransaction.id),
            db.transactionDao().reconciliationCandidates(
                accountId = accountId,
                fromMillis = 0,
                toMillis = Long.MAX_VALUE,
            ).map { it.id },
        )

        val repeated = importer.import(CARD_PAYMENT, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, repeated.outcome)
        assertEquals(1, transactionCount())
    }

    @Test
    fun mappingOneCardPayment_routesAllQueuedPaymentsForTheSameCard() = runBlocking {
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )
        val first = importer.import(CARD_PAYMENT, RECEIVED_AT)
        val second = importer.import(SECOND_CARD_PAYMENT, RECEIVED_AT + 1_000)

        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, first.outcome)
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, second.outcome)
        assertEquals(2, db.smsDiagnosticDao().observeUnrouted().first().size)

        importer.resolveDiagnostic(
            requireNotNull(first.diagnosticId),
            accountId,
            PaymentInstrumentType.PHYSICAL_CARD,
        )

        assertEquals(2, transactionCount())
        assertTrue(db.smsDiagnosticDao().observeUnrouted().first().isEmpty())
        val selectedTransaction = db.transactionDao().byId(
            requireNotNull(db.smsDiagnosticDao().byId(requireNotNull(first.diagnosticId))?.transactionId),
        )!!
        val automaticallyRoutedTransaction = db.transactionDao().byId(
            requireNotNull(db.smsDiagnosticDao().byId(requireNotNull(second.diagnosticId))?.transactionId),
        )!!
        assertEquals(TxStatus.CONFIRMED, selectedTransaction.status)
        assertEquals(TxStatus.PENDING, automaticallyRoutedTransaction.status)
    }

    @Test
    fun ambiguousTransfer_requestsAccountInsteadOfDroppingMessage() = runBlocking {
        repeat(2) { index ->
            db.accountDao().insert(
                AccountEntity(
                    name = "GEL ${index + 1}",
                    type = AccountType.BANK,
                    groupId = groupId,
                    currency = "GEL",
                ),
            )
        }
        val result = importer.import(OUTGOING_TRANSFER, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.MULTIPLE_ACCOUNTS, result.reason)
        assertEquals(0, transactionCount())
    }

    @Test
    fun depositTopUp_withoutConfiguredReserve_requiresExplicitAccountChoice() = runBlocking {
        db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )

        val result = importer.import(DEPOSIT_TOP_UP, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(0, transactionCount())
    }

    @Test
    fun matchingDepositTopUpAndOutgoingTransfer_becomeOneSavingsTransfer() = runBlocking {
        val mainId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )
        val depositId = db.accountDao().insert(
            AccountEntity(name = "Hot deposit", type = AccountType.SAVINGS, groupId = groupId, currency = "GEL"),
        )

        // History is read newest-first: Credo normally sends the deposit notification second.
        val deposit = importer.import(DEPOSIT_TOP_UP, RECEIVED_AT + 1_000)
        val outgoing = importer.import(DEPOSIT_OUTGOING_TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, deposit.outcome)
        assertEquals(SmsDiagnosticOutcome.IMPORTED, outgoing.outcome)
        val depositTransaction = db.transactionDao().byId(requireNotNull(deposit.transactionId))!!
        val outgoingTransaction = db.transactionDao().byId(requireNotNull(outgoing.transactionId))!!
        assertEquals(depositId, depositTransaction.accountId)
        assertEquals(mainId, outgoingTransaction.accountId)
        assertEquals(450_000L, depositTransaction.amountMinor)
        assertEquals(-450_000L, outgoingTransaction.amountMinor)
        assertTrue(depositTransaction.isTransfer)
        assertTrue(outgoingTransaction.isTransfer)
        assertNotNull(depositTransaction.transferGroupId)
        assertEquals(depositTransaction.transferGroupId, outgoingTransaction.transferGroupId)
    }

    @Test
    fun sameDepositAmountOutsidePairWindow_staysSeparate() = runBlocking {
        val mainId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )
        db.accountDao().insert(
            AccountEntity(name = "Hot deposit", type = AccountType.SAVINGS, groupId = groupId, currency = "GEL"),
        )

        val deposit = importer.import(DEPOSIT_TOP_UP, RECEIVED_AT + 1_000)
        val unresolved = importer.import(FAR_DEPOSIT_OUTGOING_TRANSFER, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, unresolved.outcome)
        val outgoing = importer.resolveDiagnostic(requireNotNull(unresolved.diagnosticId), mainId)

        val depositTransaction = db.transactionDao().byId(requireNotNull(deposit.transactionId))!!
        val outgoingTransaction = db.transactionDao().byId(requireNotNull(outgoing.transactionId))!!
        assertFalse(depositTransaction.isTransfer)
        assertFalse(outgoingTransaction.isTransfer)
        assertEquals(null, depositTransaction.transferGroupId)
        assertEquals(null, outgoingTransaction.transferGroupId)
    }

    @Test
    fun diagnosticsSchema_neverHasRawMessageColumn() {
        val columns = buildList {
            db.openHelper.writableDatabase.query("PRAGMA table_info(`sms_diagnostics`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(name))
            }
        }
        assertFalse(columns.any { it.contains("body", ignoreCase = true) || it.contains("raw", ignoreCase = true) })
    }

    @Test
    fun proactiveCardMapping_routesMatchingCurrency() = runBlocking {
        val iban = "GE00CD0000000000000001"
        val account = AccountEntity(
            id = db.accountDao().insert(
                AccountEntity(
                    name = "Main GEL", type = AccountType.BANK, groupId = groupId,
                    currency = "GEL", iban = iban,
                ),
            ),
            name = "Main GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
            iban = iban,
        )
        val usd = AccountEntity(
            id = db.accountDao().insert(
                AccountEntity(
                    name = "Main USD", type = AccountType.BANK, groupId = groupId,
                    currency = "USD", iban = iban,
                ),
            ),
            name = "Main USD",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "USD",
            iban = iban,
        )

        db.paymentInstrumentDao().linkForAccounts(
            listOf(account, usd),
            "0001",
            PaymentInstrumentType.VIRTUAL_CARD,
        )

        assertTrue(db.paymentInstrumentDao().configuredCount() > 0)
        assertEquals(account.id, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        assertEquals(usd.id, db.accountDao().byCardAndCurrency("0001", "USD").single().id)
        assertEquals(SmsDiagnosticOutcome.IMPORTED, importer.import(CARD_PAYMENT, RECEIVED_AT).outcome)
    }

    @Test
    fun ownTransfer_createsBothPendingLegsInOneAtomicGroup() = runBlocking {
        val fromId = db.accountDao().insert(
            AccountEntity(
                name = "Main GEL",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = FROM_IBAN,
            ),
        )
        val toId = db.accountDao().insert(
            AccountEntity(
                name = "Reserve GEL",
                type = AccountType.SAVINGS,
                groupId = groupId,
                currency = "GEL",
                iban = TO_IBAN,
            ),
        )

        val result = importer.import(OWN_TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        assertEquals(2, transactionCount())
        val source = db.transactionDao().byId(requireNotNull(result.transactionId))!!
        val legs = db.transactionDao().byTransferGroup(requireNotNull(source.transferGroupId))
        assertEquals(2, legs.size)
        assertEquals(setOf(fromId, toId), legs.map { it.accountId }.toSet())
        assertEquals(setOf(-20_000L, 20_000L), legs.map { it.amountMinor }.toSet())
        assertTrue(legs.all { it.status == TxStatus.PENDING && it.source == TxSource.SMS && it.isTransfer })
        assertEquals(133_456L, legs.single { it.accountId == fromId }.balanceAfterMinor)
        assertEquals(null, legs.single { it.accountId == toId }.balanceAfterMinor)
        assertEquals(
            TransferGroupType.TRANSFER.name,
            transferGroupType(requireNotNull(source.transferGroupId)),
        )
    }

    @Test
    fun currencyExchange_waitsForBothAccounts_thenResolvesAtomically() = runBlocking {
        val gelId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )

        val unresolved = importer.import(CURRENCY_EXCHANGE, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, unresolved.outcome)
        assertEquals(0, transactionCount())

        val usdId = db.accountDao().insert(
            AccountEntity(name = "Main USD", type = AccountType.BANK, groupId = groupId, currency = "USD"),
        )
        val partialAttempt = importer.resolveDiagnostic(requireNotNull(unresolved.diagnosticId), gelId)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, partialAttempt.outcome)
        assertEquals(0, transactionCount())

        val resolved = importer.resolveGroupedDiagnostic(
            requireNotNull(unresolved.diagnosticId),
            gelId,
            usdId,
        )

        assertEquals(SmsDiagnosticOutcome.IMPORTED, resolved.outcome)
        assertEquals(2, transactionCount())
        val source = db.transactionDao().byId(requireNotNull(resolved.transactionId))!!
        val legs = db.transactionDao().byTransferGroup(requireNotNull(source.transferGroupId))
        assertEquals(-5_000L, legs.single { it.accountId == gelId }.amountMinor)
        assertEquals(1_800L, legs.single { it.accountId == usdId }.amountMinor)
        assertTrue(legs.all { it.status == TxStatus.CONFIRMED })
        assertEquals(null, legs.single { it.accountId == gelId }.balanceAfterMinor)
        assertEquals(1_800L, legs.single { it.accountId == usdId }.balanceAfterMinor)
        assertEquals(
            TransferGroupType.CONVERSION.name,
            transferGroupType(requireNotNull(source.transferGroupId)),
        )
    }

    @Test
    fun routingCardPayment_afterStatement_attachesEvidenceWithoutDraft() = runBlocking {
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )
        val statementId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -1_234,
                currency = "GEL",
                occurredAt = LocalDateTime.of(2026, 4, 3, 20, 48, 5)
                    .atZone(ZoneId.of("Asia/Tbilisi"))
                    .toInstant()
                    .toEpochMilli(),
                rawCounterparty = "EXAMPLE MARKET",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                externalKey = "stmt|example",
            ),
        )
        val unresolved = importer.import(CARD_PAYMENT, RECEIVED_AT)

        val resolved = importer.resolveDiagnostic(
            requireNotNull(unresolved.diagnosticId),
            accountId,
            PaymentInstrumentType.PHYSICAL_CARD,
        )

        assertEquals(SmsDiagnosticOutcome.ATTACHED, resolved.outcome)
        assertEquals(statementId, resolved.transactionId)
        assertEquals(1, transactionCount())
        assertTrue(db.smsDiagnosticDao().observeUnrouted().first().isEmpty())
    }

    @Test
    fun mappedCardPayment_afterStatementAttachesImmediately() = runBlocking {
        val account = AccountEntity(
            id = db.accountDao().insert(
                AccountEntity(
                    name = "Main GEL",
                    type = AccountType.BANK,
                    groupId = groupId,
                    currency = "GEL",
                ),
            ),
            name = "Main GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
        )
        db.paymentInstrumentDao().linkForAccount(
            account,
            "0001",
            PaymentInstrumentType.PHYSICAL_CARD,
        )
        val statementId = db.transactionDao().insert(
            TransactionEntity(
                accountId = account.id,
                amountMinor = -1_234,
                currency = "GEL",
                occurredAt = LocalDateTime.of(2026, 4, 3, 20, 48, 5)
                    .atZone(ZoneId.of("Asia/Tbilisi"))
                    .toInstant()
                    .toEpochMilli(),
                rawCounterparty = "EXAMPLE MARKET",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                externalKey = "stmt|mapped-example",
            ),
        )

        val result = importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(statementId, result.transactionId)
        assertEquals(1, transactionCount())
    }

    private fun transactionCount(): Int = db.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM transactions")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun transferGroupType(groupId: Long): String = db.openHelper.writableDatabase
        .query("SELECT type FROM transfer_groups WHERE id = ?", arrayOf(groupId.toString()))
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val RECEIVED_AT = 1_775_000_000_000
        const val FROM_IBAN = "GE00CD0000000000000001"
        const val TO_IBAN = "GE00CD0000000000000002"
        val CARD_PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance: 567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val SECOND_CARD_PAYMENT = """
            Payment: 21.98 GEL
            Card N ****0001
            EXAMPLE PHARMACY>Tbilisi               GE
            Balance: 545.91 GEL
            03/04/2026 21:03:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val OUTGOING_TRANSFER = """
            Outgoing transfer
            Amount: 100.00 GEL;
            Balance: 1234.56 GEL
            Date:4/5/2026 10:43:19 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val DEPOSIT_OUTGOING_TRANSFER = """
            Outgoing transfer
            Amount: 4500.00 GEL;
            Balance: 163.18 GEL
            Date:7/12/2026 5:18:36 AM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val DEPOSIT_TOP_UP = """
            Deposit top-up
            Amount: 4500.00 GEL
            Available Balance on Deposit 4500.00 GEL.
            Date: 7/12/2026 5:18:36 AM;
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val FAR_DEPOSIT_OUTGOING_TRANSFER = """
            Outgoing transfer
            Amount: 4500.00 GEL;
            Balance: 163.18 GEL
            Date:7/12/2026 5:21:00 AM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val OWN_TRANSFER = """
            Transfer between accounts
            Amount: 200.00 GEL;
            From: $FROM_IBAN
            To: $TO_IBAN
            Balance: 1334.56 GEL
            Date: 4/5/2026 10:43:03 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val CURRENCY_EXCHANGE = """
            Currency exchange
            Amount: 50.00 GEL
            Received amount: 18.00 USD
            Balance: 18.00 USD
            Date:4/5/2026 9:36:59 PM;
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
    }
}
