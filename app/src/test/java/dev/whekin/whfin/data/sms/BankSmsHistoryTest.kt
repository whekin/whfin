package dev.whekin.whfin.data.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankSmsHistoryTest {
    @Test fun historyRetainsBankIdentityAndRejectsImitatingUntrustedSenders() = runBlocking {
        val text = "2.00 GEL (*0001) EXAMPLE BUS 08/09/26 22:22"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS)).apply {
                    addRow(arrayOf<Any>(text, 1000L, "TBC SMS"))
                    addRow(arrayOf<Any>(text, 1001L, "Example Friend"))
                    addRow(arrayOf<Any>("TBC SMS Code: 0000", 1002L, "TBC SMS"))
                    addRow(arrayOf<Any>("Payment: 2.00 GEL Card N ****0001 EXAMPLE SHOP>Tbilisi GE 08/09/2026 22:22:00", 1003L, "Credo Bank"))
                }
            override fun getType(uri: Uri) = "vnd.android.cursor.dir/sms"
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal("sms", provider)
        val reader = SmsHistoryReader(ApplicationProvider.getApplicationContext<Context>().contentResolver)
        val messages = reader.bankCandidates(0)
        assertEquals(listOf(BankSmsBank.TBC, BankSmsBank.CREDO), messages.map { it.bank })
        assertEquals(BankSmsBank.TBC, reader.findByExternalKey(BankSmsBank.TBC.key(text), 1000)?.bank)
    }
}
