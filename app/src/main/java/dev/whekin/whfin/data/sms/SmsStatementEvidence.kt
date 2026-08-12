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

    suspend fun find(diagnostic: SmsDiagnosticEntity): Match? = when (diagnostic.kind) {
        SmsDiagnosticKind.CARD_PAYMENT,
        SmsDiagnosticKind.BILL_PAYMENT,
        SmsDiagnosticKind.OUTGOING_TRANSFER,
        SmsDiagnosticKind.INCOMING_TRANSFER,
        SmsDiagnosticKind.CASH_DEPOSIT,
        SmsDiagnosticKind.INTEREST,
        SmsDiagnosticKind.DEPOSIT_TOP_UP,
        -> singleLeg(diagnostic)

        SmsDiagnosticKind.CURRENCY_EXCHANGE -> conversion(diagnostic)
        // An own transfer names both IBANs itself; when their ledgers exist it never reaches here.
        SmsDiagnosticKind.OWN_TRANSFER,
        SmsDiagnosticKind.IGNORED,
        SmsDiagnosticKind.UNRECOGNIZED,
        -> null
    }

    private suspend fun singleLeg(diagnostic: SmsDiagnosticEntity): Match? {
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

        val candidates = candidatesFor(diagnostic, ledgerCurrency).filter { (_, transaction) ->
            val signMatches = if (incoming) transaction.amountMinor > 0 else transaction.amountMinor < 0
            val amountMatches = !sameCurrency || abs(transaction.amountMinor) == abs(amountMinor)
            val merchantMatches = merchant == null ||
                transaction.rawCounterparty?.let(MerchantNormalizer::normalize) == merchant
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
    private suspend fun conversion(diagnostic: SmsDiagnosticEntity): Match? {
        val soldMinor = diagnostic.amountMinor ?: return null
        val soldCurrency = diagnostic.currency ?: return null
        val boughtMinor = diagnostic.secondaryAmountMinor ?: return null
        val boughtCurrency = diagnostic.secondaryCurrency ?: return null

        val sold = candidatesFor(diagnostic, soldCurrency).filter { (_, transaction) ->
            transaction.isTransfer && transaction.amountMinor == -abs(soldMinor)
        }
        val bought = candidatesFor(diagnostic, boughtCurrency).filter { (_, transaction) ->
            transaction.isTransfer && transaction.amountMinor == abs(boughtMinor)
        }
        if (bought.isEmpty()) return null
        return decide(sold, diagnostic, exact = true)
    }

    private suspend fun candidatesFor(
        diagnostic: SmsDiagnosticEntity,
        currency: String,
    ): List<Pair<AccountEntity, TransactionEntity>> {
        val occurredAt = diagnostic.occurredAt ?: return emptyList()
        val day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        // A card reaches the statement a day or two after the purchase, and the statement books it
        // on the purchase date; a day either side covers the disagreement without inviting another.
        val from = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return db.accountDao().bankAccountsByCurrency(currency).flatMap { account ->
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
        val INCOMING_KINDS = setOf(
            SmsDiagnosticKind.INCOMING_TRANSFER,
            SmsDiagnosticKind.CASH_DEPOSIT,
            SmsDiagnosticKind.INTEREST,
            SmsDiagnosticKind.DEPOSIT_TOP_UP,
        )
    }
}
