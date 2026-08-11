package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook.Row
import dev.whekin.whfin.data.statement.UnsupportedStatementException
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared pipeline behind the bank adapter boundary: account creation, dedup and own-movement
 * handling must hold for any bank, so the harness only feeds it a generated statement.
 */
@RunWith(AndroidJUnit4::class)
class StatementImporterInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private lateinit var importer: StatementImporter

    private val cardPayment = Row(
        date = LocalDate.of(2026, 1, 12),
        operation = "საბარათე ოპერაცია",
        debit = "7.14",
        balance = "92.86",
        description = "გადახდა - NIKORA 7.14 GEL 09.01.2026",
    )
    private val conversion = Row(
        date = LocalDate.of(2026, 1, 14),
        operation = "უნაღდო კონვერტაცია",
        debit = "5.00",
        balance = "87.86",
        description = "currency exchange",
    )
    private val incoming = Row(
        date = LocalDate.of(2026, 1, 16),
        operation = "სხვა ბანკიდან ჩარიცხვა",
        credit = "3.00",
        balance = "90.86",
        description = "Incoming",
        beneficiaryName = "Synthetic Sender",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        importer = StatementImporter(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun import(vararg rows: Row) = importer.import(
        ByteArrayInputStream(
            SyntheticCredoWorkbook.build(
                openingBalance = "100.00",
                closingBalance = "90.86",
                rows = rows.toList(),
            ),
        ),
        fileName = "statement.xlsx",
    )

    private suspend fun importStatement(
        iban: String = SyntheticCredoWorkbook.IBAN,
        currency: String = "GEL",
        periodFrom: LocalDate,
        periodTo: LocalDate,
        opening: String,
        closing: String,
        rows: List<Row>,
    ) = importer.import(
        ByteArrayInputStream(
            SyntheticCredoWorkbook.build(
                iban = iban,
                currency = currency,
                periodFrom = periodFrom,
                periodTo = periodTo,
                openingBalance = opening,
                closingBalance = closing,
                rows = rows,
            ),
        ),
        fileName = "statement-$currency-$periodFrom.xlsx",
    )

    private fun workbook(vararg rows: Row) = SyntheticCredoWorkbook.build(
        openingBalance = "100.00",
        closingBalance = "90.86",
        rows = rows.toList(),
    )

    @Test
    fun aPreview_saysExactlyWhatTheImportWillDo() = runBlocking {
        val bytes = workbook(cardPayment, conversion, incoming)

        val preview = importer.preview(ByteArrayInputStream(bytes), "statement.xlsx")

        assertEquals(3, preview.totalRows)
        assertEquals(3, preview.inserted)
        assertEquals(0, preview.duplicates)
        assertEquals(LedgerEffect.CREATED, preview.ledgerEffect)
        assertTrue(preview.createsAccount)
        // Reading a file must not leave the account it describes behind.
        assertTrue(db.accountDao().allActive().isEmpty())

        val result = importer.import(ByteArrayInputStream(bytes), "statement.xlsx")

        assertEquals(preview.totalRows, result.totalRows)
        assertEquals(preview.inserted, result.inserted)
        assertEquals(preview.reconciled, result.reconciled)
    }

    @Test
    fun anUnknownBankLabelImportsSafelyAndIsReportedByPreviewAndResult() = runBlocking {
        val rawName = "სრულიად უცნობი ოპერაცია"
        val bytes = SyntheticCredoWorkbook.build(
            openingBalance = "100.00",
            closingBalance = "90.00",
            rows = listOf(
                Row(
                    date = LocalDate.of(2026, 1, 12),
                    operation = rawName,
                    debit = "10.00",
                    balance = "90.00",
                ),
            ),
        )

        val preview = importer.preview(ByteArrayInputStream(bytes), "statement.xlsx")
        val result = importer.import(ByteArrayInputStream(bytes), "statement.xlsx")

        assertEquals(setOf(rawName), preview.unmappedOperationNames)
        assertEquals(setOf(rawName), result.unmappedOperationNames)
        val imported = db.transactionDao().observeByAccount(result.accountId).first()
            .single { it.source == TxSource.STATEMENT }
        assertFalse(imported.isTransfer)
        assertEquals(-1_000L, imported.amountMinor)
    }

    @Test
    fun previewingAnAlreadyImportedFile_promisesNoChange() = runBlocking {
        val bytes = workbook(cardPayment, conversion, incoming)
        importer.import(ByteArrayInputStream(bytes), "statement.xlsx")

        val preview = importer.preview(ByteArrayInputStream(bytes), "statement.xlsx")

        assertEquals(LedgerEffect.UNCHANGED, preview.ledgerEffect)
        assertFalse(preview.createsAccount)
        assertEquals(0, preview.inserted)
        assertEquals(3, preview.duplicates)
        assertTrue(preview.changesNothing)

        val before = db.transactionDao().sumByAccount(db.accountDao().allActive().single().id)
        importer.import(ByteArrayInputStream(bytes), "statement.xlsx")
        assertEquals(before, db.transactionDao().sumByAccount(db.accountDao().allActive().single().id))
    }

    @Test
    fun previewingAStatementForAnSmsLedger_doesNotPromiseANewAccount() = runBlocking {
        // Adopting an IBAN-less ledger is not the mistake worth asking about; creating one is.
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )

        val preview = importer.preview(
            ByteArrayInputStream(workbook(cardPayment, conversion, incoming)),
            "statement.xlsx",
        )

        assertEquals(LedgerEffect.ADOPTED, preview.ledgerEffect)
        assertFalse(preview.createsAccount)
        assertFalse(preview.changesNothing)
        assertEquals("Everyday", preview.ledgerName)
        assertEquals(3, preview.inserted)
        // Still only a reading: the ledger keeps its missing IBAN until an import is accepted.
        assertNull(db.accountDao().allActive().single().iban)
    }

    @Test
    fun oneDraft_isClaimedByOnlyOneStatementLine() = runBlocking {
        // Two identical purchases at the same shop on the same day, but only one SMS draft: the
        // draft may confirm one of them, never both.
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = "Credo GEL",
                type = AccountType.BANK,
                groupId = db.financialGroupDao().insert(
                    FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "credo"),
                ),
                currency = "GEL",
                iban = SyntheticCredoWorkbook.IBAN,
            ),
        )
        db.transactionDao().insert(
            dev.whekin.whfin.data.db.TransactionEntity(
                accountId = accountId,
                amountMinor = -714,
                currency = "GEL",
                occurredAt = LocalDate.of(2026, 1, 9).atStartOfDay(java.time.ZoneId.of("Asia/Tbilisi"))
                    .toInstant().toEpochMilli() + 3_600_000,
                rawCounterparty = "NIKORA",
                status = TxStatus.PENDING,
                source = TxSource.SMS,
                externalKey = "sms|test|1",
            ),
        )

        val secondCoffee = cardPayment.copy(date = LocalDate.of(2026, 1, 13), balance = "85.72")
        val result = importer.import(
            ByteArrayInputStream(
                SyntheticCredoWorkbook.build(
                    openingBalance = "100.00",
                    closingBalance = "85.72",
                    rows = listOf(cardPayment, secondCoffee),
                ),
            ),
            fileName = "statement.xlsx",
        )

        assertEquals(1, result.reconciled)
        assertEquals(1, result.inserted)
        assertEquals(
            2,
            db.transactionDao().observeByAccount(accountId).first()
                .count { it.source == TxSource.STATEMENT },
        )
    }

    @Test
    fun midnightSmsOwnMovementReconcilesWithThePreviousBankPostingDay() = runBlocking {
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = "Credo GEL",
                type = AccountType.BANK,
                groupId = db.financialGroupDao().insert(
                    FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
                ),
                currency = "GEL",
                iban = SyntheticCredoWorkbook.IBAN,
            ),
        )
        val zone = java.time.ZoneId.of("Asia/Tbilisi")
        db.transactionDao().insert(
            dev.whekin.whfin.data.db.TransactionEntity(
                accountId = accountId,
                amountMinor = -28_886,
                currency = "GEL",
                occurredAt = LocalDate.of(2026, 8, 12).atStartOfDay(zone).toInstant().toEpochMilli() + 50 * 60_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                isTransfer = true,
                externalKey = "sms|midnight-conversion",
            ),
        )
        val statementRow = Row(
            date = LocalDate.of(2026, 8, 11),
            operation = "უნაღდო კონვერტაცია",
            debit = "288.86",
            balance = "211.14",
            description = "currency exchange",
        )

        val result = importStatement(
            periodFrom = LocalDate.of(2026, 8, 11),
            periodTo = LocalDate.of(2026, 8, 12),
            opening = "500.00",
            closing = "211.14",
            rows = listOf(statementRow),
        )

        assertEquals(1, result.reconciled)
        assertEquals(0, result.inserted)
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(1)?.source)
    }

    @Test
    fun repeatedImportRepairsAStatementThatPreviouslyLandedBesideItsSms() = runBlocking {
        val statementRow = Row(
            date = LocalDate.of(2026, 8, 11),
            operation = "უნაღდო კონვერტაცია",
            debit = "288.86",
            balance = "211.14",
            description = "currency exchange",
        )
        val first = importStatement(
            periodFrom = LocalDate.of(2026, 8, 11),
            periodTo = LocalDate.of(2026, 8, 12),
            opening = "500.00",
            closing = "211.14",
            rows = listOf(statementRow),
        )
        val originalStatement = db.transactionDao().observeByAccount(first.accountId).first()
            .single { it.source == TxSource.STATEMENT }
        val smsGroupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(
                type = TransferGroupType.CONVERSION,
                note = "Credo SMS conversion",
                createdAt = System.currentTimeMillis(),
            ),
        )
        val zone = java.time.ZoneId.of("Asia/Tbilisi")
        val smsId = db.transactionDao().insert(
            dev.whekin.whfin.data.db.TransactionEntity(
                accountId = first.accountId,
                amountMinor = -28_886,
                currency = "GEL",
                occurredAt = LocalDate.of(2026, 8, 12).atStartOfDay(zone).toInstant().toEpochMilli() + 50 * 60_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                isTransfer = true,
                transferGroupId = smsGroupId,
                externalKey = "sms|late-reconciliation",
            ),
        )

        val repaired = importStatement(
            periodFrom = LocalDate.of(2026, 8, 11),
            periodTo = LocalDate.of(2026, 8, 12),
            opening = "500.00",
            closing = "211.14",
            rows = listOf(statementRow),
        )

        assertEquals(1, repaired.reconciled)
        assertEquals(0, repaired.duplicates)
        assertTrue(db.transactionDao().byId(originalStatement.id)!!.isVoided)
        assertNull(db.transactionDao().byId(originalStatement.id)!!.externalKey)
        val canonical = db.transactionDao().byId(smsId)!!
        assertEquals(TxSource.STATEMENT, canonical.source)
        assertEquals(smsGroupId, canonical.transferGroupId)
        assertEquals(1, db.transactionDao().observeByAccount(first.accountId).first().count { it.amountMinor == -28_886L })
        assertEquals(21_114L, db.transactionDao().sumByAccount(first.accountId))
    }

    @Test
    fun firstImport_createsTheBankFromTheAdapterProfile() = runBlocking {
        val result = import(cardPayment, conversion, incoming)

        assertTrue(result.accountCreated)
        assertEquals(3, result.totalRows)
        assertEquals(3, result.inserted)
        assertEquals(0, result.duplicates)

        val account = db.accountDao().byId(result.accountId)!!
        assertEquals(SyntheticCredoWorkbook.IBAN, account.iban)
        assertEquals("GEL", account.currency)

        val group = db.financialGroupDao().byProvider(FinancialGroupType.BANK, "Credo")
        assertNotNull(group)
        assertEquals(group!!.id, account.groupId)
    }

    @Test
    fun repeatedImport_ofTheSameFile_addsNothing() = runBlocking {
        val first = import(cardPayment, conversion, incoming)
        val second = import(cardPayment, conversion, incoming)

        assertEquals(3, first.inserted)
        assertEquals(0, second.inserted)
        assertEquals(3, second.duplicates)
        assertEquals(first.accountId, second.accountId)
        assertTrue(!second.accountCreated)
    }

    @Test
    fun importedRows_carryStatementProvenanceAndOwnMovementFlags() = runBlocking {
        val result = import(cardPayment, conversion, incoming)
        val rows = db.transactionDao().observeByAccount(result.accountId).first()
            .sortedBy { it.occurredAt }

        // Row one is the opening balance carried by a freshly created ledger.
        val opening = rows.first()
        assertEquals(TxSource.ADJUSTMENT, opening.source)
        assertTrue(opening.isTransfer)

        val statementRows = rows.drop(1)
        assertEquals(3, statementRows.size)
        assertTrue(statementRows.all { it.source == TxSource.STATEMENT })
        assertTrue(statementRows.all { it.status == TxStatus.CONFIRMED })

        val payment = statementRows.first { it.amountMinor == -714L }
        assertEquals("NIKORA", payment.rawCounterparty)
        assertTrue(!payment.isTransfer)
        assertNotNull(payment.merchantId)

        val exchange = statementRows.first { it.amountMinor == -500L }
        assertTrue("conversion stays out of income and expenses", exchange.isTransfer)

        val credit = statementRows.first { it.amountMinor == 300L }
        assertTrue(!credit.isTransfer)
    }

    @Test
    fun ledgerBalance_matchesTheClosingBalanceOfTheStatement() = runBlocking {
        val result = import(cardPayment, conversion, incoming)

        assertEquals(9086L, db.transactionDao().sumByAccount(result.accountId))
    }

    @Test
    fun unknownFormat_isRejectedWithoutTouchingTheLedger() = runBlocking {
        assertThrows(UnsupportedStatementException::class.java) {
            runBlocking {
                importer.import(
                    ByteArrayInputStream("not a bank export".toByteArray()),
                    fileName = "notes.txt",
                )
            }
        }

        assertEquals(emptyList<Any>(), db.accountDao().allActive())
        assertNull(db.financialGroupDao().byProvider(FinancialGroupType.BANK, "Credo"))
    }

    @Test
    fun precreatedSmsLedger_isAdoptedByItsFirstStatement() = runBlocking {
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        val pendingAccountId = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
        )

        val result = import(cardPayment, conversion, incoming)

        assertEquals(pendingAccountId, result.accountId)
        assertTrue(!result.accountCreated)
        assertEquals(SyntheticCredoWorkbook.IBAN, db.accountDao().byId(pendingAccountId)?.iban)
        assertEquals(1, db.accountDao().allActive().size)
        assertEquals(9_086L, db.transactionDao().sumByAccount(pendingAccountId))
    }

    @Test
    fun importingOlderHistory_movesTheSingleOpeningAnchor() = runBlocking {
        val february = importStatement(
            periodFrom = LocalDate.of(2026, 2, 1),
            periodTo = LocalDate.of(2026, 2, 28),
            opening = "200.00",
            closing = "150.00",
            rows = listOf(Row(LocalDate.of(2026, 2, 10), "გადახდები", debit = "50.00", balance = "150.00")),
        )
        importStatement(
            periodFrom = LocalDate.of(2026, 1, 1),
            periodTo = LocalDate.of(2026, 1, 31),
            opening = "100.00",
            closing = "200.00",
            rows = listOf(Row(LocalDate.of(2026, 1, 15), "სხვა ბანკიდან ჩარიცხვა", credit = "100.00", balance = "200.00")),
        )

        val rows = db.transactionDao().observeByAccount(february.accountId).first()
        assertEquals(1, rows.count { it.source == TxSource.ADJUSTMENT && it.externalKey?.startsWith("opening|") == true })
        assertEquals(15_000L, db.transactionDao().sumByAccount(february.accountId))
        assertTrue(rows.single { it.source == TxSource.ADJUSTMENT }.externalKey!!.contains("|GEL|2026-01-01"))
    }

    @Test
    fun sameIbanRowsInDifferentCurrencies_haveDifferentIdentities() = runBlocking {
        val date = LocalDate.of(2026, 3, 5)
        val gel = importStatement(
            currency = "GEL",
            periodFrom = LocalDate.of(2026, 3, 1),
            periodTo = LocalDate.of(2026, 3, 31),
            opening = "100.00",
            closing = "90.00",
            rows = listOf(Row(date, "გადახდები", debit = "10.00", balance = "90.00")),
        )
        val usd = importStatement(
            currency = "USD",
            periodFrom = LocalDate.of(2026, 3, 1),
            periodTo = LocalDate.of(2026, 3, 31),
            opening = "100.00",
            closing = "90.00",
            rows = listOf(Row(date, "გადახდები", debit = "10.00", balance = "90.00")),
        )

        assertTrue(gel.accountId != usd.accountId)
        val keys = listOf(gel.accountId, usd.accountId).flatMap { accountId ->
            db.transactionDao().externalKeysForAccount(accountId).filter { it.startsWith("stmt|") }
        }
        assertEquals(2, keys.distinct().size)
        assertTrue(keys.any { it.contains("|GEL|") })
        assertTrue(keys.any { it.contains("|USD|") })
    }

    @Test
    fun brokenBalanceChain_isRejectedWithoutTouchingTheLedger() = runBlocking {
        assertThrows(InvalidStatementException::class.java) {
            runBlocking {
                importStatement(
                    periodFrom = LocalDate.of(2026, 4, 1),
                    periodTo = LocalDate.of(2026, 4, 30),
                    opening = "100.00",
                    closing = "90.00",
                    rows = listOf(Row(LocalDate.of(2026, 4, 2), "გადახდები", debit = "10.00", balance = "91.00")),
                )
            }
        }

        assertTrue(db.accountDao().allActive().isEmpty())
        assertNull(db.financialGroupDao().byProvider(FinancialGroupType.BANK, "Credo"))
    }
}
