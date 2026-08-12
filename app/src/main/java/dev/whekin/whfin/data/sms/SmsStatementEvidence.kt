package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.MerchantNormalizer
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The ledger row a message is talking about, found without knowing which account it belongs to.
 *
 * The statement is the source of truth and it arrives per account, so once it is imported the
 * question "which of the four lari ledgers is this?" is already answered on disk: exactly one of
 * them holds this payment. Asking the user to pick an account for money the bank has already filed
 * is asking them to re-do work the data has done — and answering wrong would write a second row for
 * one purchase.
 *
 * Everything here is deliberately unwilling to guess. A row is only accepted when it is the single
 * candidate across every ledger of the right currency, and a row another message already explains is
 * never taken: two coffees at one shop on one day are indistinguishable, and picking either would
 * silently attach the wrong one.
 */
internal class SmsStatementEvidence(
    private val db: WhfinDatabase,
    private val zone: ZoneId,
) {

    /**
     * @param exact the shape matched on its own terms — same currency, same amount, and for a card
     *   the same merchant. Only an exact match is strong enough to teach the card's ledger.
     */
    data class Match(
        val transaction: TransactionEntity,
        val account: AccountEntity,
        val exact: Boolean,
    )

    /**
     * @param restrictTo the ledgers the caller has already routed this message to, when it has. A
     *   known route must not be overruled by a similar row in a different account; the search then
     *   only answers "is this operation already here?".
     */
    suspend fun find(
        diagnostic: SmsDiagnosticEntity,
        restrictTo: List<AccountEntity>? = null,
    ): Match? = when (diagnostic.kind) {
        SmsDiagnosticKind.CARD_PAYMENT,
        SmsDiagnosticKind.BILL_PAYMENT,
        SmsDiagnosticKind.OUTGOING_TRANSFER,
        SmsDiagnosticKind.INCOMING_TRANSFER,
        SmsDiagnosticKind.CASH_DEPOSIT,
        SmsDiagnosticKind.INTEREST,
        SmsDiagnosticKind.DEPOSIT_TOP_UP,
        -> singleLeg(diagnostic, restrictTo)

        SmsDiagnosticKind.CURRENCY_EXCHANGE -> conversion(diagnostic, restrictTo)
        // An own transfer names both IBANs, so routing it was never the problem. Finding it is: the
        // statement holds the same two legs, and writing a second pair doubles both balances.
        SmsDiagnosticKind.OWN_TRANSFER -> ownTransfer(diagnostic, restrictTo)
        SmsDiagnosticKind.IGNORED,
        SmsDiagnosticKind.UNRECOGNIZED,
        -> null
    }

    private suspend fun singleLeg(
        diagnostic: SmsDiagnosticEntity,
        restrictTo: List<AccountEntity>?,
    ): Match? {
        val amountMinor = diagnostic.amountMinor ?: return null
        // The balance is stated in the currency of the ledger the money moved on; the amount may be
        // the foreign one a card was charged in.
        val ledgerCurrency = diagnostic.balanceCurrency ?: diagnostic.currency ?: return null
        val incoming = diagnostic.kind in INCOMING_KINDS
        val sameCurrency = diagnostic.currency == ledgerCurrency
        val merchant = diagnostic.counterparty
            ?.let(MerchantNormalizer::normalize)
            ?.takeIf(String::isNotEmpty)
        if (diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT && merchant == null && !sameCurrency) {
            // Neither the merchant nor a comparable amount: nothing here would be evidence.
            return null
        }

        val candidates = candidatesFor(diagnostic, ledgerCurrency, restrictTo).filter { (_, transaction) ->
            val signMatches = if (incoming) transaction.amountMinor > 0 else transaction.amountMinor < 0
            val amountMatches = !sameCurrency || abs(transaction.amountMinor) == abs(amountMinor)
            val merchantMatches = merchant == null ||
                sameMerchant(transaction.rawCounterparty, merchant)
            signMatches && amountMatches && when (diagnostic.kind) {
                // A card payment is identified by where it was made; the statement prints the same
                // merchant the message did.
                SmsDiagnosticKind.CARD_PAYMENT -> merchantMatches
                // Everything else names no merchant reliably, so the money itself has to match.
                else -> sameCurrency
            }
        }

        val exact = when (diagnostic.kind) {
            // Same money at the same merchant. An amount alone repeats too often to name a card's ledger.
            SmsDiagnosticKind.CARD_PAYMENT -> sameCurrency && merchant != null
            else -> sameCurrency
        }
        return decide(candidates, diagnostic, exact)
    }

    /**
     * A conversion is two ledger rows, and the message states both sides. Requiring the receiving
     * leg to exist too keeps a plain outgoing transfer of the same amount from passing as one.
     */
    private suspend fun conversion(
        diagnostic: SmsDiagnosticEntity,
        restrictTo: List<AccountEntity>?,
    ): Match? {
        val soldMinor = diagnostic.amountMinor ?: return null
        val soldCurrency = diagnostic.currency ?: return null
        val boughtMinor = diagnostic.secondaryAmountMinor ?: return null
        val boughtCurrency = diagnostic.secondaryCurrency ?: return null

        val sold = candidatesFor(diagnostic, soldCurrency, restrictTo).filter { (_, transaction) ->
            transaction.isTransfer && transaction.amountMinor == -abs(soldMinor)
        }
        val bought = candidatesFor(diagnostic, boughtCurrency, restrictTo).filter { (_, transaction) ->
            transaction.isTransfer && transaction.amountMinor == abs(boughtMinor)
        }
        if (bought.isEmpty()) return null
        return decide(sold, diagnostic, exact = true)
    }

    /**
     * Both legs of a transfer between own accounts, as the statement filed them.
     *
     * The IBANs name the ledgers exactly, so this is not a search for the account but for the
     * operation. Requiring the receiving leg as well keeps an unrelated payment of the same amount
     * from passing as the transfer.
     */
    private suspend fun ownTransfer(
        diagnostic: SmsDiagnosticEntity,
        restrictTo: List<AccountEntity>?,
    ): Match? {
        val amountMinor = diagnostic.amountMinor ?: return null
        val currency = diagnostic.currency ?: return null
        val fromIban = diagnostic.fromIban ?: return null
        val toIban = diagnostic.toIban ?: return null

        val legs = candidatesFor(diagnostic, currency, restrictTo)
        val sent = legs.filter { (account, transaction) ->
            account.iban == fromIban &&
                transaction.isTransfer &&
                transaction.amountMinor == -abs(amountMinor)
        }
        val received = legs.filter { (account, transaction) ->
            account.iban == toIban &&
                transaction.isTransfer &&
                transaction.amountMinor == abs(amountMinor)
        }
        if (received.isEmpty()) return null
        return decide(sent, diagnostic, exact = true)
    }

    /**
     * A card prints its merchant differently in each channel: `ANTHROPIC* CLAUDE.AI` in the message,
     * `ANTHROPIC` on the statement. One being the start of the other is the same shop; requiring
     * equality left real pairs unmatched, and an unmatched pair is what becomes a duplicate row.
     */
    private fun sameMerchant(rawCounterparty: String?, wanted: String): Boolean {
        val candidate = rawCounterparty?.let(MerchantNormalizer::normalize).orEmpty()
        if (candidate.isEmpty()) return false
        if (candidate == wanted) return true
        val shorter = minOf(candidate, wanted, compareBy(String::length))
        val longer = maxOf(candidate, wanted, compareBy(String::length))
        return shorter.length >= MERCHANT_PREFIX_MINIMUM && longer.startsWith(shorter)
    }

    private suspend fun candidatesFor(
        diagnostic: SmsDiagnosticEntity,
        currency: String,
        restrictTo: List<AccountEntity>?,
    ): List<Pair<AccountEntity, TransactionEntity>> {
        val occurredAt = diagnostic.occurredAt ?: return emptyList()
        val day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        // A card reaches the statement a day or two after the purchase, and the statement books it
        // on the purchase date; a day either side covers the disagreement without inviting another.
        val from = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val accounts = restrictTo?.filter { it.currency == currency }
            ?: db.accountDao().bankAccountsByCurrency(currency)
        return accounts.flatMap { account ->
            db.transactionDao().statementCandidates(account.id, from, to)
                .filter {
                    db.smsDiagnosticDao()
                        .countOtherForTransaction(it.id, diagnostic.externalKey) == 0
                }
                .map { account to it }
        }
    }

    /**
     * The balance the bank stated is the tie-breaker: only one ledger stood at that figure after
     * that operation, so it names the account even when the amount alone is ambiguous.
     */
    private fun decide(
        candidates: List<Pair<AccountEntity, TransactionEntity>>,
        diagnostic: SmsDiagnosticEntity,
        exact: Boolean,
    ): Match? {
        val byBalance = candidates.filter { (account, transaction) ->
            diagnostic.balanceMinor != null &&
                diagnostic.balanceCurrency == account.currency &&
                transaction.balanceAfterMinor == diagnostic.balanceMinor
        }
        val chosen = byBalance.singleOrNull() ?: candidates.singleOrNull() ?: return null
        return Match(
            transaction = chosen.second,
            account = chosen.first,
            exact = exact || byBalance.isNotEmpty(),
        )
    }

    private companion object {
        /** Short enough to pair a truncated merchant, long enough not to pair two different shops. */
        const val MERCHANT_PREFIX_MINIMUM = 5

        val INCOMING_KINDS = setOf(
            SmsDiagnosticKind.INCOMING_TRANSFER,
            SmsDiagnosticKind.CASH_DEPOSIT,
            SmsDiagnosticKind.INTEREST,
            SmsDiagnosticKind.DEPOSIT_TOP_UP,
        )
    }
}
