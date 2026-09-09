package dev.whekin.whfin.data.sms

import android.content.ContentResolver
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoricalSms(
    val body: String,
    val receivedAt: Long,
    val bank: BankSmsBank = BankSmsBank.CREDO,
)

/** Reads a bounded local window. Callers must already hold READ_SMS. */
class SmsHistoryReader(private val resolver: ContentResolver) {
    suspend fun bankCandidates(since: Long, limit: Int = 500): List<HistoricalSms> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<HistoricalSms>()
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && result.size < limit) {
                    val body = cursor.getString(bodyIndex).orEmpty()
                    val senderIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bank = BankSmsBank.fromSender(if (senderIndex >= 0) cursor.getString(senderIndex) else null) ?: continue
                    val classification = bank.classify(body)
                    if (classification !is BankSmsMessage.Classification.Ignored) {
                        result += HistoricalSms(body, cursor.getLong(dateIndex), bank)
                    }
                }
            }
            result
        }

    /**
     * The login code delivered since [since], read straight from the inbox.
     *
     * The broadcast is not dependable: One UI has already been seen to leave a sideloaded app out of
     * an SMS delivery it holds the permission for, and there is no way to tell that apart from a
     * slow bank. The inbox is where the message ends up either way, so the code is looked for there
     * as well. Only bodies matching the login template are considered, and only during an open
     * challenge — no other message is read from, and nothing is kept.
     */
    suspend fun loginCodeSince(since: Long): HistoricalSms? = withContext(Dispatchers.IO) {
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex).orEmpty()
                if (CredoLoginOtp.extract(body) != null) {
                    return@withContext HistoricalSms(body, cursor.getLong(dateIndex))
                }
            }
        }
        null
    }

    suspend fun findByExternalKey(externalKey: String, receivedAt: Long): HistoricalSms? =
        withContext(Dispatchers.IO) {
            val oneDay = 24 * 60 * 60 * 1_000L
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf((receivedAt - oneDay).toString(), (receivedAt + oneDay).toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIndex).orEmpty()
                    val bank = BankSmsBank.fromKey(externalKey)
                    val senderIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    if (senderIndex >= 0 && BankSmsBank.fromSender(cursor.getString(senderIndex)) != bank) continue
                    if (bank.key(body) == externalKey) {
                        return@withContext HistoricalSms(body, cursor.getLong(dateIndex), bank)
                    }
                }
            }
            null
        }
}
