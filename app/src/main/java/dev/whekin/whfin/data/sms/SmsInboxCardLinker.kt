package dev.whekin.whfin.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.whekin.whfin.data.db.WhfinDatabase

/**
 * Reads card identity out of messages the phone already has.
 *
 * A statement names the account but never the card; a message names the card but never the account.
 * The link between them exists only where both describe the same purchase, so it can be derived —
 * but only once the statements are in. That ordering is the whole reason this is a separate step
 * rather than something the importer does as messages arrive.
 *
 * Nothing is imported and no message is stored: a message that produces no exact match is left
 * exactly as it was. Without `READ_SMS` the step does nothing at all rather than asking for it.
 */
object SmsInboxCardLinker {

    /** Ninety days of messages is far more than enough to meet every card the user actually uses. */
    private const val LOOKBACK_MILLIS = 90L * 24 * 60 * 60 * 1000

    data class Result(val cardsLinked: Int = 0, val messagesAnswered: Int = 0)

    /**
     * @param now passed in so the window is the caller's, which keeps this testable without a clock.
     */
    suspend fun run(
        context: Context,
        db: WhfinDatabase,
        now: Long = System.currentTimeMillis(),
    ): Result {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result()
        }
        val messages = SmsHistoryReader(context.contentResolver)
            .credoCandidates(now - LOOKBACK_MILLIS)
        val importer = SmsTransactionImporter(db)
        val linked = importer.learnCardsFrom(messages.map(HistoricalSms::body))
        // A card learned a moment ago can place messages that were already waiting on one.
        val answered = importer.attachUnroutedToStatements()
        return Result(cardsLinked = linked, messagesAnswered = answered)
    }
}
