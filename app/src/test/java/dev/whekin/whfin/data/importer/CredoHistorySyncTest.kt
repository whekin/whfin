package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.credo.*
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoHistorySyncTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026, 9, 9)
    private val remote = CredoRemoteAccount("GE00CD0000000000000001", "GEL", 10, "Current", "ACCOUNT")
    private val session = CredoSession("synthetic", null)
    private fun row(id: String? = null, amount: Long = -500) = StatementRow(day, StatementOperation.CARD_PAYMENT,
        "საბარათე ოპერაცია", amount, if (id == null) 9500 else null,
        "გადახდა - EXAMPLE CAFE 5.00 GEL 09.09.2026", null, null, "EXAMPLE CAFE", day,
        bankTransactionId = id?.let(CredoRowIdentity::mobileId))
    private fun statement(rows: List<StatementRow>) = BankStatement(BankProfile("Credo", "Credo"), remote.accountNumber,
        remote.currency, day, day, null, null, rows)
    private suspend fun file(rows: List<StatementRow> = listOf(row())): ImportPlan {
        val statement = statement(rows).copy(openingBalanceMinor = 10000, closingBalanceMinor = 9500)
        val resolved = BankLedgerResolver(db).resolve(statement)
        val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, resolved.account, resolved.created, resolved.adopted)
        ImportApplier(db, LedgerCalendar.zone).apply(plan, resolved.account, "synthetic.xlsx", StatementImportOrigin.FILE)
        return plan
    }
    private fun gateway(rows: List<StatementRow>) = object : CredoGateway {
        override suspend fun initiateLogin(credentials: CredoCredentials): CredoLoginChallenge = error("unused")
        override suspend fun sendOtp(operationId: String) = Unit
        override suspend fun confirmLogin(challenge: CredoLoginChallenge, username: String, otp: String?): CredoSession = error("unused")
        override suspend fun accounts(session: CredoSession) = listOf(remote)
        override suspend fun downloadStatement(session: CredoSession, account: CredoRemoteAccount, fromIso: String, toIso: String): ByteArray = error("API must not export")
        override suspend fun history(session: CredoSession, account: CredoRemoteAccount, from: LocalDate, to: LocalDate) = rows
    }
    private suspend fun sync(rows: List<StatementRow>) = CredoHistorySync(db).sync(gateway(rows), session, remote, day, day)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun legacySettlementWithoutPeerNameDoesNotBlockBankFile() = runBlocking {
        val transfer = row(amount = -1000).copy(operation = StatementOperation.OWN_TRANSFER, merchantRaw = null,
            beneficiaryName = "EXAMPLE OWNER", beneficiaryAccount = "GE00CD0000000000000002", description = "Personal Transfer")
        file(listOf(transfer))
        val account = db.accountDao().allActive().single()
        val before = db.transactionDao().allStatementRows(account.id).single()
        db.transactionDao().update(before.copy(rawCounterparty = null))
        assertEquals(0, file(listOf(transfer)).inserted)
        assertEquals(before.id, db.transactionDao().allStatementRows(account.id).single().id)
        assertTrue(runCatching { file(listOf(transfer.copy(beneficiaryAccount = "GE00CD0000000000000003"))) }.exceptionOrNull() is InvalidStatementException)
    }
    @Test fun conflictingOwnerCategoriesStopConsolidationBeforeWriting() = runBlocking {
        val account = BankLedgerResolver(db).resolve(statement(emptyList())).account
        val at = day.atTime(12, 0).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
        for ((index, amount) in listOf(-3990L, -210L).withIndex()) {
            val category = db.categoryDao().insert(CategoryEntity(name = "Choice $index", kind = CategoryKind.EXPENSE, icon = "Home", color = 0))
            val id = db.transactionDao().insert(TransactionEntity(accountId = account.id, currency = "GEL", amountMinor = amount,
                occurredAt = at + index, categoryId = category, rawCounterparty = "EXAMPLE RIDE", source = TxSource.SMS,
                status = TxStatus.CONFIRMED, externalKey = "sms|synthetic-category-$index"))
            db.smsDiagnosticDao().insert(SmsDiagnosticEntity(externalKey = "sms|synthetic-category-$index", kind = SmsDiagnosticKind.CARD_PAYMENT,
                outcome = SmsDiagnosticOutcome.IMPORTED, receivedAt = at, occurredAt = at, amountMinor = -amount, currency = "GEL",
                cardLast4 = "0001", transactionId = id, accountId = account.id, updatedAt = at))
        }
        val before = db.transactionDao().allForIntegrity()
        val charge = row(amount = -4200).copy(merchantRaw = "EXAMPLE RIDE", description = "EXAMPLE RIDE", balanceAfterMinor = 5800)
        assertTrue(runCatching { file(listOf(charge)) }.exceptionOrNull() is InvalidStatementException)
        assertEquals(before, db.transactionDao().allForIntegrity())
    }
    @Test fun apiIdentityDoesNotHideAnUnreconciledSmsOrEraseFileBalance() = runBlocking {
        file()
        sync(listOf(row("a")))
        val account = db.accountDao().allActive().single()
        val id = db.transactionDao().insert(TransactionEntity(accountId = account.id, currency = "GEL", amountMinor = -500,
            occurredAt = day.atTime(12, 0).atZone(LedgerCalendar.zone).toInstant().toEpochMilli(),
            rawCounterparty = "EXAMPLE CAFE", source = TxSource.SMS, status = TxStatus.CONFIRMED, externalKey = "sms|synthetic-late"))
        assertEquals(1, sync(listOf(row("a")))!!.reconciled)
        val active = db.transactionDao().activeForAccount(account.id).filter { it.source != TxSource.ADJUSTMENT }
        assertEquals(id, active.single().id)
        assertEquals(9500L, active.single().balanceAfterMinor)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
    }
    @Test fun completeStatementRetiresLegacyBalanceRevisionAndKeepsOriginalCategory() = runBlocking {
        file()
        val account = db.accountDao().allActive().single()
        val original = db.transactionDao().allStatementRows(account.id).single()
        val category = db.categoryDao().insert(CategoryEntity(name = "Chosen", kind = CategoryKind.EXPENSE, icon = "Home", color = 0))
        db.transactionDao().update(original.copy(categoryId = category))
        val revised = row().copy(balanceAfterMinor = 8500)
        val copyId = db.transactionDao().insert(original.copy(id = 0, externalKey = StatementIdentity.of(statement(listOf(revised))).rowKey(revised), balanceAfterMinor = 8500))
        assertTrue(file().reconciled > 0)
        val active = db.transactionDao().allStatementRows(account.id).filterNot { it.isVoided }
        assertEquals(listOf(original.id), active.map { it.id })
        assertEquals(category, active.single().categoryId)
        assertEquals(original.id, db.transactionDao().byId(copyId)!!.mergedIntoTransactionId)
        assertTrue(file().isNoOp)
        assertFalse(StatementLedgerAudit(db, LedgerCalendar.zone).needsReview(statement(listOf(row())).copy(openingBalanceMinor = 10000, closingBalanceMinor = 9500), account.id))
    }
    @Test fun optionalLocalStatementRoundTrip() = runBlocking {
        val path = System.getenv("WHFIN_STATEMENT_CHECK")
        Assume.assumeTrue("Optional private fixture is supplied outside the repository", path != null)
        val local = java.io.File(requireNotNull(path))
        System.getenv("WHFIN_RESTORE_CHECK")?.let { backup ->
            java.io.File(backup).inputStream().use { dev.whekin.whfin.data.backup.WhfinBackupManager(db).restore(it) }
        }
        val previouslyMerged = db.transactionDao().allForIntegrity().count { it.mergedIntoTransactionId != null }
        val importer = StatementImporter(db)
        val first = local.inputStream().use { importer.import(it, "local.xlsx") }
        if (System.getenv("WHFIN_RESTORE_CHECK") == null || System.getenv("WHFIN_REQUIRE_BALANCE") == "1") assertFalse("Statement must reconcile the restored ledger", first.balanceNeedsReview)
        System.getenv("WHFIN_EXPECT_MERGES")?.toInt()?.let { expected ->
            assertEquals(expected, db.transactionDao().allForIntegrity().count { it.mergedIntoTransactionId != null } - previouslyMerged)
        }
        val before = db.transactionDao().activeForAccount(first.accountId)
        suspend fun peers(rows: List<TransactionEntity>) = rows.associate { tx ->
            tx.id to tx.transferGroupId?.let { group -> db.transactionDao().byTransferGroup(group).map { it.id }.sorted() }
        }
        val beforePeers = peers(before)
        val repeated = local.inputStream().use { importer.import(it, "local.xlsx") }
        assertEquals(0, repeated.inserted)
        assertEquals(0, repeated.reconciled)
        assertEquals(first.balanceNeedsReview, repeated.balanceNeedsReview)
        val after = db.transactionDao().activeForAccount(first.accountId)
        // Derived groups may be rebuilt with new surrogate IDs; their membership must not change.
        assertEquals(before.map { it.copy(transferGroupId = null) }, after.map { it.copy(transferGroupId = null) })
        assertEquals(beforePeers, peers(after))
    }
    @Test fun statementThenTransferWithFutureDateKeepsBankMovement() = runBlocking {
        val transfer = row(amount = -3000).copy(operation = StatementOperation.TRANSFER_OUT,
            merchantRaw = null, description = "Personal transfer", beneficiaryName = "EXAMPLE PERSON", balanceAfterMinor = 7000)
        file(listOf(transfer))
        val account = db.accountDao().allActive().single()
        val sms = dev.whekin.whfin.data.sms.SmsTransactionImporter(db)
        val received = day.atTime(10, 0).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
        sms.import("Outgoing transfer\nAmount: 30.00 GEL;\nBalance: 70.00 GEL\nDate:9/13/2026 10:00:00 AM", received)
        assertEquals(1, db.transactionDao().activeForAccount(account.id).count { it.source != TxSource.ADJUSTMENT })
    }
    @Test fun equalAmountReorderingDoesNotSwapMerchantIdentities() = runBlocking {
        val a = row()
        val b = row().copy(merchantRaw = "SECOND STORE", description = "SECOND STORE", balanceAfterMinor = 9000)
        file(listOf(a, b))
        val account = db.accountDao().allActive().single()
        val before = db.transactionDao().allStatementRows(account.id)
        val revised = listOf(b.copy(balanceAfterMinor = 9500), a.copy(balanceAfterMinor = 9000))
        assertEquals(0, file(revised).inserted)
        for (tx in before) {
            val after = requireNotNull(db.transactionDao().byId(tx.id))
            assertEquals(tx.rawCounterparty, after.rawCounterparty)
            assertEquals(if (tx.rawCounterparty == "SECOND STORE") 9500L else 9000L, after.balanceAfterMinor)
        }
        assertTrue(file(revised).isNoOp)
    }
    @Test fun bankBalanceAuditUsesCutoffAndDoesNotChangeMoney() = runBlocking {
        file()
        val account = db.accountDao().allActive().single()
        val doc = statement(listOf(row())).copy(openingBalanceMinor = 10000, closingBalanceMinor = 9500)
        val audit = StatementLedgerAudit(db, LedgerCalendar.zone)
        assertFalse(audit.needsReview(doc, account.id))
        val after = day.plusDays(1).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
        db.transactionDao().insert(TransactionEntity(accountId = account.id, currency = "GEL", amountMinor = -500,
            occurredAt = after, source = TxSource.SMS, status = TxStatus.CONFIRMED))
        assertFalse(audit.needsReview(doc, account.id))
        db.transactionDao().insert(TransactionEntity(accountId = account.id, currency = "GEL", amountMinor = -200,
            occurredAt = after - 1000, source = TxSource.SMS, status = TxStatus.CONFIRMED))
        val before = db.transactionDao().allForIntegrity()
        assertTrue(audit.needsReview(doc, account.id))
        assertEquals(before, db.transactionDao().allForIntegrity())
    }

    @Test fun statementBeforeRideAndTipDoesNotAddExpenses() = runBlocking {
        val charge = row(amount = -4200).copy(merchantRaw = "EXAMPLE RIDE", description = "EXAMPLE RIDE", balanceAfterMinor = 5800)
        file(listOf(charge))
        val account = db.accountDao().allActive().single()
        db.paymentInstrumentDao().linkForAccount(account, "0001", PaymentInstrumentType.PHYSICAL_CARD)
        val sms = dev.whekin.whfin.data.sms.SmsTransactionImporter(db)
        for ((amount, time) in listOf("39.90" to "18:20:00", "2.10" to "19:10:00"))
            sms.import("Payment: $amount GEL Card N ****0001 EXAMPLE RIDE>Tbilisi GE Balance: 50.00 GEL 09/09/2026 $time")
        assertEquals(1, db.transactionDao().allForIntegrity().count { !it.isVoided && it.source != TxSource.ADJUSTMENT })
        assertEquals(-4200L, db.transactionDao().allStatementRows(account.id).single().amountMinor)
    }
    @Test fun repeatedSmallChargesSurviveReorderedStatementCohorts() = runBlocking {
        val original = listOf(row(amount = -100).copy(balanceAfterMinor = 9900), row(amount = -100).copy(balanceAfterMinor = 9800))
        file(original)
        val account = db.accountDao().allActive().single()
        val ids = db.transactionDao().allStatementRows(account.id).map { it.id }
        val revised = listOf(original[0].copy(balanceAfterMinor = 9400), original[1].copy(balanceAfterMinor = 9300))
        assertEquals(0, file(revised).inserted)
        assertEquals(ids, db.transactionDao().allStatementRows(account.id).map { it.id })
        assertTrue(file(revised).isNoOp)
        assertTrue(file(original).isNoOp)
    }
    @Test fun twoRealOwnTransfersRemainTwoWithBothSidesReconciled() = runBlocking {
        val first = BankLedgerResolver(db).resolve(statement(emptyList())).account
        val peerStatement = statement(emptyList()).copy(accountIban = "GE00CD0000000000000002")
        val second = BankLedgerResolver(db).resolve(peerStatement).account
        for (minute in listOf(10, 40)) {
            val group = db.transactionDao().insertTransferGroup(TransferGroupEntity(type = TransferGroupType.TRANSFER, createdAt = 1))
            for ((account, amount) in listOf(first to -30000L, second to 30000L))
                db.transactionDao().insert(TransactionEntity(accountId = account.id, amountMinor = amount, currency = "GEL",
                    occurredAt = day.atTime(20, minute).atZone(LedgerCalendar.zone).toInstant().toEpochMilli(),
                    source = TxSource.SMS, status = TxStatus.CONFIRMED, isTransfer = true, transferGroupId = group,
                    externalKey = "sms|synthetic-${account.id}-$minute"))
        }
        for ((account, peer, amount) in listOf(Triple(first, second, -30000L), Triple(second, first, 30000L))) {
            val rows = listOf(70000L, 40000L).map { balance -> row(amount = amount).copy(operation = StatementOperation.OWN_TRANSFER,
                merchantRaw = null, description = "Own transfer", beneficiaryAccount = peer.iban, balanceAfterMinor = balance) }
            val doc = statement(rows).copy(accountIban = requireNotNull(account.iban))
            val plan = ImportPlanner(db, LedgerCalendar.zone).plan(doc, account, false, false)
            assertEquals(0, plan.inserted)
            assertEquals(2, plan.reconciled)
            ImportApplier(db, LedgerCalendar.zone).apply(plan, account, "synthetic.xlsx", StatementImportOrigin.FILE)
        }
        val active = db.transactionDao().allForIntegrity().filterNot { it.isVoided }
        assertEquals(4, active.size)
        assertTrue(active.all { it.source == TxSource.STATEMENT && it.isTransfer })
        assertEquals(2, active.map { it.transferGroupId }.distinct().size)
        assertEquals(0L, active.sumOf { it.amountMinor })
    }
    @Test fun transferWithFuturePrintedDateUsesReceiptEvidenceAndKeepsOneMovement() = runBlocking {
        val account = BankLedgerResolver(db).resolve(statement(emptyList())).account
        val sms = dev.whekin.whfin.data.sms.SmsTransactionImporter(db)
        val received = day.atTime(10, 0).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
        val imported = sms.import("Outgoing transfer\nAmount: 30.00 GEL;\nBalance: 70.00 GEL\nDate:9/13/2026 10:00:00 AM", received)
        if (imported.outcome != SmsDiagnosticOutcome.IMPORTED)
            sms.resolveDiagnostic(requireNotNull(imported.diagnosticId), account.id)
        val transfer = row(amount = -3000).copy(operation = StatementOperation.TRANSFER_OUT,
            merchantRaw = null, description = "Personal transfer", beneficiaryName = "EXAMPLE PERSON", balanceAfterMinor = 7000)
        file(listOf(transfer))
        assertEquals(1, db.transactionDao().allForIntegrity().count { !it.isVoided && it.source != TxSource.ADJUSTMENT })
        assertTrue(file(listOf(transfer)).isNoOp)
    }
    @Test fun rideAndTipBecomeOneBookedChargeAndRepeatedFileIsIdempotent() = runBlocking {
        val account = BankLedgerResolver(db).resolve(statement(emptyList())).account
        val sms = dev.whekin.whfin.data.sms.SmsTransactionImporter(db)
        for ((amount, time) in listOf("39.90" to "18:20:00", "2.10" to "19:10:00")) {
            val imported = sms.import("Payment: $amount GEL Card N ****0001 EXAMPLE RIDE>Tbilisi GE Balance: 50.00 GEL 09/09/2026 $time")
            if (imported.outcome != SmsDiagnosticOutcome.IMPORTED)
                sms.resolveDiagnostic(requireNotNull(imported.diagnosticId), account.id)
        }
        assertEquals(2, db.transactionDao().allForIntegrity().count { it.source == TxSource.SMS })
        val charge = row(amount = -4200).copy(merchantRaw = "EXAMPLE RIDE", description = "EXAMPLE RIDE", balanceAfterMinor = 5800)
        file(listOf(charge))
        val active = db.transactionDao().allForIntegrity().filter { !it.isVoided && it.source != TxSource.ADJUSTMENT }
        assertEquals(1, active.size)
        assertEquals(-4200L, active.single().amountMinor)
        assertEquals(2, db.smsDiagnosticDao().forTransaction(active.single().id).size)
        assertTrue(file(listOf(charge)).isNoOp)
        for ((amount, time) in listOf("39.90" to "18:20:00", "2.10" to "19:10:00"))
            assertEquals(active.single().id, sms.import("Payment: $amount GEL Card N ****0001 EXAMPLE RIDE>Tbilisi GE Balance: 50.00 GEL 09/09/2026 $time").transactionId)
        assertEquals(2, db.smsDiagnosticDao().forTransaction(active.single().id).size)
    }
    @Test fun peerAccountDisambiguatesSameDaySameAmountTransfers() = runBlocking {
        val first = row().copy(operation = StatementOperation.OWN_TRANSFER, description = "Own transfer", merchantRaw = null,
            beneficiaryName = "Owner", beneficiaryAccount = "GE00CD0000000000000002")
        val second = first.copy(beneficiaryAccount = "GE00CD0000000000000003", balanceAfterMinor = 9000)
        file(listOf(first, second))
        val remoteRows = listOf(first.copy(bankTransactionId = CredoRowIdentity.mobileId("a"), balanceAfterMinor = null, description = "Transfer between own accounts"),
            second.copy(bankTransactionId = CredoRowIdentity.mobileId("b"), balanceAfterMinor = null, description = "Transfer between own accounts"))
        assertEquals(2, sync(remoteRows)!!.reconciled)
        assertTrue(sync(remoteRows)!!.isNoOp)
        assertEquals(2, db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).size)
    }

    @Test fun firstLedgerRequestsAutomaticStatementSeed() = runBlocking {
        assertNull(sync(listOf(row("a"))))
        assertTrue(db.accountDao().allActive().isEmpty())
    }
    @Test fun fileThenApiKeepsMoneyBalanceAndIdentityAcrossRepeatedImports() = runBlocking {
        file()
        val before = db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single()
        assertEquals(1, sync(listOf(row("a")))!!.reconciled)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
        assertTrue(file().isNoOp)
        val after = db.transactionDao().byId(before.id)!!
        assertEquals(9500L, after.balanceAfterMinor)
        assertTrue(CredoRowIdentity.hasMobile(after.externalKey, CredoRowIdentity.mobileId("a")))
        assertEquals(9500L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
    @Test fun smsThenTwoOverlappingStatementsKeepOriginalPurchase() = runBlocking {
        val account = BankLedgerResolver(db).resolve(statement(emptyList())).account
        val sms = dev.whekin.whfin.data.sms.SmsTransactionImporter(db)
        val body = "Payment: 5.00 GEL Card N ****0001 EXAMPLE CAFE>Tbilisi GE Balance: 15.23 GEL 09/09/2026 18:20:00"
        val imported = sms.import(body)
        if (imported.outcome != SmsDiagnosticOutcome.IMPORTED) sms.resolveDiagnostic(requireNotNull(imported.diagnosticId), account.id)
        val before = db.transactionDao().allForIntegrity().single { it.source == TxSource.SMS }
        val category = db.categoryDao().insert(CategoryEntity(name = "Owner category", kind = CategoryKind.EXPENSE, icon = "Home", color = 0))
        db.transactionDao().update(before.copy(categoryId = category))
        val posted = row().copy(postedDate = day.plusDays(1))
        assertEquals(1, file(listOf(posted)).reconciled)
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(before.id)!!.source)
        assertEquals(0, file(listOf(posted.copy(balanceAfterMinor = 9400))).inserted)
        val after = db.transactionDao().allStatementRows(account.id).single()
        assertEquals(before.id, after.id)
        assertEquals(category, after.categoryId)
        assertEquals(9400L, after.balanceAfterMinor)
        assertTrue(file(listOf(posted.copy(balanceAfterMinor = 9400))).isNoOp)
    }
    @Test fun repeatedFileWithRevisedRunningBalanceKeepsOnePurchase() = runBlocking {
        file()
        val account = db.accountDao().allActive().single()
        val before = db.transactionDao().allStatementRows(account.id).single()
        val revised = row().copy(balanceAfterMinor = 9400)
        val plan = file(listOf(revised))
        assertEquals(0, plan.inserted)
        val after = db.transactionDao().allStatementRows(account.id).single()
        assertEquals(before.id, after.id)
        assertEquals(9400L, after.balanceAfterMinor)
        assertTrue(file(listOf(revised)).isNoOp)
        assertEquals(1, db.transactionDao().allStatementRows(account.id).size)
    }
    @Test fun reorderedFilePreservesBothPurchasesAndOwnerCategory() = runBlocking {
        val a = row()
        val b = row(amount = -200).copy(description = "SECOND STORE", merchantRaw = "SECOND STORE", balanceAfterMinor = 9300)
        file(listOf(a, b))
        val account = db.accountDao().allActive().single()
        val original = db.transactionDao().allStatementRows(account.id)
        val category = db.categoryDao().insert(CategoryEntity(name = "Owner choice", kind = CategoryKind.EXPENSE, icon = "Home", color = 0))
        val first = original.first { it.amountMinor == -500L }
        db.transactionDao().update(first.copy(categoryId = category))
        val revised = listOf(b.copy(balanceAfterMinor = 9800), a.copy(balanceAfterMinor = 9300))
        assertEquals(0, file(revised).inserted)
        assertEquals(original.map { it.id }.toSet(), db.transactionDao().allStatementRows(account.id).map { it.id }.toSet())
        assertEquals(category, db.transactionDao().byId(first.id)!!.categoryId)
        assertTrue(file(revised).isNoOp)
        assertTrue(file(listOf(a, b)).isNoOp)
    }
    @Test fun genuineIdenticalPurchaseWithExistingKeyIsStillInserted() = runBlocking {
        file()
        val second = row().copy(balanceAfterMinor = 9000)
        assertEquals(1, file(listOf(row(), second)).inserted)
        assertEquals(2, db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).size)
        assertTrue(file(listOf(row(), second)).isNoOp)
    }
    @Test fun ambiguousRevisedTwinsStopBeforeWriting() = runBlocking {
        file()
        val before = db.transactionDao().allForIntegrity()
        val rows = listOf(row().copy(balanceAfterMinor = 9400), row().copy(balanceAfterMinor = 8900))
        assertTrue(runCatching { file(rows) }.exceptionOrNull() is InvalidStatementException)
        assertEquals(before, db.transactionDao().allForIntegrity())
    }
    @Test fun revisedFileKeepsApiAliasAndWithdrawnDecision() = runBlocking {
        file()
        sync(listOf(row("a")))
        val account = db.accountDao().allActive().single()
        val before = db.transactionDao().allStatementRows(account.id).single()
        file(listOf(row().copy(balanceAfterMinor = 9400)))
        val changed = db.transactionDao().byId(before.id)!!
        assertEquals(before.externalKey, changed.externalKey)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
        db.transactionDao().update(changed.copy(isVoided = true))
        assertTrue(file(listOf(row().copy(balanceAfterMinor = 9300))).isNoOp)
        assertTrue(db.transactionDao().byId(before.id)!!.isVoided)
    }
    @Test fun apiOnlyMovementIsUpgradedByLaterFileWithoutDuplicating() = runBlocking {
        file(emptyList())
        assertEquals(1, sync(listOf(row("a")))!!.inserted)
        val id = db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single().id
        assertEquals(1, file().reconciled)
        assertEquals(id, db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single().id)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
    }
    @Test fun ambiguousTwinAndChangedFileBackedAmountAreAtomic() = runBlocking {
        file()
        assertTrue(runCatching { sync(listOf(row("a"), row("b"))) }.exceptionOrNull() is InvalidStatementException)
        assertFalse(db.transactionDao().allForIntegrity().any { "|credoapi|" in it.externalKey.orEmpty() })
        sync(listOf(row("a")))
        assertTrue(runCatching { sync(listOf(row("a", -600))) }.exceptionOrNull() is InvalidStatementException)
        assertEquals(9500L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
}
