package dev.whekin.whfin.data.sms

import androidx.room.withTransaction
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
import dev.whekin.whfin.data.importer.MerchantNormalizer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

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

/** Converts a Credo SMS classification into a visible diagnostic and, when possible, an active transaction. */
class SmsTransactionImporter(private val db: WhfinDatabase) {
    private val zone = ZoneId.of("Asia/Tbilisi")
    private val statementEvidence = SmsStatementEvidence(db, zone)

    /** A reversal follows its payment closely; a wider window would retract an unrelated purchase. */
    private val CANCELLATION_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000

    suspend fun preview(body: String, receivedAt: Long = System.currentTimeMillis()): SmsImportResult =
        db.withTransaction {
            evaluate(CredoSmsParser.classify(body), smsExternalKey(body), receivedAt, persist = false)
        }

    suspend fun import(body: String, receivedAt: Long = System.currentTimeMillis()): SmsImportResult = try {
        db.withTransaction {
            evaluate(CredoSmsParser.classify(body), smsExternalKey(body), receivedAt, persist = true)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        val key = smsExternalKey(body)
        val classification = CredoSmsParser.classify(body)
        val diagnosticId = runCatching {
            db.withTransaction {
                val diagnostic = when (classification) {
                    is CredoSmsParser.Classification.Parsed -> diagnosticFor(
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
        val expectedCurrency = diagnostic.balanceCurrency ?: diagnostic.currency
        if (expectedCurrency == null || account.currency != expectedCurrency) {
            return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        }

        val cardLast4 = diagnostic.cardLast4
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
        db.smsDiagnosticDao().unresolvedCardPayments(cardLast4).forEach { queued ->
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

        findStatementEvidence(diagnostic, account)?.let { existing ->
            val saved = diagnostic.copy(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                reason = null,
                transactionId = existing.id,
                accountId = existing.accountId,
                updatedAt = System.currentTimeMillis(),
            )
            db.smsDiagnosticDao().update(saved)
            return SmsImportResult(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                diagnosticId = diagnostic.id,
                transactionId = existing.id,
            )
        }

        val sms = diagnostic.toParsedSms()
            ?: return updateFailure(diagnostic, SmsDiagnosticReason.PARSE_FAILURE)
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
        val sms = diagnostic.toParsedSms()
        if (sms !is CredoSmsParser.OwnTransfer && sms !is CredoSmsParser.CurrencyExchange) {
            return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.PARSE_FAILURE)
        }
        val from = db.accountDao().byId(fromAccountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        val to = db.accountDao().byId(toAccountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        if (!validGroupedAccounts(sms, from, to)) {
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
    private fun occurredMillis(sms: CredoSmsParser.Sms, receivedAt: Long): Long =
        sms.timestamp?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt

    private suspend fun evaluate(
        classification: CredoSmsParser.Classification,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult = when (classification) {
        is CredoSmsParser.Classification.Canceled ->
            evaluateCanceled(classification.payment, key, receivedAt, persist)
        is CredoSmsParser.Classification.Ignored -> {
            val reason = when (classification.reason) {
                CredoSmsParser.IgnoreReason.OTP -> SmsDiagnosticReason.OTP
                CredoSmsParser.IgnoreReason.REJECTED -> SmsDiagnosticReason.REJECTED
                CredoSmsParser.IgnoreReason.UNRELATED -> SmsDiagnosticReason.UNRELATED
            }
            if (!classification.credoCandidate) {
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
        CredoSmsParser.Classification.Unrecognized -> {
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
        is CredoSmsParser.Classification.Parsed -> evaluateParsed(
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
        payment: CredoSmsParser.CardPayment,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
        val occurredAt = occurredMillis(payment, receivedAt)
        val original = db.smsDiagnosticDao().matchingCardPayment(
            amountMinor = payment.amountMinor,
            currency = payment.currency,
            cardLast4 = payment.cardLast4,
            occurredAt = occurredAt,
            fromMillis = occurredAt - CANCELLATION_WINDOW_MILLIS,
            toMillis = occurredAt + CANCELLATION_WINDOW_MILLIS,
        )
        if (!persist) return SmsImportResult(SmsDiagnosticOutcome.CANCELED)

        original?.transactionId?.let { transactionId ->
            val transaction = db.transactionDao().byId(transactionId)
            // Reconciliation may have turned the original SMS operation into statement truth while the
            // diagnostic kept pointing at the same row. A later SMS cancellation is evidence, not
            // authority to erase bank-statement truth; its eventual reversal belongs to the next
            // statement. An SMS-sourced row can still be withdrawn here regardless of UI status.
            if (transaction?.source == TxSource.SMS) {
                db.transactionDao().delete(transactionId)
                db.smsDiagnosticDao().update(
                    original.copy(
                        outcome = SmsDiagnosticOutcome.CANCELED,
                        transactionId = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        val diagnostic = diagnosticFor(
            sms = payment,
            externalKey = key,
            outcome = SmsDiagnosticOutcome.CANCELED,
            reason = null,
            receivedAt = receivedAt,
        )
        return SmsImportResult(SmsDiagnosticOutcome.CANCELED, persistDiagnostic(diagnostic))
    }

    private suspend fun evaluateParsed(
        sms: CredoSmsParser.Sms,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
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

        if (sms is CredoSmsParser.OwnTransfer || sms is CredoSmsParser.CurrencyExchange) {
            return evaluateGroupedParsed(sms, key, receivedAt, persist)
        }

        return when (val resolution = resolveAccount(sms)) {
            is AccountResolution.Found -> {
                val evidenceDiagnostic = diagnosticFor(
                    sms = sms,
                    externalKey = key,
                    outcome = SmsDiagnosticOutcome.ATTACHED,
                    reason = null,
                    receivedAt = receivedAt,
                    accountId = resolution.account.id,
                )
                findStatementEvidence(evidenceDiagnostic, resolution.account)?.let { existing ->
                    if (!persist) {
                        return SmsImportResult(
                            SmsDiagnosticOutcome.ATTACHED,
                            transactionId = existing.id,
                        )
                    }
                    val diagnostic = evidenceDiagnostic.copy(transactionId = existing.id)
                    val id = persistDiagnostic(diagnostic)
                    return SmsImportResult(SmsDiagnosticOutcome.ATTACHED, id, existing.id)
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
        sms: CredoSmsParser.Sms,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult {
        val resolution = resolveGroupedAccounts(sms)
        if (resolution == null) {
            val sourceCurrency = sms.currency
            val destinationCurrency = (sms as? CredoSmsParser.CurrencyExchange)?.receivedCurrency
                ?: sms.currency
            val eligible: (AccountEntity) -> Boolean = if (sms is CredoSmsParser.CurrencyExchange) {
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

    private suspend fun resolveAccount(sms: CredoSmsParser.Sms): AccountResolution {
        val currency = sms.balanceCurrency ?: sms.currency
        if (sms is CredoSmsParser.CardPayment) {
            val mapped = db.accountDao().byCardAndCurrency(sms.cardLast4, currency)
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
        val candidates = db.accountDao().bankAccountsByCurrency(currency)
        val pairedAccount = pairedAccountHint(sms)
        val narrowed = when (sms) {
            is CredoSmsParser.DepositTopUp -> candidates.filter { candidate ->
                (
                    candidate.type == AccountType.SAVINGS ||
                        candidate.bankProduct == BankProduct.DEMAND_DEPOSIT ||
                        candidate.bankProduct == BankProduct.TERM_DEPOSIT
                ) &&
                    candidate.id != pairedAccount?.id &&
                    (pairedAccount?.groupId == null || candidate.groupId == pairedAccount.groupId)
            }
            is CredoSmsParser.OutgoingTransfer -> candidates.filter { candidate ->
                candidate.id != pairedAccount?.id &&
                    (pairedAccount?.groupId == null || candidate.groupId == pairedAccount.groupId)
            }
            else -> emptyList()
        }
        if (narrowed.size == 1) return AccountResolution.Found(narrowed.single())
        if (sms is CredoSmsParser.DepositTopUp) {
            return AccountResolution.NeedsChoice(
                SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                if (candidates.isEmpty()) SmsDiagnosticReason.NO_ACCOUNT else SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
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

    private suspend fun resolveGroupedAccounts(sms: CredoSmsParser.Sms): GroupedAccountResolution? {
        return when (sms) {
            is CredoSmsParser.OwnTransfer -> {
                val from = db.accountDao().byIbanAndCurrency(sms.fromIban, sms.currency)
                val to = db.accountDao().byIbanAndCurrency(sms.toIban, sms.currency)
                if (from != null && to != null && validGroupedAccounts(sms, from, to)) {
                    GroupedAccountResolution(from, to)
                } else {
                    null
                }
            }
            is CredoSmsParser.CurrencyExchange -> {
                val sources = db.accountDao().bankAccountsByCurrency(sms.currency)
                    .filter(::isCurrencyExchangeLedger)
                val destinations = db.accountDao().bankAccountsByCurrency(sms.receivedCurrency)
                    .filter(::isCurrencyExchangeLedger)
                sources.flatMap { from ->
                    destinations.mapNotNull { to ->
                        GroupedAccountResolution(from, to)
                            .takeIf { validGroupedAccounts(sms, from, to) }
                    }
                }.singleOrNull()
            }
            else -> null
        }
    }

    private fun validGroupedAccounts(
        sms: CredoSmsParser.Sms,
        from: AccountEntity,
        to: AccountEntity,
    ): Boolean {
        val destinationCurrency = (sms as? CredoSmsParser.CurrencyExchange)?.receivedCurrency
            ?: sms.currency
        val eligibleTypes = if (sms is CredoSmsParser.CurrencyExchange) {
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

    private suspend fun insertGroupedTransactions(
        sms: CredoSmsParser.Sms,
        from: AccountEntity,
        to: AccountEntity,
        key: String,
        receivedAt: Long,
        status: TxStatus = TxStatus.CONFIRMED,
    ): Long {
        require(validGroupedAccounts(sms, from, to))
        val groupType = if (sms is CredoSmsParser.CurrencyExchange) {
            TransferGroupType.CONVERSION
        } else {
            TransferGroupType.TRANSFER
        }
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(
                type = groupType,
                note = if (groupType == TransferGroupType.CONVERSION) {
                    "Credo SMS exchange"
                } else {
                    "Credo SMS transfer"
                },
                createdAt = System.currentTimeMillis(),
            ),
        )
        val occurredAt = occurredMillis(sms, receivedAt)
        val destinationAmount = when (sms) {
            is CredoSmsParser.CurrencyExchange -> sms.receivedAmountMinor
            else -> sms.amountMinor
        }
        val ownTransfer = sms as? CredoSmsParser.OwnTransfer
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
                        .takeIf { sms !is CredoSmsParser.CurrencyExchange },
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
                        .takeIf { sms is CredoSmsParser.CurrencyExchange },
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

    private suspend fun insertTransaction(
        sms: CredoSmsParser.Sms,
        account: AccountEntity,
        key: String,
        receivedAt: Long,
        status: TxStatus = TxStatus.CONFIRMED,
    ): Long {
        val rawCounterparty = when (sms) {
            is CredoSmsParser.CardPayment -> sms.merchantRaw
            is CredoSmsParser.IncomingTransfer -> sms.senderName
            else -> null
        }
        val merchant = rawCounterparty?.let { resolveMerchant(it) }
        val amount = if (sms is CredoSmsParser.IncomingTransfer || sms is CredoSmsParser.DepositTopUp) {
            sms.amountMinor
        } else {
            -sms.amountMinor
        }
        val accountAmount = if (sms.currency == account.currency) amount else 0L
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
                categoryId = merchant?.categoryId,
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
        if (db.accountDao().byCardAndCurrency(last4, account.currency).isNotEmpty()) return
        db.paymentInstrumentDao().linkForAccounts(
            cardFamilyFor(account),
            last4,
            PaymentInstrumentType.PHYSICAL_CARD,
        )
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
    suspend fun attachUnroutedToStatements(): Int = db.withTransaction {
        var resolved = 0
        db.smsDiagnosticDao().unrouted().forEach { diagnostic ->
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
            val resolution = resolveAccount(sms)
            if (resolution is AccountResolution.Found) {
                resolveIntoAccount(diagnostic, resolution.account, TxStatus.CONFIRMED)
                resolved += 1
            }
        }
        resolved
    }

    private suspend fun findStatementEvidence(
        diagnostic: SmsDiagnosticEntity,
        account: AccountEntity,
    ): TransactionEntity? {
        if (diagnostic.kind != SmsDiagnosticKind.CARD_PAYMENT) return null
        val counterparty = diagnostic.counterparty ?: return null
        val occurredAt = diagnostic.occurredAt ?: return null
        val day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        val candidates = db.transactionDao().statementCandidates(
            accountId = account.id,
            fromMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        ).filter { candidate ->
            candidate.rawCounterparty?.let(MerchantNormalizer::normalize) ==
                MerchantNormalizer.normalize(counterparty)
        }
        val sameCurrencyAmount = diagnostic.amountMinor
            ?.takeIf { diagnostic.currency == account.currency }
            ?.let { -kotlin.math.abs(it) }
        return sameCurrencyAmount
            ?.let { amount -> candidates.filter { it.amountMinor == amount }.singleOrNull() }
            ?: candidates.singleOrNull()
    }

    private fun diagnosticFor(
        sms: CredoSmsParser.Sms,
        externalKey: String,
        outcome: SmsDiagnosticOutcome,
        reason: SmsDiagnosticReason?,
        receivedAt: Long,
        accountId: Long? = null,
        transactionId: Long? = null,
    ): SmsDiagnosticEntity = SmsDiagnosticEntity(
        externalKey = externalKey,
        kind = when (sms) {
            is CredoSmsParser.CardPayment -> SmsDiagnosticKind.CARD_PAYMENT
            is CredoSmsParser.OutgoingTransfer -> SmsDiagnosticKind.OUTGOING_TRANSFER
            is CredoSmsParser.IncomingTransfer -> SmsDiagnosticKind.INCOMING_TRANSFER
            is CredoSmsParser.DepositTopUp -> SmsDiagnosticKind.DEPOSIT_TOP_UP
            is CredoSmsParser.OwnTransfer -> SmsDiagnosticKind.OWN_TRANSFER
            is CredoSmsParser.CurrencyExchange -> SmsDiagnosticKind.CURRENCY_EXCHANGE
            is CredoSmsParser.BillPayment -> SmsDiagnosticKind.BILL_PAYMENT
            is CredoSmsParser.CashDeposit -> SmsDiagnosticKind.CASH_DEPOSIT
            is CredoSmsParser.InterestAccrual -> SmsDiagnosticKind.INTEREST
        },
        outcome = outcome,
        reason = reason,
        receivedAt = receivedAt,
        occurredAt = occurredMillis(sms, receivedAt),
        amountMinor = sms.amountMinor,
        currency = sms.currency,
        secondaryAmountMinor = (sms as? CredoSmsParser.CurrencyExchange)?.receivedAmountMinor,
        secondaryCurrency = (sms as? CredoSmsParser.CurrencyExchange)?.receivedCurrency,
        balanceMinor = sms.balanceMinor,
        balanceCurrency = sms.balanceCurrency,
        cardLast4 = (sms as? CredoSmsParser.CardPayment)?.cardLast4,
        counterparty = when (sms) {
            is CredoSmsParser.CardPayment -> sms.merchantRaw
            is CredoSmsParser.IncomingTransfer -> sms.senderName
            else -> null
        },
        fromIban = (sms as? CredoSmsParser.OwnTransfer)?.fromIban,
        toIban = (sms as? CredoSmsParser.OwnTransfer)?.toIban,
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

    private fun SmsDiagnosticEntity.toParsedSms(): CredoSmsParser.Sms? {
        val amount = amountMinor ?: return null
        val valueCurrency = currency ?: return null
        val instant = occurredAt?.let(Instant::ofEpochMilli) ?: return null
        val timestamp = LocalDateTime.ofInstant(instant, zone)
        return when (kind) {
            SmsDiagnosticKind.CARD_PAYMENT -> CredoSmsParser.CardPayment(
                amount, valueCurrency, cardLast4 ?: return null, counterparty ?: return null, null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.OUTGOING_TRANSFER -> CredoSmsParser.OutgoingTransfer(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.INCOMING_TRANSFER -> CredoSmsParser.IncomingTransfer(
                amount, valueCurrency, counterparty, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.BILL_PAYMENT -> CredoSmsParser.BillPayment(
                amount, valueCurrency, counterparty, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.CASH_DEPOSIT -> CredoSmsParser.CashDeposit(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.INTEREST -> CredoSmsParser.InterestAccrual(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.DEPOSIT_TOP_UP -> CredoSmsParser.DepositTopUp(
                amount, valueCurrency, balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.OWN_TRANSFER -> CredoSmsParser.OwnTransfer(
                amount, valueCurrency, fromIban ?: return null, toIban ?: return null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.CURRENCY_EXCHANGE -> CredoSmsParser.CurrencyExchange(
                amount, valueCurrency, secondaryAmountMinor ?: return null, secondaryCurrency ?: return null,
                balanceMinor, balanceCurrency, timestamp,
            )
            SmsDiagnosticKind.IGNORED, SmsDiagnosticKind.UNRECOGNIZED -> null
        }
    }

    private suspend fun resolveMerchant(raw: String): MerchantEntity? {
        val key = MerchantNormalizer.normalize(raw)
        if (key.isEmpty()) return null
        db.merchantDao().resolve(key)?.let { return it }
        val id = db.merchantDao().insert(
            MerchantEntity(normalizedKey = key, displayName = MerchantNormalizer.displayName(raw)),
        )
        return if (id > 0) db.merchantDao().byKey(key) else db.merchantDao().resolve(key)
    }

    private suspend fun cardFamilyFor(account: AccountEntity): List<AccountEntity> {
        val groupId = account.groupId ?: return listOf(account)
        return db.accountDao().byGroup(groupId).filter { candidate ->
            if (account.iban != null) candidate.iban == account.iban else candidate.id == account.id
        }.ifEmpty { listOf(account) }
    }

    private suspend fun pairedAccountHint(sms: CredoSmsParser.Sms): AccountEntity? {
        val oppositeKind = when (sms) {
            is CredoSmsParser.OutgoingTransfer -> SmsDiagnosticKind.DEPOSIT_TOP_UP
            is CredoSmsParser.DepositTopUp -> SmsDiagnosticKind.OUTGOING_TRANSFER
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
        ).mapNotNull { it.accountId }
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
