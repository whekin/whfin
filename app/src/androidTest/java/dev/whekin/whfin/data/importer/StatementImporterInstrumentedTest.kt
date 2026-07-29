package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
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
}
