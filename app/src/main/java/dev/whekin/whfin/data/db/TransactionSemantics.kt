package dev.whekin.whfin.data.db

/**
 * True for the technical row that establishes what a ledger already held before WHFIN started
 * tracking it.
 *
 * Opening rows intentionally use [TxSource.ADJUSTMENT] because they are not bank evidence, but
 * they are not a user correction either. Manual account creation has no external key; statement
 * imports use the stable `opening|...` key. Both forms are balance-only and must stay out of
 * analytics and user-facing activity. A regular balance adjustment is not a transfer and therefore
 * does not match this classifier.
 */
fun TransactionEntity.isOpeningBalanceAnchor(): Boolean =
    source == TxSource.ADJUSTMENT &&
        isTransfer &&
        !isVoided &&
        correctionOfTransactionId == null &&
        (externalKey == null || externalKey.startsWith("opening|"))
