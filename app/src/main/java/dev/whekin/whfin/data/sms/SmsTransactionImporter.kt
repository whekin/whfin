package dev.whekin.whfin.data.sms

import androidx.room.withTransaction
import dev.whekin.whfin.data.categorization.MerchantCategorizer
import dev.whekin.whfin.data.categorization.OperationCategories
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import dev.whekin.whfin.data.LedgerCalendar

data class SmsImportResult(
    val outcome: SmsDiagnosticOutcome,
    val diagnosticId: Long? = null,
    val transactionId: Long? = null,
    val reason: SmsDiagnosticReason? = null,
)

internal fun isCurrencyExchangeLedger(account: AccountEntity): Boolean =
    account.type == AccountType.BANK &&
        account.bankProduct != BankProduct.DEMAND_DEPOSIT &&
        account.bankProduct != BankProduct.TERM_DEPOSIT

/**
 * A ledger a deposit's money sits in, which is what both a top-up and an interest payment reach.
 *
 * Asked of the bank product and never of the fund role: available-or-reserve is the owner's
 * statement about their own money, and a demand deposit paying interest on each day's balance is
 * exactly the account somebody keeps available and transfers out of constantly. The legacy SAVINGS
 * type is still accepted because accounts created before products existed carry no product at all.
 */
internal fun isDepositLedger(account: AccountEntity): Boolean =
    account.type == AccountType.SAVINGS ||
        account.bankProduct == BankProduct.DEMAND_DEPOSIT ||
        account.bankProduct == BankProduct.TERM_DEPOSIT

/** Converts a bank-scoped SMS classification into a visible diagnostic and, when possible, an active transaction. */
class SmsTransactionImporter(private val db: WhfinDatabase, private val bank: BankSmsBank = BankSmsBank.CREDO) {
    private val zone = LedgerCalendar.zone
    private val statementEvidence = SmsStatementEvidence(db, zone)

    /** A reversal follows its payment closely; a wider window would retract an unrelated purchase. */
    private val CANCELLATION_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000

    suspend fun preview(body: String, receivedAt: Long = System.currentTimeMillis()): SmsImportResult =
        db.withTransaction {
            evaluate(bank.classify(body), bank.key(body), receivedAt, persist = false)
        }

    suspend fun import(body: String, receivedAt: Long = System.currentTimeMillis()): SmsImportResult = try {
        db.withTransaction {
            evaluate(bank.classify(body), bank.key(body), receivedAt, persist = true)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        val key = bank.key(body)
        val classification = bank.classify(body)
        val diagnosticId = runCatching {
            db.withTransaction {
                val diagnostic = when (classification) {
                    is BankSmsMessage.Classification.Parsed -> diagnosticFor(
                        sms = classification.sms,
                        externalKey = key,
                        outcome = SmsDiagnosticOutcome.ERROR,
                        reason = SmsDiagnosticReason.STORAGE_ERROR,
                        receivedAt = receivedAt,
                    )
                    else -> basicDiagnostic(
                        externalKey = key,
                        kind = SmsDiagnosticKind.UNRECOGNIZED,
                        outcome = SmsDiagnosticOutcome.ERROR,
                        reason = SmsDiagnosticReason.STORAGE_ERROR,
                        receivedAt = receivedAt,
                    )
                }
                persistDiagnostic(diagnostic)
            }
        }.getOrNull()
        SmsImportResult(
            outcome = SmsDiagnosticOutcome.ERROR,
            diagnosticId = diagnosticId,
            reason = SmsDiagnosticReason.STORAGE_ERROR,
        )
    }

    internal suspend fun importPush(classification: BankSmsMessage.Classification, key: String, receivedAt: Long): SmsImportResult {
        require(bank == BankSmsBank.TBC && key.startsWith("sms|tbc|push|"))
        return db.withTransaction { evaluate(classification, key, receivedAt, persist = true) }
    }

    suspend fun resolveDiagnostic(
        diagnosticId: Long,
        accountId: Long,
        cardType: PaymentInstrumentType = PaymentInstrumentType.PHYSICAL_CARD,
    ): SmsImportResult = db.withTransaction {
        val diagnostic = db.smsDiagnosticDao().byId(diagnosticId)
            ?: return@withTransaction SmsImportResult(SmsDiagnosticOutcome.ERROR, reason = SmsDiagnosticReason.NO_ACCOUNT)
        if (
            diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
            diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
        ) {
            return@withTransaction SmsImportResult(
                outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                diagnosticId = diagnostic.id,
                reason = SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            )
        }
        val account = db.accountDao().byId(accountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        if (BankSmsBank.fromKey(diagnostic.externalKey) != bank) {
            return@withTransaction SmsTransactionImporter(db, BankSmsBank.fromKey(diagnostic.externalKey))
                .resolveDiagnostic(diagnosticId, accountId, cardType)
        }
        if (!bank.accepts(db, account)) return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        val expectedCurrency = diagnostic.balanceCurrency ?: diagnostic.currency
        if (expectedCurrency == null || account.currency != expectedCurrency) {
            return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        }

        val cardLast4 = diagnostic.cardLast4
        val depositNumber = diagnostic.depositNumber
        if (cardLast4 == null && depositNumber != null) {
            // Answered once, and the deposit keeps the number: every later notice about it, and every
            // one already queued, routes without asking again. Only an account claiming no number is
            // taught one — reaching this question means none claimed this one.
            if (account.depositNumber == null) {
                db.accountDao().update(account.copy(depositNumber = depositNumber))
            }
            var selected: SmsImportResult? = null
            db.smsDiagnosticDao().unresolvedInterest(depositNumber).forEach { queued ->
                val queuedCurrency = queued.balanceCurrency ?: queued.currency
                if (queuedCurrency != account.currency) return@forEach
                val result = resolveIntoAccount(
                    diagnostic = queued,
                    account = account,
                    status = TxStatus.CONFIRMED,
                )
                if (queued.id == diagnostic.id) selected = result
            }
            return@withTransaction selected ?: resolveIntoAccount(
                diagnostic = diagnostic,
                account = account,
                status = TxStatus.CONFIRMED,
            )
        }
        if (cardLast4 == null) {
            return@withTransaction resolveIntoAccount(
                diagnostic = diagnostic,
                account = account,
                status = TxStatus.CONFIRMED,
            )
        }

        val family = cardFamilyFor(account)
        db.paymentInstrumentDao().linkForAccounts(family, cardLast4, cardType)
        var selectedResult: SmsImportResult? = null
        db.smsDiagnosticDao().unresolvedCardPayments(cardLast4).filter { BankSmsBank.fromKey(it.externalKey) == bank }.forEach { queued ->
            val queuedCurrency = queued.balanceCurrency ?: queued.currency
            val target = family.singleOrNull { it.currency == queuedCurrency } ?: return@forEach
            val result = resolveIntoAccount(
                diagnostic = queued,
                account = target,
                status = TxStatus.CONFIRMED,
            )
            if (queued.id == diagnostic.id) selectedResult = result
        }
        selectedResult ?: resolveIntoAccount(
            diagnostic = diagnostic,
            account = account,
            status = TxStatus.CONFIRMED,
        )
    }

    private suspend fun resolveIntoAccount(
        diagnostic: SmsDiagnosticEntity,
        account: AccountEntity,
        status: TxStatus,
    ): SmsImportResult {
        if (bank == BankSmsBank.TBC && diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT) {
            diagnostic.transactionId?.let { id ->
                if (db.transactionDao().byId(id) != null) return SmsImportResult(SmsDiagnosticOutcome.DUPLICATE, diagnostic.id, id)
            }
        }
        db.transactionDao().byExternalKey(diagnostic.externalKey)?.let { existing ->
            val saved = diagnostic.copy(
                outcome = SmsDiagnosticOutcome.DUPLICATE,
                reason = null,
                transactionId = existing.id,
                accountId = existing.accountId,
                updatedAt = System.currentTimeMillis(),
            )
            db.smsDiagnosticDao().update(saved)
            pairDepositTransfer(saved)
            return SmsImportResult(
                SmsDiagnosticOutcome.DUPLICATE,
                diagnostic.id,
                existing.id,
            )
        }

        diagnostic.toParsedSms()?.let { sms ->
            attachBankHold(sms, diagnostic.externalKey, diagnostic.receivedAt, true)?.let { return it }
        }
        statementEvidence.find(diagnostic, listOf(account))?.let { match ->
            val saved = diagnostic.copy(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                reason = null,
                transactionId = match.transaction.id,
                accountId = match.transaction.accountId,
                updatedAt = System.currentTimeMillis(),
            )
            db.smsDiagnosticDao().update(saved)
            return SmsImportResult(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                diagnosticId = diagnostic.id,
                transactionId = match.transaction.id,
            )
        }

        val sms = diagnostic.toParsedSms()
            ?: return updateFailure(diagnostic, SmsDiagnosticReason.PARSE_FAILURE)
        crossChannelCard(sms, diagnostic.externalKey, diagnostic.receivedAt, true)?.let { return it }
        val transactionId =
            insertTransaction(sms, account, diagnostic.externalKey, diagnostic.receivedAt, status)
        val outcome = if (transactionId > 0) SmsDiagnosticOutcome.IMPORTED else SmsDiagnosticOutcome.DUPLICATE
        val resolvedTransactionId = transactionId.takeIf { it > 0 }
            ?: db.transactionDao().byExternalKey(diagnostic.externalKey)?.id
        val saved = diagnostic.copy(
            outcome = outcome,
            reason = null,
            transactionId = resolvedTransactionId,
            accountId = account.id,
            updatedAt = System.currentTimeMillis(),
        )
        db.smsDiagnosticDao().update(saved)
        pairDepositTransfer(saved)
        return SmsImportResult(outcome, diagnostic.id, resolvedTransactionId)
    }

    suspend fun resolveGroupedDiagnostic(
        diagnosticId: Long,
        fromAccountId: Long,
        toAccountId: Long,
    ): SmsImportResult = db.withTransaction {
        val diagnostic = db.smsDiagnosticDao().byId(diagnosticId)
            ?: return@withTransaction SmsImportResult(
                SmsDiagnosticOutcome.ERROR,
                reason = SmsDiagnosticReason.NO_ACCOUNT,
            )
        if (BankSmsBank.fromKey(diagnostic.externalKey) != bank) {
            return@withTransaction SmsTransactionImporter(db, BankSmsBank.fromKey(diagnostic.externalKey))
                .resolveGroupedDiagnostic(diagnosticId, fromAccountId, toAccountId)
        }
        val sms = diagnostic.toParsedSms()
        if (sms !is BankSmsMessage.OwnTransfer && sms !is BankSmsMessage.CurrencyExchange) {
            return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.PARSE_FAILURE)
        }
        val from = db.accountDao().byId(fromAccountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        val to = db.accountDao().byId(toAccountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        if (!bank.accepts(db, from) || !bank.accepts(db, to) || !validGroupedAccounts(sms, from, to)) {
            return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        }

        db.transactionDao().byExternalKey(diagnostic.externalKey)?.let { existing ->
            val saved = diagnostic.copy(
                outcome = SmsDiagnosticOutcome.DUPLICATE,
                reason = null,
                transactionId = existing.id,
                accountId = existing.accountId,
                updatedAt = System.currentTimeMillis(),
            )
            db.smsDiagnosticDao().update(saved)
            return@withTransaction SmsImportResult(
                SmsDiagnosticOutcome.DUPLICATE,
                diagnostic.id,
                existing.id,
            )
        }

        val transactionId = insertGroupedTransactions(
            sms = sms,
            from = from,
            to = to,
            key = diagnostic.externalKey,
            receivedAt = diagnostic.receivedAt,
            status = TxStatus.CONFIRMED,
        )
        val saved = diagnostic.copy(
            outcome = SmsDiagnosticOutcome.IMPORTED,
            reason = null,
            transactionId = transactionId,
            accountId = from.id,
            updatedAt = System.currentTimeMillis(),
        )
        db.smsDiagnosticDao().update(saved)
        SmsImportResult(SmsDiagnosticOutcome.IMPORTED, diagnostic.id, transactionId)
    }

    /**
     * A message without its own date is booked at the moment it arrived.
     *
     * Credo's utility template ships an unresolved date placeholder and its interest notice prints
     * an ambiguous day; guessing either would move money into the wrong month.
     */
    private fun occurredMillis(sms: BankSmsMessage.Sms, receivedAt: Long): Long =
        sms.timestamp?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt

    /**
     * What the operation does to the ledger it belongs to, signed.
     *
     * Messages state a magnitude and say in words where the money went, so the direction lives here
     * and nowhere else: the row that gets written and the balance arithmetic that finds its account
     * must not be able to disagree about which way the money moved.
     */
    private fun ledgerDeltaMinor(sms: BankSmsMessage.Sms): Long = when (sms) {
        is BankSmsMessage.IncomingTransfer,
        is BankSmsMessage.DepositTopUp,
        // Cash paid in at a desk and interest paid by the bank are money arriving, not leaving.
        is BankSmsMessage.CashDeposit,
        is BankSmsMessage.InterestAccrual,
        -> sms.amountMinor
        else -> -sms.amountMinor
    }

    private suspend fun evaluate(
        classification: BankSmsMessage.Classification,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult = when (classification) {
        is BankSmsMessage.Classification.Canceled ->
            evaluateCanceled(classification.payment, key, receivedAt, persist)
        is BankSmsMessage.Classification.Ignored -> {
            val reason = when (classification.reason) {
                BankSmsMessage.IgnoreReason.OTP -> SmsDiagnosticReason.OTP
                BankSmsMessage.IgnoreReason.REJECTED -> SmsDiagnosticReason.REJECTED
                BankSmsMessage.IgnoreReason.UNRELATED -> SmsDiagnosticReason.UNRELATED
            }
            if (!classification.bankCandidate) {
                SmsImportResult(SmsDiagnosticOutcome.IGNORED, reason = reason)
            } else {
                val diagnostic = basicDiagnostic(
                    externalKey = key,
                    kind = SmsDiagnosticKind.IGNORED,
                    outcome = SmsDiagnosticOutcome.IGNORED,
                    reason = reason,
                    receivedAt = receivedAt,
                )
                val id = if (persist) persistDiagnostic(diagnostic) else null
                SmsImportResult(SmsDiagnosticOutcome.IGNORED, id, reason = reason)
            }
        }
        BankSmsMessage.Classification.Unrecognized -> {
            val diagnostic = basicDiagnostic(
                externalKey = key,
                kind = SmsDiagnosticKind.UNRECOGNIZED,
                outcome = SmsDiagnosticOutcome.UNRECOGNIZED,
                reason = SmsDiagnosticReason.PARSE_FAILURE,
                receivedAt = receivedAt,
            )
            val id = if (persist) persistDiagnostic(diagnostic) else null
            SmsImportResult(SmsDiagnosticOutcome.UNRECOGNIZED, id, reason = diagnostic.reason)
        }
        is BankSmsMessage.Classification.Parsed -> evaluateParsed(
            classification.sms,
            key,
            receivedAt,
            persist,
        )
    }

    /**
     * A cancellation withdraws the SMS operation its payment created.
     *
     * The payment message already produced an active row; leaving it would keep money the bank gave
     * back even though the bank explicitly retracted the operation.
     */
    private suspend fun evaluateCanceled(
        payment: BankSmsMessage.CardPayment,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
        val occurredAt = occurredMillis(payment, receivedAt)
        val candidates = db.smsDiagnosticDao().cancellationCandidates(
            cancellationExternalKey = key,
            amountMinor = payment.amountMinor,
            currency = payment.currency,
            cardLast4 = payment.cardLast4,
            occurredAt = occurredAt,
            fromMillis = occurredAt - CANCELLATION_WINDOW_MILLIS,
            toMillis = occurredAt + CANCELLATION_WINDOW_MILLIS,
        )
        val original = SmsCancellationMatcher.match(payment, occurredAt, candidates.filter { BankSmsBank.fromKey(it.externalKey) == bank })
        if (original == null) {
            val diagnostic = diagnosticFor(
                sms = payment,
                externalKey = key,
                outcome = SmsDiagnosticOutcome.ERROR,
                reason = SmsDiagnosticReason.CANCELLATION_TARGET_NOT_FOUND,
                receivedAt = receivedAt,
            )
            val id = if (persist) persistDiagnostic(diagnostic) else null
            return SmsImportResult(
                SmsDiagnosticOutcome.ERROR,
                diagnosticId = id,
                reason = SmsDiagnosticReason.CANCELLATION_TARGET_NOT_FOUND,
            )
        }
        if (!persist) return SmsImportResult(SmsDiagnosticOutcome.CANCELED)

        original.transactionId?.let { transactionId ->
            val transaction = db.transactionDao().byId(transactionId)
            // Reconciliation may have turned the original SMS operation into statement truth while the
            // diagnostic kept pointing at the same row. A later SMS cancellation is evidence, not
            // authority to erase bank-statement truth; its eventual reversal belongs to the next
            // statement. An SMS-sourced row can still be withdrawn here regardless of UI status.
            if (transaction?.source == TxSource.SMS) {
                db.transactionDao().update(
                    transaction.copy(
                        isVoided = true,
                        canceledBySmsExternalKey = key,
                    ),
                )
            }
        }
        db.smsDiagnosticDao().update(
            original.copy(
                outcome = SmsDiagnosticOutcome.CANCELED,
                reason = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val diagnostic = diagnosticFor(
            sms = payment,
            externalKey = key,
            outcome = SmsDiagnosticOutcome.CANCELED,
            reason = null,
            receivedAt = receivedAt,
            accountId = original.accountId,
            transactionId = original.transactionId,
        )
        return SmsImportResult(
            SmsDiagnosticOutcome.CANCELED,
            persistDiagnostic(diagnostic),
            original.transactionId,
        )
    }

    private suspend fun attachBankHold(sms: BankSmsMessage.Sms, key: String, receivedAt: Long, persist: Boolean): SmsImportResult? {
        if (bank != BankSmsBank.TBC || sms !is BankSmsMessage.CardPayment) return null
        val time = occurredMillis(sms, receivedAt)
        val start = time / 60000 * 60000
        val mapped = db.accountDao().byCardAndCurrency(sms.cardLast4, sms.currency).map { it.id }
        val candidates = db.bankHoldDao().inWindow(sms.currency, start, start + 59999).filter { hold ->
            hold.amountMinor == -sms.amountMinor &&
                dev.whekin.whfin.data.importer.MerchantNormalizer.equivalent(hold.merchant, sms.merchantRaw) &&
                (hold.cardLast4 == sms.cardLast4 || hold.cardLast4 == null && hold.accountId in mapped) &&
                (mapped.isEmpty() || hold.accountId in mapped) &&
                db.smsDiagnosticDao().forTransaction(hold.transactionId).none {
                    it.externalKey != key && it.externalKey.startsWith("sms|tbc|push|") == key.startsWith("sms|tbc|push|")
                }
        }.distinctBy { it.transactionId }
        if (candidates.isEmpty()) return null
        if (candidates.size > 1) {
            val diagnostic = diagnosticFor(sms, key, SmsDiagnosticOutcome.UNRECOGNIZED, SmsDiagnosticReason.PARSE_FAILURE, receivedAt)
            return SmsImportResult(SmsDiagnosticOutcome.UNRECOGNIZED, if (persist) persistDiagnostic(diagnostic) else null)
        }
        val hold = candidates.single()
        val row = db.transactionDao().byId(hold.transactionId) ?: return null
        // Attaching evidence cannot resurrect an owner-voided hold or a settled purchase.
        val diagnostic = diagnosticFor(sms, key, SmsDiagnosticOutcome.ATTACHED, null, receivedAt, row.accountId, row.id)
        return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, if (persist) persistDiagnostic(diagnostic) else null, row.id)
    }

    internal suspend fun attachUnroutedToHolds() {
        for (diagnostic in db.smsDiagnosticDao().unrouted()) {
            if (BankSmsBank.fromKey(diagnostic.externalKey) != bank) continue
            val sms = diagnostic.toParsedSms() ?: continue
            attachBankHold(sms, diagnostic.externalKey, diagnostic.receivedAt, true)
        }
    }

    private suspend fun crossChannelCard(sms: BankSmsMessage.Sms, key: String, receivedAt: Long, persist: Boolean): SmsImportResult? {
        if (bank == BankSmsBank.TBC && sms is BankSmsMessage.CardPayment) {
            val time = occurredMillis(sms, receivedAt)
            val start = time / 60_000L * 60_000L
            val push = key.startsWith("sms|tbc|push|")
            val all = db.smsDiagnosticDao().matchingImported(SmsDiagnosticKind.CARD_PAYMENT, sms.amountMinor,
                sms.currency, time, start, start + 59_999).filter {
                BankSmsBank.fromKey(it.externalKey) == bank && it.cardLast4 == sms.cardLast4 &&
                    dev.whekin.whfin.data.importer.MerchantNormalizer.equivalent(it.counterparty, sms.merchantRaw)
            }
            val opposite = all.filter { it.externalKey.startsWith("sms|tbc|push|") != push }.distinctBy { it.transactionId }
            val eligible = opposite.filter { candidate -> all.none {
                it.transactionId == candidate.transactionId && it.externalKey.startsWith("sms|tbc|push|") == push
            } }
            if (eligible.size == 1) {
                val existing = eligible.single()
                val diagnostic = diagnosticFor(sms, key, SmsDiagnosticOutcome.ATTACHED, null, receivedAt,
                    existing.accountId, existing.transactionId)
                return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, if (persist) persistDiagnostic(diagnostic) else null, existing.transactionId)
            }
            if (eligible.size > 1) {
                val diagnostic = diagnosticFor(sms, key, SmsDiagnosticOutcome.UNRECOGNIZED, SmsDiagnosticReason.PARSE_FAILURE, receivedAt)
                return SmsImportResult(SmsDiagnosticOutcome.UNRECOGNIZED, if (persist) persistDiagnostic(diagnostic) else null)
            }
        }
        return null
    }

    private suspend fun evaluateParsed(
        sms: BankSmsMessage.Sms,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
        db.smsDiagnosticDao().byExternalKey(key)?.takeIf {
            it.outcome == SmsDiagnosticOutcome.CANCELED
        }?.let { canceled ->
            return SmsImportResult(
                outcome = SmsDiagnosticOutcome.CANCELED,
                diagnosticId = canceled.id.takeIf { persist },
                transactionId = canceled.transactionId,
            )
        }
        // Message identity survives settlement, which replaces the transaction's external key.
        // Re-reading a receipt must not erase its durable diagnostic link or create new money.
        db.smsDiagnosticDao().byExternalKey(key)?.let { evidence ->
            evidence.transactionId?.let { id ->
                var linked = db.transactionDao().byId(id)
                val seen = mutableSetOf<Long>()
                while (linked?.mergedIntoTransactionId != null && seen.add(linked.id))
                    linked = db.transactionDao().byId(requireNotNull(linked.mergedIntoTransactionId))
                linked?.let { survivor ->
                    if (persist && evidence.transactionId != survivor.id)
                        db.smsDiagnosticDao().update(evidence.copy(transactionId = survivor.id))
                    return SmsImportResult(SmsDiagnosticOutcome.DUPLICATE, evidence.id.takeIf { persist }, survivor.id)
                }
            }
        }
        attachBankHold(sms, key, receivedAt, persist)?.let { return it }
        crossChannelCard(sms, key, receivedAt, persist)?.let { return it }
        db.transactionDao().byExternalKey(key)?.let { existing ->
            val diagnostic = diagnosticFor(
                sms = sms,
                externalKey = key,
                outcome = SmsDiagnosticOutcome.DUPLICATE,
                reason = null,
                receivedAt = receivedAt,
                accountId = existing.accountId,
                transactionId = existing.id,
            )
            val id = if (persist) persistDiagnostic(diagnostic) else null
            if (persist) pairDepositTransfer(diagnostic.copy(id = requireNotNull(id)))
            return SmsImportResult(SmsDiagnosticOutcome.DUPLICATE, id, existing.id)
        }

        if (sms is BankSmsMessage.OwnTransfer || sms is BankSmsMessage.CurrencyExchange) {
            return evaluateGroupedParsed(sms, key, receivedAt, persist)
        }

        return when (val resolution = resolveAccount(sms, occurredMillis(sms, receivedAt))) {
            is AccountResolution.Found -> {
                val evidenceDiagnostic = diagnosticFor(
                    sms = sms,
                    externalKey = key,
                    outcome = SmsDiagnosticOutcome.ATTACHED,
                    reason = null,
                    receivedAt = receivedAt,
                    accountId = resolution.account.id,
                )
                statementEvidence.find(evidenceDiagnostic, listOf(resolution.account))?.let { existing ->
                    if (!persist) {
                        return SmsImportResult(
                            SmsDiagnosticOutcome.ATTACHED,
                            transactionId = existing.transaction.id,
                        )
                    }
                    val diagnostic = evidenceDiagnostic.copy(transactionId = existing.transaction.id)
                    val id = persistDiagnostic(diagnostic)
                    return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, id, existing.transaction.id)
                }
                if (isCoveredByStatement(resolution.account.id, evidenceDiagnostic.occurredAt)) {
                    return unwritten(evidenceDiagnostic, persist)
                }
                if (!persist) return SmsImportResult(SmsDiagnosticOutcome.IMPORTED)
                val transactionId = insertTransaction(sms, resolution.account, key, receivedAt)
                val outcome = if (transactionId > 0) SmsDiagnosticOutcome.IMPORTED else SmsDiagnosticOutcome.DUPLICATE
                val resolvedTransactionId = transactionId.takeIf { it > 0 }
                    ?: db.transactionDao().byExternalKey(key)?.id
                val diagnostic = diagnosticFor(
                    sms = sms,
                    externalKey = key,
                    outcome = outcome,
                    reason = null,
                    receivedAt = receivedAt,
                    accountId = resolution.account.id,
                    transactionId = resolvedTransactionId,
                )
                val id = persistDiagnostic(diagnostic)
                pairDepositTransfer(diagnostic.copy(id = id))
                SmsImportResult(outcome, id, resolvedTransactionId)
            }
            is AccountResolution.NeedsChoice -> {
                val unrouted = diagnosticFor(
                    sms = sms,
                    externalKey = key,
                    outcome = resolution.outcome,
                    reason = resolution.reason,
                    receivedAt = receivedAt,
                )
                // The statement already filed this payment under one account: ask it before asking
                // the user, or the same money gets a second row in whichever ledger they pick.
                attachToStatement(unrouted, persist)?.let { return it }
                val id = if (persist) persistDiagnostic(unrouted) else null
                SmsImportResult(resolution.outcome, id, reason = resolution.reason)
            }
        }
    }

    private suspend fun evaluateGroupedParsed(
        sms: BankSmsMessage.Sms,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
        val resolution = resolveGroupedAccounts(sms)
        if (resolution == null) {
            val sourceCurrency = sms.currency
            val destinationCurrency = (sms as? BankSmsMessage.CurrencyExchange)?.receivedCurrency
                ?: sms.currency
            val eligible: (AccountEntity) -> Boolean = if (sms is BankSmsMessage.CurrencyExchange) {
                ::isCurrencyExchangeLedger
            } else {
                { it.type in setOf(AccountType.BANK, AccountType.SAVINGS) }
            }
            val hasSource = db.accountDao().bankAccountsByCurrency(sourceCurrency).any(eligible)
            val hasDestination = db.accountDao().bankAccountsByCurrency(destinationCurrency).any(eligible)
            val reason = if (!hasSource || !hasDestination) {
                SmsDiagnosticReason.NO_ACCOUNT
            } else {
                SmsDiagnosticReason.MULTIPLE_ACCOUNTS
            }
            val diagnostic = diagnosticFor(
                sms = sms,
                externalKey = key,
                outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                reason = reason,
                receivedAt = receivedAt,
            )
            attachToStatement(diagnostic, persist)?.let { return it }
            val id = if (persist) persistDiagnostic(diagnostic) else null
            return SmsImportResult(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, id, reason = reason)
        }
        val groupedDiagnostic = diagnosticFor(
            sms = sms,
            externalKey = key,
            outcome = SmsDiagnosticOutcome.ATTACHED,
            reason = null,
            receivedAt = receivedAt,
            accountId = resolution.from.id,
        )
        // Both IBANs were known, so this message routed cleanly — and wrote a second pair of legs on
        // top of the transfer the statement had already filed, doubling both balances. Routing was
        // never the question here; whether the operation is already in the ledger is.
        statementEvidence.find(groupedDiagnostic, listOf(resolution.from, resolution.to))?.let { existing ->
            if (!persist) {
                return SmsImportResult(
                    SmsDiagnosticOutcome.ATTACHED,
                    transactionId = existing.transaction.id,
                )
            }
            val id = persistDiagnostic(groupedDiagnostic.copy(transactionId = existing.transaction.id))
            return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, id, existing.transaction.id)
        }
        if (isCoveredByStatement(resolution.from.id, groupedDiagnostic.occurredAt)) {
            return unwritten(groupedDiagnostic, persist)
        }
        if (!persist) return SmsImportResult(SmsDiagnosticOutcome.IMPORTED)
        val transactionId = insertGroupedTransactions(
            sms = sms,
            from = resolution.from,
            to = resolution.to,
            key = key,
            receivedAt = receivedAt,
        )
        val diagnostic = diagnosticFor(
            sms = sms,
            externalKey = key,
            outcome = SmsDiagnosticOutcome.IMPORTED,
            reason = null,
            receivedAt = receivedAt,
            accountId = resolution.from.id,
            transactionId = transactionId,
        )
        val id = persistDiagnostic(diagnostic)
        return SmsImportResult(SmsDiagnosticOutcome.IMPORTED, id, transactionId)
    }

    private suspend fun resolveAccount(
        sms: BankSmsMessage.Sms,
        atMillis: Long,
    ): AccountResolution {
        val currency = sms.balanceCurrency ?: sms.currency
        // A refund names its card and no account; the card is what says where the money went back to.
        val cardLast4 = (sms as? BankSmsMessage.CardPayment)?.cardLast4
            ?: (sms as? BankSmsMessage.IncomingTransfer)?.cardLast4
        if (cardLast4 != null) {
            val mapped = db.accountDao().byCardAndCurrency(cardLast4, currency).filter { bank.accepts(db, it) }
            return when (mapped.size) {
                1 -> AccountResolution.Found(mapped.single())
                0 -> AccountResolution.NeedsChoice(
                    SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
                    SmsDiagnosticReason.NO_CARD_MAPPING,
                )
                else -> AccountResolution.NeedsChoice(
                    SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                    SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
                )
            }
        }
        val candidates = db.accountDao().bankAccountsByCurrency(currency).filter { bank.accepts(db, it) }
        // Interest names its deposit. An identity the bank prints beats arithmetic over the ledger,
        // which only holds while every row since the last declared balance is present.
        (sms as? BankSmsMessage.InterestAccrual)?.depositNumber?.let { number ->
            candidates.singleOrNull { it.depositNumber == number }
                ?.let { return AccountResolution.Found(it) }
        }
        val pairedAccount = pairedAccountHint(sms)
        val narrowed = when (sms) {
            // Interest is paid on a deposit, and which accounts are deposits is already stated — by
            // the bank product, never by the fund role: a demand deposit paying on each day's balance
            // is money its owner spends from, so it is rightly marked available and is still a deposit.
            is BankSmsMessage.DepositTopUp, is BankSmsMessage.InterestAccrual ->
                candidates.filter { candidate ->
                    isDepositLedger(candidate) &&
                        candidate.id != pairedAccount?.id &&
                        (pairedAccount?.groupId == null || candidate.groupId == pairedAccount.groupId)
                }
            is BankSmsMessage.OutgoingTransfer -> candidates.filter { candidate ->
                candidate.id != pairedAccount?.id &&
                    (pairedAccount?.groupId == null || candidate.groupId == pairedAccount.groupId)
            }
            else -> emptyList()
        }
        if (narrowed.size == 1) return AccountResolution.Found(narrowed.single())
        val pool = when (sms) {
            is BankSmsMessage.OutgoingTransfer,
            is BankSmsMessage.DepositTopUp,
            is BankSmsMessage.InterestAccrual,
            -> narrowed
            else -> candidates
        }
        accountAtDeclaredBalance(sms, pool, atMillis)?.let { return AccountResolution.Found(it) }
        if (sms is BankSmsMessage.DepositTopUp || sms is BankSmsMessage.InterestAccrual) {
            // The question is about deposits, so its emptiness is about deposits too: offering every
            // account of the currency asked the person to re-answer what they had already marked.
            return AccountResolution.NeedsChoice(
                SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                if (narrowed.isEmpty()) SmsDiagnosticReason.NO_ACCOUNT else SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            )
        }
        return when (candidates.size) {
            1 -> AccountResolution.Found(candidates.single())
            0 -> AccountResolution.NeedsChoice(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, SmsDiagnosticReason.NO_ACCOUNT)
            else -> AccountResolution.NeedsChoice(
                SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            )
        }
    }

    /**
     * The ledger whose balance the message itself names, when exactly one of them fits.
     *
     * Only a same-currency operation can be checked this way: a card charged abroad moves the ledger
     * by an amount the message never prints, so no arithmetic reaches the stated balance. A card is
     * left alone for a different reason — its message is asked about once in order to learn which
     * ledger the card belongs to, and a silent guess would trade that answer for one routed message
     * and keep asking forever.
     */
    private suspend fun accountAtDeclaredBalance(
        sms: BankSmsMessage.Sms,
        candidates: List<AccountEntity>,
        atMillis: Long,
    ): AccountEntity? {
        if (candidates.size < 2) return null
        val declared = sms.balanceMinor ?: return null
        val balanceCurrency = sms.balanceCurrency ?: return null
        if (!balanceCurrency.equals(sms.currency, ignoreCase = true)) return null
        val evidence = candidates.mapNotNull { account ->
            if (!account.currency.equals(balanceCurrency, ignoreCase = true)) return@mapNotNull null
            val anchor = db.transactionDao().latestDeclaredBalance(account.id, atMillis)
                ?: return@mapNotNull null
            DeclaredBalanceEvidence(
                accountId = account.id,
                anchorBalanceMinor = anchor.balanceAfterMinor ?: return@mapNotNull null,
                movedSinceMinor = db.transactionDao().sumSinceDeclaredBalance(
                    accountId = account.id,
                    anchorMillis = anchor.occurredAt,
                    anchorId = anchor.id,
                    atMillis = atMillis,
                ),
            )
        }
        val id = accountAtDeclaredBalance(evidence, ledgerDeltaMinor(sms), declared) ?: return null
        return candidates.firstOrNull { it.id == id }
    }

    private suspend fun resolveGroupedAccounts(sms: BankSmsMessage.Sms): GroupedAccountResolution? {
        return when (sms) {
            is BankSmsMessage.OwnTransfer -> {
                val from = db.accountDao().byIbanAndCurrency(sms.fromIban, sms.currency)
                val to = db.accountDao().byIbanAndCurrency(sms.toIban, sms.currency)
                if (from != null && to != null && bank.accepts(db, from) && bank.accepts(db, to) && validGroupedAccounts(sms, from, to)) {
                    GroupedAccountResolution(from, to)
                } else {
                    null
                }
            }
            is BankSmsMessage.CurrencyExchange -> {
                val sources = db.accountDao().bankAccountsByCurrency(sms.currency).filter { bank.accepts(db, it) }
                    .filter(::isCurrencyExchangeLedger)
                val destinations = db.accountDao().bankAccountsByCurrency(sms.receivedCurrency).filter { bank.accepts(db, it) }
                    .filter(::isCurrencyExchangeLedger)
                val pairs = sources.flatMap { from ->
                    destinations.mapNotNull { to -> GroupedAccountResolution(from, to)
                        .takeIf { validGroupedAccounts(sms, from, to) } }
                }
                pairs.singleOrNull() ?: run {
                    val balance = sms.balanceMinor
                    val at = occurredMillis(sms, System.currentTimeMillis())
                    val targets = if (balance != null && sms.balanceCurrency == sms.receivedCurrency) {
                        val evidence = destinations.mapNotNull { target ->
                            val anchor = db.transactionDao().latestDeclaredBalance(target.id, at) ?: return@mapNotNull null
                            DeclaredBalanceEvidence(target.id, anchor.balanceAfterMinor!!,
                                db.transactionDao().sumSinceDeclaredBalance(target.id, anchor.occurredAt, anchor.id, at))
                        }
                        accountAtDeclaredBalance(evidence, sms.receivedAmountMinor, balance)
                    } else null
                    pairs.filter { it.to.id == targets }.singleOrNull()
                }
            }
            else -> null
        }
    }

    private fun validGroupedAccounts(
        sms: BankSmsMessage.Sms,
        from: AccountEntity,
        to: AccountEntity,
    ): Boolean {
        val destinationCurrency = (sms as? BankSmsMessage.CurrencyExchange)?.receivedCurrency
            ?: sms.currency
        val eligibleTypes = if (sms is BankSmsMessage.CurrencyExchange) {
            isCurrencyExchangeLedger(from) && isCurrencyExchangeLedger(to)
        } else {
            from.type in setOf(AccountType.BANK, AccountType.SAVINGS) &&
                to.type in setOf(AccountType.BANK, AccountType.SAVINGS)
        }
        return from.id != to.id &&
            from.groupId != null &&
            from.groupId == to.groupId &&
            from.currency == sms.currency &&
            to.currency == destinationCurrency &&
            eligibleTypes
    }

    private suspend fun groupedBalanceAccount(sms: BankSmsMessage.Sms, from: AccountEntity, to: AccountEntity, at: Long): Long? {
        val declared = sms.balanceMinor ?: return null
        val currency = sms.balanceCurrency ?: return null
        if (from.currency != to.currency) return listOf(from, to).singleOrNull { it.currency == currency }?.id
        if (currency != from.currency) return null
        val after = listOf(from to -sms.amountMinor, to to sms.amountMinor).associate { (account, delta) ->
            val anchor = db.transactionDao().latestDeclaredBalance(account.id, at)
            account.id to anchor?.let { a -> runCatching { Math.addExact(Math.addExact(a.balanceAfterMinor!!,
                db.transactionDao().sumSinceDeclaredBalance(account.id, a.occurredAt, a.id, at)), delta) }.getOrNull() }
        }
        return declaredBalanceSide(after, declared)
    }

    private suspend fun insertGroupedTransactions(
        sms: BankSmsMessage.Sms,
        from: AccountEntity,
        to: AccountEntity,
        key: String,
        receivedAt: Long,
        status: TxStatus = TxStatus.CONFIRMED,
    ): Long {
        require(validGroupedAccounts(sms, from, to))
        val groupType = if (sms is BankSmsMessage.CurrencyExchange) {
            TransferGroupType.CONVERSION
        } else {
            TransferGroupType.TRANSFER
        }
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(
                type = groupType,
                note = if (groupType == TransferGroupType.CONVERSION) {
                    "${bank.provider} SMS exchange"
                } else {
                    "${bank.provider} SMS transfer"
                },
                createdAt = System.currentTimeMillis(),
            ),
        )
        val occurredAt = occurredMillis(sms, receivedAt)
        val destinationAmount = when (sms) {
            is BankSmsMessage.CurrencyExchange -> sms.receivedAmountMinor
            else -> sms.amountMinor
        }
        val ownTransfer = sms as? BankSmsMessage.OwnTransfer
        val balanceAccount = groupedBalanceAccount(sms, from, to, occurredAt)
        val ids = db.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    accountId = from.id,
                    amountMinor = -kotlin.math.abs(sms.amountMinor),
                    currency = from.currency,
                    occurredAt = occurredAt,
                    counterpartyIban = ownTransfer?.toIban,
                    status = status,
                    source = TxSource.SMS,
                    transferGroupId = groupId,
                    isTransfer = true,
                    balanceAfterMinor = sms.balanceMinor
                        .takeIf { balanceAccount == from.id },
                    externalKey = key,
                    createdAt = System.currentTimeMillis(),
                ),
                TransactionEntity(
                    accountId = to.id,
                    amountMinor = kotlin.math.abs(destinationAmount),
                    currency = to.currency,
                    occurredAt = occurredAt,
                    counterpartyIban = ownTransfer?.fromIban,
                    status = status,
                    source = TxSource.SMS,
                    transferGroupId = groupId,
                    isTransfer = true,
                    balanceAfterMinor = sms.balanceMinor
                        .takeIf { balanceAccount == to.id },
                    externalKey = "$key|to",
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        check(ids.size == 2 && ids.all { it > 0 }) {
            "Grouped SMS must create both transfer legs atomically"
        }
        return ids.first()
    }

    /**
     * The category that follows from what the bank called this operation.
     *
     * Null when the category does not exist yet: it is offered from the evidence of rows like this
     * one rather than seeded, so that a category the owner deleted cannot come back on its own.
     */
    private suspend fun operationCategory(sms: BankSmsMessage.Sms): Long? =
        OperationCategories.operationOf(diagnosticKind(sms))
            ?.let { OperationCategories.categoryFor(it, db.categoryDao().all()) }
            ?.id

    /** What kind of message this is, in the vocabulary the diagnostic keeps after the text is gone. */
    private fun diagnosticKind(sms: BankSmsMessage.Sms): SmsDiagnosticKind = when (sms) {
        is BankSmsMessage.CardPayment -> SmsDiagnosticKind.CARD_PAYMENT
        is BankSmsMessage.OutgoingTransfer -> SmsDiagnosticKind.OUTGOING_TRANSFER
        is BankSmsMessage.IncomingTransfer -> SmsDiagnosticKind.INCOMING_TRANSFER
        is BankSmsMessage.DepositTopUp -> SmsDiagnosticKind.DEPOSIT_TOP_UP
        is BankSmsMessage.OwnTransfer -> SmsDiagnosticKind.OWN_TRANSFER
        is BankSmsMessage.CurrencyExchange -> SmsDiagnosticKind.CURRENCY_EXCHANGE
        is BankSmsMessage.BillPayment -> SmsDiagnosticKind.BILL_PAYMENT
        is BankSmsMessage.CashDeposit -> SmsDiagnosticKind.CASH_DEPOSIT
        is BankSmsMessage.InterestAccrual -> SmsDiagnosticKind.INTEREST
    }

    private suspend fun insertTransaction(
        sms: BankSmsMessage.Sms,
        account: AccountEntity,
        key: String,
        receivedAt: Long,
        status: TxStatus = TxStatus.CONFIRMED,
    ): Long {
        val rawCounterparty = when (sms) {
            is BankSmsMessage.CardPayment -> sms.merchantRaw
            is BankSmsMessage.IncomingTransfer -> sms.senderName
            else -> null
        }
        val merchant = rawCounterparty?.let { resolveMerchant(it) }
        val accountAmount = if (sms.currency == account.currency) ledgerDeltaMinor(sms) else 0L
        return db.transactionDao().insert(
            TransactionEntity(
                accountId = account.id,
                amountMinor = accountAmount,
                currency = account.currency,
                origAmountMinor = sms.amountMinor.takeIf { sms.currency != account.currency },
                origCurrency = sms.currency.takeIf { sms.currency != account.currency },
                occurredAt = occurredMillis(sms, receivedAt),
                merchantId = merchant?.id,
                rawCounterparty = rawCounterparty,
                // The same order the statement importer uses: what the counterparty is known to mean
                // first, then what the bank said the operation is. Interest arriving by message used
                // to land blank while the identical statement row landed categorised.
                categoryId = merchant?.categoryId ?: operationCategory(sms),
                status = status,
                source = TxSource.SMS,
                isTransfer = false,
                balanceAfterMinor = sms.balanceMinor,
                externalKey = key,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Files a message the routing rules could not place against the row the statement already holds.
     *
     * Nothing is written to the ledger here — the transaction exists and is bank truth. The message
     * only stops being a question, and, when the pair is unambiguous, says which ledger the card it
     * names belongs to. That mapping is the whole reason a second message from the same card never
     * has to be asked about again.
     */
    private suspend fun attachToStatement(
        diagnostic: SmsDiagnosticEntity,
        persist: Boolean,
    ): SmsImportResult? {
        val match = statementEvidence.find(diagnostic) ?: return null
        if (!persist) {
            return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, transactionId = match.transaction.id)
        }
        val id = persistDiagnostic(
            diagnostic.copy(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                reason = null,
                accountId = match.account.id,
                transactionId = match.transaction.id,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (match.exact) learnCardMapping(diagnostic, match.account)
        return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, id, match.transaction.id)
    }

    /**
     * A statement never prints a card number and a card message never names an account; together
     * they do. Learning the pair here is what turns one matched purchase into every later message
     * from that card landing on its own.
     */
    private suspend fun learnCardMapping(diagnostic: SmsDiagnosticEntity, account: AccountEntity) {
        val last4 = diagnostic.cardLast4?.takeIf { it.matches(Regex("\\d{4}")) } ?: return
        if (account.groupId == null) return
        // An existing mapping is the user's own; a match is evidence, not grounds to overrule it.
        if (db.accountDao().byCardAndCurrency(last4, account.currency).any { bank.accepts(db, it) }) return
        db.paymentInstrumentDao().linkForAccounts(
            cardFamilyFor(account),
            last4,
            PaymentInstrumentType.PHYSICAL_CARD,
        )
    }

    /**
     * Whether an imported statement already covers this account on this day.
     *
     * Inside a covered period the statement is the whole truth of that account, so a message that
     * found no row there is not new money — it is a row the bank has not printed the same way, or
     * one this app failed to recognise. Writing it anyway is how one purchase became two. It stays
     * a visible question instead, and the next import attaches it.
     */
    private suspend fun isCoveredByStatement(accountId: Long, occurredAt: Long?): Boolean {
        val at = occurredAt ?: return false
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return db.statementImportDao().forAccount(accountId).filter {
            it.origin !in setOf(dev.whekin.whfin.data.db.StatementImportOrigin.TBC_SYNC, dev.whekin.whfin.data.db.StatementImportOrigin.TBC_HISTORY, dev.whekin.whfin.data.db.StatementImportOrigin.CREDO_API, dev.whekin.whfin.data.db.StatementImportOrigin.USER_OPENING)
        }.any { import ->
            val from = import.periodFrom?.let(LocalDate::ofEpochDay) ?: return@any false
            val to = import.periodTo?.let(LocalDate::ofEpochDay) ?: return@any false
            !day.isBefore(from) && !day.isAfter(to)
        }
    }

    /** Recorded, visible, and deliberately not in the ledger. */
    private suspend fun unwritten(
        diagnostic: SmsDiagnosticEntity,
        persist: Boolean,
    ): SmsImportResult {
        val unrouted = diagnostic.copy(
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            reason = SmsDiagnosticReason.STATEMENT_COVERS_PERIOD,
            accountId = null,
            transactionId = null,
        )
        val id = if (persist) persistDiagnostic(unrouted) else null
        return SmsImportResult(
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            diagnosticId = id,
            reason = SmsDiagnosticReason.STATEMENT_COVERS_PERIOD,
        )
    }

    /**
     * Learns which ledger each card belongs to from messages the phone already holds.
     *
     * Nothing about these messages is stored: they are classified, matched against statements that
     * are already in the ledger, and thrown away. The only thing written is the card link — the one
     * fact neither side knows alone and the one that stops every later message from that card from
     * becoming a question. Importing the messages themselves stays an explicit, previewed action.
     *
     * @return how many cards were newly linked.
     */
    suspend fun learnCardsFrom(bodies: List<String>): Int = db.withTransaction {
        var learned = 0
        // Positive lookups stay valid throughout this transaction. Do not cache misses: a later
        // message can provide the first statement evidence for a previously unknown card.
        val knownCards = mutableSetOf<Pair<String, String>>()
        bodies.forEach { body ->
            val payment = (bank.classify(body) as? BankSmsMessage.Classification.Parsed)?.sms as? BankSmsMessage.CardPayment ?: return@forEach
            val card = payment.cardLast4 to (payment.balanceCurrency ?: payment.currency)
            if (card in knownCards) return@forEach
            if (db.accountDao().byCardAndCurrency(
                    payment.cardLast4,
                    payment.balanceCurrency ?: payment.currency,
                ).any { bank.accepts(db, it) }
            ) {
                knownCards += card
                return@forEach
            }
            val probe = diagnosticFor(
                sms = payment,
                externalKey = bank.key(body),
                outcome = SmsDiagnosticOutcome.ATTACHED,
                reason = null,
                receivedAt = System.currentTimeMillis(),
            )
            val match = statementEvidence.find(probe)?.takeIf { it.exact } ?: return@forEach
            learnCardMapping(probe, match.account)
            knownCards += card
            learned += 1
        }
        learned
    }

    /**
     * Re-reads every message still waiting on the user against the statements imported since.
     *
     * The two layers arrive in either order. A statement imported after a message already adopts it,
     * but a message read after its statement had nowhere to look — which is exactly what happens
     * when a bank connection back-fills a year and the phone's inbox is scanned afterwards.
     *
     * @return how many questions stopped being questions.
     */
    suspend fun attachUnroutedToStatements(): Int = BankSmsBank.entries.sumOf {
        SmsTransactionImporter(db, it).attachBankUnroutedToStatements()
    }

    private suspend fun attachBankUnroutedToStatements(): Int = db.withTransaction {
        var resolved = 0
        db.smsDiagnosticDao().unrouted().forEach { diagnostic ->
            if (BankSmsBank.fromKey(diagnostic.externalKey) != bank) return@forEach
            val attached = attachToStatement(diagnostic, persist = true)
            if (attached != null) {
                resolved += 1
                return@forEach
            }
            // A card mapping learned a moment ago can place messages the statement never covered.
            // Transfers and conversions are two ledgers at once and keep their own resolver: a
            // single-account path through here would write half of one.
            if (diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
                diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
            ) {
                return@forEach
            }
            val sms = diagnostic.toParsedSms() ?: return@forEach
            val resolution = resolveAccount(
                sms,
                diagnostic.occurredAt ?: diagnostic.receivedAt,
            )
            if (
                resolution is AccountResolution.Found &&
                !isCoveredByStatement(resolution.account.id, diagnostic.occurredAt)
            ) {
                resolveIntoAccount(diagnostic, resolution.account, TxStatus.CONFIRMED)
                resolved += 1
            }
        }
        resolved
    }

    private fun diagnosticFor(
        sms: BankSmsMessage.Sms,
        externalKey: String,
        outcome: SmsDiagnosticOutcome,
        reason: SmsDiagnosticReason?,
        receivedAt: Long,
        accountId: Long? = null,
        transactionId: Long? = null,
    ): SmsDiagnosticEntity = SmsDiagnosticEntity(
        externalKey = externalKey,
        kind = diagnosticKind(sms),
        outcome = outcome,
        reason = reason,
        receivedAt = receivedAt,
        occurredAt = occurredMillis(sms, receivedAt),
        amountMinor = sms.amountMinor,
        currency = sms.currency,
        secondaryAmountMinor = (sms as? BankSmsMessage.CurrencyExchange)?.receivedAmountMinor,
        secondaryCurrency = (sms as? BankSmsMessage.CurrencyExchange)?.receivedCurrency,
        balanceMinor = sms.balanceMinor,
        balanceCurrency = sms.balanceCurrency,
        cardLast4 = (sms as? BankSmsMessage.CardPayment)?.cardLast4
            ?: (sms as? BankSmsMessage.IncomingTransfer)?.cardLast4,
        depositNumber = (sms as? BankSmsMessage.InterestAccrual)?.depositNumber,
        counterparty = when (sms) {
            is BankSmsMessage.CardPayment -> sms.merchantRaw
            is BankSmsMessage.IncomingTransfer -> sms.senderName
            else -> null
        },
        fromIban = (sms as? BankSmsMessage.OwnTransfer)?.fromIban,
        toIban = (sms as? BankSmsMessage.OwnTransfer)?.toIban,
        transactionId = transactionId,
        accountId = accountId,
        updatedAt = System.currentTimeMillis(),
    )

    private fun basicDiagnostic(
        externalKey: String,
        kind: SmsDiagnosticKind,
        outcome: SmsDiagnosticOutcome,
        reason: SmsDiagnosticReason,
        receivedAt: Long,
    ) = SmsDiagnosticEntity(
        externalKey = externalKey,
        kind = kind,
        outcome = outcome,
        reason = reason,
        receivedAt = receivedAt,
        updatedAt = System.currentTimeMillis(),
    )

    private suspend fun persistDiagnostic(item: SmsDiagnosticEntity): Long {
        val existing = db.smsDiagnosticDao().byExternalKey(item.externalKey)
        if (existing == null) return db.smsDiagnosticDao().insert(item)
        db.smsDiagnosticDao().update(
            item.copy(id = existing.id, receivedAt = minOf(existing.receivedAt, item.receivedAt)),
        )
        return existing.id
    }

    private suspend fun updateFailure(
        item: SmsDiagnosticEntity,
        reason: SmsDiagnosticReason,
    ): SmsImportResult {
        db.smsDiagnosticDao().update(
            item.copy(
                outcome = SmsDiagnosticOutcome.ERROR,
                reason = reason,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return SmsImportResult(SmsDiagnosticOutcome.ERROR, item.id, reason = reason)
    }

    private fun SmsDiagnosticEntity.toParsedSms(): BankSmsMessage.Sms? {
        val amount = amountMinor ?: return null
        val valueCurrency = currency ?: return null
        val instant = occurredAt?.let(Instant::ofEpochMilli) ?: return null
        val timestamp = LocalDateTime.ofInstant(instant, zone)
        return when (kind) {
            SmsDiagnosticKind.CARD_PAYMENT -> BankSmsMessage.CardPayment(
                amount, valueCurrency, cardLast4 ?: return null, counterparty ?: return null, null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.OUTGOING_TRANSFER -> BankSmsMessage.OutgoingTransfer(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.INCOMING_TRANSFER -> BankSmsMessage.IncomingTransfer(
                amount, valueCurrency, counterparty, cardLast4, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.BILL_PAYMENT -> BankSmsMessage.BillPayment(
                amount, valueCurrency, counterparty, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.CASH_DEPOSIT -> BankSmsMessage.CashDeposit(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.INTEREST -> BankSmsMessage.InterestAccrual(
                amount, valueCurrency, depositNumber, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.DEPOSIT_TOP_UP -> BankSmsMessage.DepositTopUp(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.OWN_TRANSFER -> BankSmsMessage.OwnTransfer(
                amount, valueCurrency, fromIban ?: return null, toIban ?: return null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.CURRENCY_EXCHANGE -> BankSmsMessage.CurrencyExchange(
                amount, valueCurrency, secondaryAmountMinor ?: return null, secondaryCurrency ?: return null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.IGNORED, SmsDiagnosticKind.UNRECOGNIZED -> null
        }
    }

    private suspend fun resolveMerchant(raw: String): MerchantEntity? {
        return MerchantCategorizer.resolve(db, raw)
    }

    private suspend fun cardFamilyFor(account: AccountEntity): List<AccountEntity> {
        val groupId = account.groupId ?: return listOf(account)
        return db.accountDao().byGroup(groupId).filter { candidate ->
            if (account.iban != null) candidate.iban == account.iban else candidate.id == account.id
        }.ifEmpty { listOf(account) }
    }

    private suspend fun pairedAccountHint(sms: BankSmsMessage.Sms): AccountEntity? {
        val oppositeKind = when (sms) {
            is BankSmsMessage.OutgoingTransfer -> SmsDiagnosticKind.DEPOSIT_TOP_UP
            is BankSmsMessage.DepositTopUp -> SmsDiagnosticKind.OUTGOING_TRANSFER
            else -> return null
        }
        val occurredAt = occurredMillis(sms, System.currentTimeMillis())
        val accounts = db.smsDiagnosticDao().matchingImported(
            kind = oppositeKind,
            amountMinor = sms.amountMinor,
            currency = sms.currency,
            occurredAt = occurredAt,
            fromMillis = occurredAt - DEPOSIT_PAIR_WINDOW_MILLIS,
            toMillis = occurredAt + DEPOSIT_PAIR_WINDOW_MILLIS,
        ).filter { BankSmsBank.fromKey(it.externalKey) == bank }.mapNotNull { it.accountId }
            .distinct()
            .mapNotNull { db.accountDao().byId(it) }
        return accounts.singleOrNull()
    }

    private suspend fun pairDepositTransfer(current: SmsDiagnosticEntity) {
        val oppositeKind = when (current.kind) {
            SmsDiagnosticKind.OUTGOING_TRANSFER -> SmsDiagnosticKind.DEPOSIT_TOP_UP
            SmsDiagnosticKind.DEPOSIT_TOP_UP -> SmsDiagnosticKind.OUTGOING_TRANSFER
            else -> return
        }
        val currentTransactionId = current.transactionId ?: return
        val occurredAt = current.occurredAt ?: return
        val amountMinor = current.amountMinor ?: return
        val currency = current.currency ?: return
        val currentTransaction = db.transactionDao().byId(currentTransactionId) ?: return
        if (currentTransaction.transferGroupId != null) return
        val currentAccount = db.accountDao().byId(currentTransaction.accountId) ?: return
        val matches = db.smsDiagnosticDao().matchingImported(
            kind = oppositeKind,
            amountMinor = amountMinor,
            currency = currency,
            occurredAt = occurredAt,
            fromMillis = occurredAt - DEPOSIT_PAIR_WINDOW_MILLIS,
            toMillis = occurredAt + DEPOSIT_PAIR_WINDOW_MILLIS,
            excludeId = current.id,
        ).mapNotNull { diagnostic ->
            val transaction = diagnostic.transactionId?.let { db.transactionDao().byId(it) }
                ?: return@mapNotNull null
            val account = db.accountDao().byId(transaction.accountId) ?: return@mapNotNull null
            if (transaction.transferGroupId != null || transaction.accountId == currentTransaction.accountId) {
                return@mapNotNull null
            }
            if (currentAccount.groupId == null || account.groupId != currentAccount.groupId) return@mapNotNull null
            if (transaction.amountMinor != -currentTransaction.amountMinor) return@mapNotNull null
            transaction
        }
        val match = matches.singleOrNull() ?: return
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(
                type = TransferGroupType.SAVINGS,
                note = "Credo deposit transfer",
                createdAt = System.currentTimeMillis(),
            ),
        )
        db.transactionDao().attachToTransferGroup(listOf(currentTransaction.id, match.id), groupId)
    }

    private sealed interface AccountResolution {
        data class Found(val account: AccountEntity) : AccountResolution
        data class NeedsChoice(
            val outcome: SmsDiagnosticOutcome,
            val reason: SmsDiagnosticReason,
        ) : AccountResolution
    }

    private data class GroupedAccountResolution(
        val from: AccountEntity,
        val to: AccountEntity,
    )

    private companion object {
        const val DEPOSIT_PAIR_WINDOW_MILLIS = 2L * 60 * 1_000
    }
}

internal fun smsExternalKey(body: String): String = "sms|" + MessageDigest.getInstance("SHA-256")
    .digest(body.trim().toByteArray())
    .take(12)
    .joinToString("") { "%02x".format(it) }
