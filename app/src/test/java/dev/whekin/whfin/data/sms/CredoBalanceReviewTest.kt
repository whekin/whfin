package dev.whekin.whfin.data.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class CredoBalanceReviewTest {
    private lateinit var db: WhfinDatabase
    @Before fun setup() = runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WhfinDatabase::class.java).allowMainThreadQueries().build()
        val group=db.financialGroupDao().insert(FinancialGroupEntity(name="Credo",type=FinancialGroupType.BANK,provider="Credo"))
        for(i in 1L..2L) db.accountDao().insert(AccountEntity(id=i,name="Synthetic $i",type=AccountType.BANK,groupId=group,currency="GEL",iban="GE00CD000000000000000$i"))
        for((id,amount) in listOf(1L to 10000L,2L to 200000L)) db.transactionDao().insert(TransactionEntity(accountId=id,amountMinor=amount,
            currency="GEL",occurredAt=1,status=TxStatus.CONFIRMED,source=TxSource.STATEMENT,balanceAfterMinor=amount,createdAt=1))
        val transfer=db.transactionDao().insertTransferGroup(TransferGroupEntity(type=TransferGroupType.TRANSFER,note="Credo SMS transfer",createdAt=2))
        db.transactionDao().insert(TransactionEntity(id=3,accountId=2,amountMinor=-25000,currency="GEL",occurredAt=2,
            status=TxStatus.CONFIRMED,source=TxSource.SMS,transferGroupId=transfer,isTransfer=true,balanceAfterMinor=35000,externalKey="sms|synthetic",createdAt=2))
        db.transactionDao().insert(TransactionEntity(id=4,accountId=1,amountMinor=25000,currency="GEL",occurredAt=2,
            status=TxStatus.CONFIRMED,source=TxSource.SMS,transferGroupId=transfer,isTransfer=true,externalKey="sms|synthetic|to",createdAt=2))
        Unit
    }
    @After fun close()=db.close()
    @Test fun previewWritesNothingAndExplicitConfirmationOnlyChangesBalanceMetadata() = runBlocking {
        val service=CredoBalanceReview(db);val before=db.transactionDao().allForIntegrity()
        val preview=service.preview(); assertEquals(2,preview.changes.size)
        assertEquals(before,db.transactionDao().allForIntegrity())
        service.confirm(preview)
        val after=db.transactionDao().allForIntegrity()
        assertEquals(before.map { it.copy(balanceAfterMinor=null) },after.map { it.copy(balanceAfterMinor=null) })
        assertNull(db.transactionDao().byId(3)!!.balanceAfterMinor)
        assertEquals(35000L,db.transactionDao().byId(4)!!.balanceAfterMinor)
        assertTrue(service.preview().changes.isEmpty())
    }
    @Test fun stalePreviewCannotBeApplied() = runBlocking {
        val service=CredoBalanceReview(db);val preview=service.preview()
        db.transactionDao().update(db.transactionDao().byId(3)!!.copy(note="Owner note"))
        assertTrue(runCatching { service.confirm(preview) }.isFailure)
        assertEquals(35000L,db.transactionDao().byId(3)!!.balanceAfterMinor)
    }
    @Test fun anUnprovenBalanceIsNotMovedToTheOtherSide() = runBlocking {
        db.transactionDao().update(db.transactionDao().byId(3)!!.copy(balanceAfterMinor=12345))
        val service=CredoBalanceReview(db);val preview=service.preview()
        assertEquals(1,preview.changes.size);assertNull(preview.changes.single().after)
        assertEquals(12345L,db.transactionDao().byId(3)!!.balanceAfterMinor)
    }
    @Test fun aProvenDebitBalanceNeedsNoRepair() = runBlocking {
        db.transactionDao().update(db.transactionDao().byId(3)!!.copy(balanceAfterMinor=175000))
        assertTrue(CredoBalanceReview(db).preview().changes.isEmpty())
    }
}
