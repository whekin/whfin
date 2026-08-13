package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsCancellationImporterTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Credo",
                type = FinancialGroupType.BANK,
                provider = "Credo",
            ),
        )
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
        db.paymentInstrumentDao().linkForAccount(account, "0001", PaymentInstrumentType.PHYSICAL_CARD)
        importer = SmsTransactionImporter(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `cancellation keeps the original row and records why it stopped counting`() = runBlocking {
        val imported = importer.import(PAYMENT, RECEIVED_AT)
        val transactionId = requireNotNull(imported.transactionId)

        val canceled = importer.import(CANCELLATION, RECEIVED_AT + 1_000)

        assertEquals(SmsDiagnosticOutcome.CANCELED, canceled.outcome)
        assertEquals(transactionId, canceled.transactionId)
        val transaction = requireNotNull(db.transactionDao().byId(transactionId))
        assertTrue(transaction.isVoided)
        assertNotNull(transaction.canceledBySmsExternalKey)
        assertEquals(1, db.transactionDao().allForIntegrity().size)

        val repeated = importer.import(CANCELLATION, RECEIVED_AT + 2_000)
        assertEquals(SmsDiagnosticOutcome.CANCELED, repeated.outcome)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
    }

    private companion object {
        const val RECEIVED_AT = 1_775_000_000_000
        val PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance: 567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val CANCELLATION = """
            Canceled operation
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            03/04/2026 20:48:05
        """.trimIndent()
    }
}
