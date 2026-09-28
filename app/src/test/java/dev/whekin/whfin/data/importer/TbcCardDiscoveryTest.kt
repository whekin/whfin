package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.tbc.TbcCardCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcCardDiscoveryTest {
    @Test fun bankSuffixLinksAllCurrencyLedgersAfterTheyExistWithoutSettingTypeOrPrimary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            WhfinDatabase::class.java).allowMainThreadQueries().build()
        val candidate = TbcCardCandidate("GE00TB0000000000000001", "1234")
        try {
            linkTbcDiscoveredCards(db, listOf(candidate))
            assertTrue(db.paymentInstrumentDao().allActive().isEmpty())
            val group = db.financialGroupDao().insert(FinancialGroupEntity(name = "TBC",
                type = FinancialGroupType.BANK, provider = "TBC"))
            val gel = db.accountDao().insert(AccountEntity(name = "Everyday", type = AccountType.BANK,
                groupId = group, iban = candidate.iban, currency = "GEL"))
            val usd = db.accountDao().insert(AccountEntity(name = "Everyday", type = AccountType.BANK,
                groupId = group, iban = candidate.iban, currency = "USD"))
            linkTbcDiscoveredCards(db, listOf(candidate, candidate))
            val instrument = db.paymentInstrumentDao().allActive().single()
            assertEquals(PaymentInstrumentType.UNCLASSIFIED_CARD, instrument.type)
            assertEquals(false, instrument.isPrimary)
            assertEquals(listOf(gel, usd).toSet(), db.paymentInstrumentDao().allLinks()
                .filter { it.instrumentId == instrument.id }.map { it.accountId }.toSet())
            linkTbcDiscoveredCards(db, listOf(candidate))
            assertEquals(1, db.paymentInstrumentDao().allActive().size)
        } finally { db.close() }
    }
}
