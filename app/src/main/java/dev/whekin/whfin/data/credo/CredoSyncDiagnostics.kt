package dev.whekin.whfin.data.credo

import android.util.Log

/** Local routing diagnostics. No free-form server text, identifiers, amounts or credentials. */
internal object CredoSyncDiagnostics {
    const val TAG = "WHFIN_CREDO_SYNC"
    enum class Event { ACCOUNT_START, API_PAGE, API_ROWS, API_APPLIED, XLSX_NO_OPENING,
        XLSX_NO_HISTORY_ID, XLSX_CONVERSION_GROUP, XLSX_AMBIGUOUS_API, XLSX_DOWNLOAD, ACCOUNT_ERROR,
        HISTORY_COMPLETE, HISTORY_INCOMPLETE, REQUEST_START, REQUEST_DONE, REQUEST_TIMEOUT, REQUEST_FAILED }
    fun record(event: Event, count: Int = 0, secondary: Int = 0) {
        Log.i(TAG, "${event.name} count=${count.coerceAtLeast(0)} secondary=${secondary.coerceAtLeast(0)}")
    }
}
