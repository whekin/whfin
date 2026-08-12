package dev.whekin.whfin.data.credo

import android.content.Context

/**
 * Which accounts have already been walked back to the beginning of what Credo keeps.
 *
 * A history load ends when the bank has nothing earlier, and that answer does not change: offering
 * it again afterwards is offering to re-download a year of statements for nothing. It stays an
 * offer for an account added later, which has its own history nobody has reached yet.
 *
 * Device-local, beside the other MyCredo markers: excluded from Android backup and transfer,
 * surviving process death and an in-place update. Only account stable keys live here.
 */
class CredoHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEYS, emptySet()).orEmpty().toSet()

    fun markComplete(keys: Set<String>) {
        if (keys.isEmpty()) return
        preferences.edit().putStringSet(KEYS, load() + keys).apply()
    }

    /** Forgetting the login forgets this too: the next owner of this device starts from nothing. */
    fun clear() = preferences.edit().remove(KEYS).apply()

    private companion object {
        const val PREFERENCES = "whfin_credo_device"
        const val KEYS = "history_complete_account_keys"
    }
}
