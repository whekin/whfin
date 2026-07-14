package dev.whekin.whfin.data.sms

import android.content.ContentResolver
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoricalSms(
    val body: String,
    val receivedAt: Long,
)

/** Reads a bounded local window. Callers must already hold READ_SMS. */
class SmsHistoryReader(private val resolver: ContentResolver) {
    suspend fun credoCandidates(since: Long, limit: Int = 500): List<HistoricalSms> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<HistoricalSms>()
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && result.size < limit) {
                    val body = cursor.getString(bodyIndex).orEmpty()
                    if (CredoSmsParser.isCredoCandidate(body)) {
                        result += HistoricalSms(body, cursor.getLong(dateIndex))
                    }
                }
            }
            result
        }

    suspend fun findByExternalKey(externalKey: String, receivedAt: Long): HistoricalSms? =
        withContext(Dispatchers.IO) {
            val oneDay = 24 * 60 * 60 * 1_000L
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf((receivedAt - oneDay).toString(), (receivedAt + oneDay).toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIndex).orEmpty()
                    if (smsExternalKey(body) == externalKey) {
                        return@withContext HistoricalSms(body, cursor.getLong(dateIndex))
                    }
                }
            }
            null
        }
}
