package dev.whekin.whfin.data.credo

import android.content.Context

/**
 * Device-local continuation marker for a partially failed statement run.
 *
 * It deliberately lives beside the MyCredo device id: this preferences file is excluded from
 * Android backup/transfer, while surviving process death and an in-place APK update. The values are
 * only account stable keys already returned by Credo; no credentials or statement contents live here.
 */
class CredoRetryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEYS, emptySet()).orEmpty().toSet()

    fun save(keys: Set<String>) {
        preferences.edit().apply {
            if (keys.isEmpty()) remove(KEYS) else putStringSet(KEYS, keys)
        }.apply()
    }

    private companion object {
        const val PREFERENCES = "whfin_credo_device"
        const val KEYS = "statement_retry_account_keys"
    }
}
