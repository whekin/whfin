package dev.whekin.whfin.ui.setup

/** Bank imports may finish after the owner leaves Bank SMS; the scan belongs to setup, not a page. */
internal fun shouldCheckCards(
    hasSmsHistoryPermission: Boolean,
    bankWorkActive: Boolean,
    importRevision: Long,
    checkedRevision: Long,
    retryKey: Int,
    checkedRetryKey: Int,
): Boolean = hasSmsHistoryPermission && !bankWorkActive && importRevision > 0L &&
    (importRevision != checkedRevision || retryKey != checkedRetryKey)
