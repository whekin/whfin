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
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
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

        val resolved = importer.resolveDiagnostic(
            diagnosticId,
            accountId,
            PaymentInstrumentType.PHYSICAL_CARD,
        )
        assertEquals(SmsDiagnosticOutcome.IMPORTED, resolved.outcome)
        assertNotNull(resolved.transactionId)
        assertEquals(accountId, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)

        val repeated = importer.import(CARD_PAYMENT, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, repeated.outcome)
        assertEquals(1, transactionCount())
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
        val account = AccountEntity(
            id = db.accountDao().insert(
                AccountEntity(name = "Main GEL", type = AccountType.BANK, groupId = groupId, currency = "GEL"),
            ),
            name = "Main GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
        )

        db.paymentInstrumentDao().linkForAccount(account, "0001", PaymentInstrumentType.VIRTUAL_CARD)

        assertTrue(db.paymentInstrumentDao().configuredCount() > 0)
        assertEquals(account.id, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        assertEquals(SmsDiagnosticOutcome.IMPORTED, importer.import(CARD_PAYMENT, RECEIVED_AT).outcome)
    }

    private fun transactionCount(): Int = db.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM transactions")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val RECEIVED_AT = 1_775_000_000_000
        val CARD_PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance: 567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val OUTGOING_TRANSFER = """
            Outgoing transfer
            Amount: 100.00 GEL;
            Balance: 1234.56 GEL
            Date:4/5/2026 10:43:19 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
    }
}
