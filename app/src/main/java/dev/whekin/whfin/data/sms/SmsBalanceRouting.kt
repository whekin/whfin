package dev.whekin.whfin.data.sms

/**
 * Which ledger stood at the balance the bank printed.
 *
 * A transfer message names neither a card nor an account, so routing it looked like a question only
 * the person could answer. It is not: the message states what was left afterwards, and that figure
 * belonged to exactly one ledger at that moment. Asking about money the message has already
 * identified is asking the person to re-derive what the data knows.
 *
 * The starting figure has to be the bank's own — the last balance it declared on that ledger, which
 * both statements and earlier messages print. Our sum of rows would be a claim that nothing is
 * missing, and a ledger that quietly lost one row would then answer confidently and wrongly. A ledger
 * with no declared balance behind it therefore does not answer at all.
 *
 * One match decides. None and several both stay a question, because the cost of a wrong route is a
 * real operation written into an account it never touched.
 */
internal data class DeclaredBalanceEvidence(
    val accountId: Long,
    /** The last balance the bank itself declared on this ledger at or before the message. */
    val anchorBalanceMinor: Long,
    /** What the ledger recorded between that declaration and the message. */
    val movedSinceMinor: Long,
)

/**
 * @param ledgerDeltaMinor what this operation does to the ledger, signed — the balance is stated
 *   after it, so the ledger has to be walked forward through it to reach the printed figure.
 */
internal fun accountAtDeclaredBalance(
    evidence: List<DeclaredBalanceEvidence>,
    ledgerDeltaMinor: Long,
    declaredBalanceMinor: Long,
): Long? = evidence
    .singleOrNull {
        it.anchorBalanceMinor + it.movedSinceMinor + ledgerDeltaMinor == declaredBalanceMinor
    }
    ?.accountId
