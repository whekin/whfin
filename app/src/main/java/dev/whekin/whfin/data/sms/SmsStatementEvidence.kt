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

    /** The reverse of statement-import consolidation: the full day's SMS cohort is one bank row. */
    suspend fun findCardAggregate(messages: List<SmsDiagnosticEntity>): Match? {
        if (messages.size < 2) return null
        val first = messages.first()
        val card = first.cardLast4 ?: return null
        val currency = first.currency ?: return null
        val merchant = first.counterparty ?: return null
        val day = first.occurredAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return null
        val bank = BankSmsBank.fromKey(first.externalKey)
        if (messages.any { it.kind != SmsDiagnosticKind.CARD_PAYMENT || it.cardLast4 != card ||
                it.currency != currency || it.balanceCurrency != currency ||
                it.amountMinor == null || it.amountMinor <= 0 ||
                it.occurredAt?.let { at -> Instant.ofEpochMilli(at).atZone(zone).toLocalDate() } != day ||
                !MerchantNormalizer.equivalent(it.counterparty, merchant) ||
                BankSmsBank.fromKey(it.externalKey) != bank }) return null
        val account = db.accountDao().byCardAndCurrency(card, currency)
            .filter { bank.accepts(db, it) }.singleOrNull() ?: return null
        val sum = try { messages.fold(0L) { total, message -> Math.addExact(total, requireNotNull(message.amountMinor)) } }
            catch (_: ArithmeticException) { return null }
        // No subset search and no occupied-row filtering before uniqueness: another purchase at
        // this merchant on that day makes the consolidation ambiguous even if already matched.
        val candidate = db.transactionDao().statementCandidates(account.id,
            day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
            .filter { !it.isTransfer && it.amountMinor < 0 && MerchantNormalizer.equivalent(it.rawCounterparty, merchant) }
            .singleOrNull() ?: return null
        if (candidate.amountMinor != -sum || db.smsDiagnosticDao().countOtherForTransaction(candidate.id, "") != 0) return null
        return Match(candidate, account, exact = true)
    }

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
            ?.takeIf { MerchantNormalizer.normalize(it).isNotEmpty() }
        if (diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT && merchant == null && !sameCurrency) {
            // Neither the merchant nor a comparable amount: nothing here would be evidence.
            return null
        }

        // Credo can print the available GEL balance while settling the purchase on the card's
        // USD/EUR ledger. Search that currency too, but require its exact purchase amount.
        val currencies = if (diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT)
            setOfNotNull(ledgerCurrency, diagnostic.currency) else setOf(ledgerCurrency)
        val routed = restrictTo ?: diagnostic.cardLast4?.let { last4 ->
            currencies.flatMap { db.accountDao().byCardAndCurrency(last4, it) }
                .filter { BankSmsBank.fromKey(diagnostic.externalKey).accepts(db, it) }
                .takeIf { it.isNotEmpty() }
        }
        val amountCandidates = currencies.flatMap { candidatesFor(diagnostic, it, routed) }
            .filter { (_, transaction) ->
            val signMatches = if (incoming) transaction.amountMinor > 0 else transaction.amountMinor < 0
            val amountMatches = transaction.currency != diagnostic.currency ||
                abs(transaction.amountMinor) == abs(amountMinor)
            val merchantMatches = merchant == null ||
                MerchantNormalizer.equivalent(transaction.rawCounterparty, merchant)
            val extraSettlementDay = diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT &&
                Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate() ==
                    Instant.ofEpochMilli(requireNotNull(diagnostic.occurredAt)).atZone(zone).toLocalDate().plusDays(2)
            val strongSettlementAmount = transaction.currency == diagnostic.currency &&
                abs(transaction.amountMinor) == abs(amountMinor)
            signMatches && amountMatches && (!extraSettlementDay || strongSettlementAmount) && when (diagnostic.kind) {
                // A card payment is identified by where it was made; the statement prints the same
                // merchant the message did.
                SmsDiagnosticKind.CARD_PAYMENT -> merchantMatches
                // Everything else names no merchant reliably, so the money itself has to match.
                else -> sameCurrency
            }
        }
        // Utility messages can name the provider while statements spell it in another alphabet.
        // When equal amounts occur on nearby days, that name can distinguish the actual bill;
        // without a provider match, retain the existing amount-only uniqueness rule.
        val candidates = if (diagnostic.kind == SmsDiagnosticKind.BILL_PAYMENT && merchant != null) {
            amountCandidates.filter { (_, transaction) ->
                MerchantNormalizer.equivalent(transaction.rawCounterparty, merchant)
            }.ifEmpty { amountCandidates }
        } else amountCandidates

        val exact = when (diagnostic.kind) {
            // Same money at the same merchant. An amount alone repeats too often to name a card's ledger.
            SmsDiagnosticKind.CARD_PAYMENT -> sameCurrency && merchant != null
            else -> sameCurrency
        }
        return decide(candidates, diagnostic, exact)?.let { match ->
            match.copy(exact = match.exact ||
                (diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT &&
                    match.transaction.currency == diagnostic.currency && merchant != null))
        }
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
        // The receiving IBAN distinguishes two equal withdrawals to different own accounts.
        // A grouped source must have its receiving leg in that same bank-confirmed movement.
        val pairedSent = sent.filter { (_, transaction) ->
            transaction.transferGroupId == null || received.any { (_, peer) ->
                peer.transferGroupId == transaction.transferGroupId
            }
        }
        return decide(pairedSent, diagnostic, exact = true)
    }

    private suspend fun candidatesFor(
        diagnostic: SmsDiagnosticEntity,
        currency: String,
        restrictTo: List<AccountEntity>?,
    ): List<Pair<AccountEntity, TransactionEntity>> {
        val occurredAt = diagnostic.occurredAt ?: return emptyList()
        val day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        val receivedDay = Instant.ofEpochMilli(diagnostic.receivedAt).atZone(zone).toLocalDate()
        val days = if (BankSmsBank.fromKey(diagnostic.externalKey) == BankSmsBank.CREDO && day > receivedDay &&
            diagnostic.kind in setOf(SmsDiagnosticKind.OUTGOING_TRANSFER, SmsDiagnosticKind.INCOMING_TRANSFER))
            setOf(day, receivedDay) else setOf(day)
        // Some card descriptions carry the settlement date, two days after the SMS. Keep the
        // narrow transfer window; cards still require a unique merchant/amount candidate.
        val throughDays = if (diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT) 3L else 2L
        val accounts = restrictTo?.filter { it.currency == currency }
            ?: db.accountDao().bankAccountsByCurrency(currency)
        return accounts.filter { BankSmsBank.fromKey(diagnostic.externalKey).accepts(db, it) }.flatMap { account ->
            days.flatMap { possibleDay -> db.transactionDao().statementCandidates(account.id,
                possibleDay.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                possibleDay.plusDays(throughDays).atStartOfDay(zone).toInstant().toEpochMilli() - 1) }.distinctBy { it.id }
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
