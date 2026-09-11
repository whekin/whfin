package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.statement.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class CredoDuplicateReviewTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026,9,4)
    private val iban = "GE00CD0000000000000001"
    @Before fun setup() = runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WhfinDatabase::class.java).allowMainThreadQueries().build()
        val group=db.financialGroupDao().insert(FinancialGroupEntity(name="Credo",type=FinancialGroupType.BANK,provider="Credo"))
        db.accountDao().insert(AccountEntity(groupId=group,id=1,name="Synthetic",type=AccountType.BANK,currency="GEL",iban=iban))
        val category=db.categoryDao().insert(CategoryEntity(name="Owner category",kind=CategoryKind.EXPENSE,icon="Home",color=0))
        val row = TransactionEntity(id=1,accountId=1,amountMinor=-12345,currency="GEL",occurredAt=LedgerCalendar.startOfDay(day),
            postedAt=LedgerCalendar.startOfDay(day.plusDays(1)),rawCounterparty="EXAMPLE STORE",note="Purchase EXAMPLE STORE",
            categoryId=category,status=TxStatus.CONFIRMED,source=TxSource.STATEMENT,balanceAfterMinor=20000,
            externalKey="stmt|$iban|GEL|2026-09-05|-12345|20000|1",createdAt=1)
        db.transactionDao().insert(row)
        db.transactionDao().insert(row.copy(id=2,createdAt=2,balanceAfterMinor=19000,externalKey="stmt|$iban|GEL|2026-09-05|-12345|19000|1"))
        Unit
    }
    @After fun close()=db.close()
    @Test fun previewDoesNotWriteAndMergePreservesOriginalAndSupportsUndo() = runBlocking {
        val service=CredoDuplicateReview(db); val before=db.transactionDao().allForIntegrity()
        val preview=service.preview();assertEquals(1,preview.pairs.size);assertEquals(before,db.transactionDao().allForIntegrity())
        val applied=service.confirm(preview,1)
        val after=db.transactionDao().allForIntegrity()
        assertEquals(1,after.count { !it.isVoided });assertEquals(-12345L,after.filterNot { it.isVoided }.sumOf { it.amountMinor })
        assertEquals(before.first { it.id==1L }.categoryId,db.transactionDao().byId(1)!!.categoryId)
        assertEquals(19000L,db.transactionDao().byId(1)!!.balanceAfterMinor)
        assertEquals(1L,db.transactionDao().byId(2)!!.mergedIntoTransactionId)
        assertNull(db.transactionDao().byId(2)!!.externalKey)
        assertTrue(service.preview().pairs.isEmpty())
        assertTrue(runCatching { service.confirm(preview,1) }.isFailure)
        service.undo(applied);assertEquals(before,db.transactionDao().allForIntegrity())
    }
    @Test fun mergedPurchaseSurvivesOldAndNewStatementImports() = runBlocking {
        val service=CredoDuplicateReview(db);service.confirm(service.preview(),1)
        for(balance in listOf(19000L,20000L,19000L)) {
            val row=StatementRow(day.plusDays(1),StatementOperation.CARD_PAYMENT,"purchase",-12345,balance,"Purchase EXAMPLE STORE",null,null,"EXAMPLE STORE",day)
            val statement=BankStatement(BankProfile("Credo","Credo"),iban,"GEL",day,day.plusDays(1),null,null,listOf(row))
            val account=db.accountDao().byId(1)!!
            val plan=ImportPlanner(db,LedgerCalendar.zone).plan(statement,account,false,false)
            assertEquals(0,plan.inserted)
            ImportApplier(db,LedgerCalendar.zone).apply(plan,account,"synthetic.xlsx",StatementImportOrigin.FILE)
            assertEquals(1,db.transactionDao().allForIntegrity().count { !it.isVoided && it.source==TxSource.STATEMENT })
        }
    }
    @Test fun changedPreviewIsRejected() = runBlocking {
        val service=CredoDuplicateReview(db);val p=service.preview()
        db.transactionDao().update(db.transactionDao().byId(1)!!.copy(note="Changed"))
        assertTrue(runCatching { service.confirm(p,1) }.isFailure)
        assertEquals(2,db.transactionDao().allForIntegrity().count { !it.isVoided })
    }
    @Test fun undoRejectsNewLinksAndNewLedgerChanges() = runBlocking {
        val service=CredoDuplicateReview(db)
        val applied=service.confirm(service.preview(),1)
        db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(transactionId=1,amountMinor=-12345,purpose=AllocationPurpose.PERSONAL)))
        assertTrue(runCatching { service.undo(applied) }.isFailure)
        db.transactionAllocationDao().deleteForTransaction(1)
        db.transactionDao().update(db.transactionDao().byId(1)!!.copy(note="Changed"))
        assertTrue(runCatching { service.undo(applied) }.isFailure)
        assertEquals(1,db.transactionDao().allForIntegrity().count { !it.isVoided })
    }
    @Test fun transportAndSameImportTwinsAreNotSuggested() = runBlocking {
        val service=CredoDuplicateReview(db)
        db.transactionDao().update(db.transactionDao().byId(2)!!.copy(createdAt=1))
        assertTrue(service.preview().pairs.isEmpty())
        for(id in 1L..2L) db.transactionDao().update(db.transactionDao().byId(id)!!.copy(createdAt=id,amountMinor=-100))
        assertTrue(service.preview().pairs.isEmpty())
    }
    @Test fun linkedAllocationBlocksEvenAnAlreadyOpenPreview() = runBlocking {
        val service=CredoDuplicateReview(db);val preview=service.preview()
        db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(transactionId=1,amountMinor=-12345,purpose=AllocationPurpose.PERSONAL)))
        assertTrue(service.preview().pairs.isEmpty())
        assertTrue(runCatching { service.confirm(preview,1) }.isFailure)
    }
}
