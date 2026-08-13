package dev.whekin.whfin.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.preferences.UiPreferences
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first

internal fun smsCatchUpSince(now: Long, lastCompletedAt: Long): Long? = when {
    lastCompletedAt > 0 && now - lastCompletedAt < 5L * 60 * 1_000 -> null
    lastCompletedAt > 0 -> (lastCompletedAt - 5L * 60 * 1_000).coerceAtLeast(0)
    else -> now - 24L * 60 * 60 * 1_000
}

/**
 * Recovers transaction messages an OEM did not deliver to the manifest receiver.
 *
 * The scan is permission-gated, bounded, throttled and idempotent through the same external keys as
 * live delivery. Raw bodies exist only while each inbox row is parsed and are never copied to Room.
 */
class SmsForegroundCatchUp(
    private val context: Context,
    private val database: WhfinDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    suspend fun runIfNeeded() {
        if (!running.compareAndSet(false, true)) return
        try {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            if (!UiPreferences(context).smsImportEnabled.first()) return
            val current = now()
            val lastCompleted = preferences.getLong(LAST_COMPLETED_AT, 0)
            val since = smsCatchUpSince(current, lastCompleted) ?: return
            val importer = SmsTransactionImporter(database)
            SmsHistoryReader(context.contentResolver)
                .credoCandidates(since, limit = MAX_MESSAGES)
                .sortedBy(HistoricalSms::receivedAt)
                .forEach { importer.import(it.body, it.receivedAt) }
            preferences.edit().putLong(LAST_COMPLETED_AT, current).apply()
        } finally {
            running.set(false)
        }
    }

    private companion object {
        const val PREFERENCES = "whfin_sms_catch_up"
        const val LAST_COMPLETED_AT = "last_completed_at"
        const val MAX_MESSAGES = 500
    }
}
