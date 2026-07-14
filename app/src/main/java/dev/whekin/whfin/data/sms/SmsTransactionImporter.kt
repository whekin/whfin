package dev.whekin.whfin.data.sms

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
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

/** Converts a Credo SMS classification into a visible diagnostic and, when possible, a pending transaction. */
class SmsTransactionImporter(private val db: WhfinDatabase) {
    private val zone = ZoneId.of("Asia/Tbilisi")

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
        val account = db.accountDao().byId(accountId)
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.NO_ACCOUNT)
        val expectedCurrency = diagnostic.balanceCurrency ?: diagnostic.currency
        if (expectedCurrency == null || account.currency != expectedCurrency) {
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

        diagnostic.cardLast4?.let { last4 ->
            val family = cardFamilyFor(account)
            db.paymentInstrumentDao().linkForAccounts(family, last4, cardType)
        }

        val sms = diagnostic.toParsedSms()
            ?: return@withTransaction updateFailure(diagnostic, SmsDiagnosticReason.PARSE_FAILURE)
        val transactionId = insertTransaction(sms, account, diagnostic.externalKey)
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
        SmsImportResult(outcome, diagnostic.id, resolvedTransactionId)
    }

    private suspend fun evaluate(
        classification: CredoSmsParser.Classification,
        key: String,
        receivedAt: Long,
        persist: Boolean,
    ): SmsImportResult = when (classification) {
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
            return SmsImportResult(SmsDiagnosticOutcome.DUPLICATE, id, existing.id)
        }

        return when (val resolution = resolveAccount(sms)) {
            is AccountResolution.Found -> {
                if (!persist) return SmsImportResult(SmsDiagnosticOutcome.IMPORTED)
                val transactionId = insertTransaction(sms, resolution.account, key)
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
                SmsImportResult(outcome, id, resolvedTransactionId)
            }
            is AccountResolution.NeedsChoice -> {
                val diagnostic = diagnosticFor(
                    sms = sms,
                    externalKey = key,
                    outcome = resolution.outcome,
                    reason = resolution.reason,
                    receivedAt = receivedAt,
                )
                val id = if (persist) persistDiagnostic(diagnostic) else null
                SmsImportResult(resolution.outcome, id, reason = resolution.reason)
            }
        }
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
        if (sms is CredoSmsParser.OwnTransfer) {
            db.accountDao().byIbanAndCurrency(sms.fromIban, currency)?.let {
                return AccountResolution.Found(it)
            }
            val candidates = db.accountDao().bankAccountsByCurrency(currency)
            return AccountResolution.NeedsChoice(
                SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                if (candidates.isEmpty()) SmsDiagnosticReason.NO_ACCOUNT else SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            )
        }
        val candidates = db.accountDao().bankAccountsByCurrency(currency)
        return when (candidates.size) {
            1 -> AccountResolution.Found(candidates.single())
            0 -> AccountResolution.NeedsChoice(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, SmsDiagnosticReason.NO_ACCOUNT)
            else -> AccountResolution.NeedsChoice(
                SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            )
        }
    }

    private suspend fun insertTransaction(sms: CredoSmsParser.Sms, account: AccountEntity, key: String): Long {
        val rawCounterparty = when (sms) {
            is CredoSmsParser.CardPayment -> sms.merchantRaw
            is CredoSmsParser.IncomingTransfer -> sms.senderName
            else -> null
        }
        val merchant = rawCounterparty?.let { resolveMerchant(it) }
        val amount = if (sms is CredoSmsParser.IncomingTransfer) sms.amountMinor else -sms.amountMinor
        val accountAmount = if (sms.currency == account.currency) amount else 0L
        return db.transactionDao().insert(
            TransactionEntity(
                accountId = account.id,
                amountMinor = accountAmount,
                currency = account.currency,
                origAmountMinor = sms.amountMinor.takeIf { sms.currency != account.currency },
                origCurrency = sms.currency.takeIf { sms.currency != account.currency },
                occurredAt = sms.timestamp.atZone(zone).toInstant().toEpochMilli(),
                merchantId = merchant?.id,
                rawCounterparty = rawCounterparty,
                categoryId = merchant?.categoryId,
                status = TxStatus.PENDING,
                source = TxSource.SMS,
                isTransfer = sms is CredoSmsParser.OwnTransfer || sms is CredoSmsParser.CurrencyExchange,
                balanceAfterMinor = sms.balanceMinor,
                externalKey = key,
                createdAt = System.currentTimeMillis(),
            ),
        )
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
            is CredoSmsParser.OwnTransfer -> SmsDiagnosticKind.OWN_TRANSFER
            is CredoSmsParser.CurrencyExchange -> SmsDiagnosticKind.CURRENCY_EXCHANGE
        },
        outcome = outcome,
        reason = reason,
        receivedAt = receivedAt,
        occurredAt = sms.timestamp.atZone(zone).toInstant().toEpochMilli(),
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

    private sealed interface AccountResolution {
        data class Found(val account: AccountEntity) : AccountResolution
        data class NeedsChoice(
            val outcome: SmsDiagnosticOutcome,
            val reason: SmsDiagnosticReason,
        ) : AccountResolution
    }
}

internal fun smsExternalKey(body: String): String = "sms|" + MessageDigest.getInstance("SHA-256")
    .digest(body.trim().toByteArray())
    .take(12)
    .joinToString("") { "%02x".format(it) }
