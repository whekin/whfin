package dev.whekin.whfin.data.categorization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeferredCategoryReviewTest {
    @Test fun unarmedReminderDoesNotQueryTheLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("deferred_category_idle_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var reads = 0
        val source = flow {
            reads++
            emit(listOf(CategoryProposals.Proposal(CategoryCatalog.all.single { it.icon == "PedalBike" }, 3)))
        }
        try {
            val review = DeferredCategoryReview(preferences, source, scope)
            assertTrue(withTimeout(5_000) { review.pending.first { it != null } }.isNullOrEmpty())
            assertEquals(0, reads)
            review.arm()
            assertEquals(1, withTimeout(5_000) { review.pending.first { it?.isNotEmpty() == true } }?.size)
            assertEquals(1, reads)
        } finally {
            scope.cancel()
            preferences.edit().clear().commit()
        }
    }

    @Test fun importedUnfiledSpendingCreatesAndThenClearsARealDatabaseProposal() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            WhfinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val account = db.accountDao().insert(AccountEntity(name = "Bank", type = AccountType.BANK,
                currency = "GEL"))
            val merchant = db.merchantDao().insert(MerchantEntity(normalizedKey = "bike24",
                displayName = "Bike shop"))
            db.transactionDao().insert(TransactionEntity(accountId = account, amountMinor = -1000,
                currency = "GEL", occurredAt = 1_700_000_000_000, merchantId = merchant,
                source = TxSource.STATEMENT, status = TxStatus.CONFIRMED))
            assertEquals("PedalBike", withTimeout(5_000) {
                CategoryProposals.observe(db).first { it.isNotEmpty() }
            }.single().definition.icon)
            db.categoryDao().insert(CategoryEntity(name = "Bike", kind = CategoryKind.EXPENSE,
                icon = "PedalBike", color = 0))
            assertTrue(withTimeout(5_000) { CategoryProposals.observe(db).first() }.isEmpty())
        } finally { db.close() }
    }

    @Test fun activeOrInterruptedBankReadDefersTheReminder() {
        val proposal = CategoryProposals.Proposal(CategoryCatalog.all.single { it.icon == "PedalBike" }, 3)
        assertTrue(deferredCategoryReviewVisible(listOf(proposal), emptyList()))
        assertTrue(!deferredCategoryReviewVisible(listOf(proposal), listOf(
            BankSyncStatus("Credo", 1, active = true, phase = SyncPhase.HISTORY))))
        assertTrue(!deferredCategoryReviewVisible(listOf(proposal), listOf(
            BankSyncStatus("Credo", 1, active = false, phase = SyncPhase.INTERRUPTED))))
    }
    @Test fun onlyNewCategoryTypesReappearAfterAReviewAndProcessRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("deferred_category_review_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bike = CategoryProposals.Proposal(CategoryCatalog.all.single { it.icon == "PedalBike" }, 3)
        val tech = CategoryProposals.Proposal(CategoryCatalog.all.single { it.icon == "Devices" }, 2)
        val proposals = MutableStateFlow(emptyList<CategoryProposals.Proposal>())
        try {
            val first = DeferredCategoryReview(preferences, proposals, scope)
            assertEquals(emptyList<CategoryProposals.Proposal>(),
                withTimeout(5_000) { first.pending.first { it != null } })
            first.arm()
            // The user opened the step before the bank returned any proposals.
            first.markSeen(emptyList())
            proposals.value = listOf(bike)
            assertEquals(listOf(bike), withTimeout(5_000) { first.pending.first { it == listOf(bike) } })
            first.markSeen(listOf(bike))
            assertTrue(withTimeout(5_000) { first.pending.first { it?.isEmpty() == true } }.isNullOrEmpty())

            val recreated = DeferredCategoryReview(preferences, proposals, scope)
            assertTrue(withTimeout(5_000) { recreated.pending.first { it != null } }.isNullOrEmpty())
            proposals.value = listOf(bike, tech)
            assertEquals(listOf(tech), withTimeout(5_000) { recreated.pending.first { it == listOf(tech) } })
            recreated.resetAfterRestore()
            assertTrue(withTimeout(5_000) { recreated.pending.first { it?.isEmpty() == true } }.isNullOrEmpty())
        } finally {
            scope.cancel()
            preferences.edit().clear().commit()
        }
    }
}
