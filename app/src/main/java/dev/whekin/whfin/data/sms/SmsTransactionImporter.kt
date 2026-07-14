package dev.whekin.whfin.data.sms

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.InstrumentAccountLinkEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.MerchantNormalizer
import java.security.MessageDigest
import java.time.ZoneId

/** Converts a parsed Credo SMS into a pending transaction. */
class SmsTransactionImporter(private val db: WhfinDatabase) {
    private val zone = ZoneId.of("Asia/Tbilisi")

    suspend fun import(body: String): Long? {
        val sms = CredoSmsParser.parse(body) ?: return null
        return db.withTransaction {
            val key = "sms|" + MessageDigest.getInstance("SHA-256")
                .digest(body.trim().toByteArray())
                .take(12).joinToString("") { "%02x".format(it) }
            db.transactionDao().byExternalKey(key)?.let { return@withTransaction it.id }

            val account = resolveAccount(sms) ?: return@withTransaction null
            val rawCounterparty = when (sms) {
                is CredoSmsParser.CardPayment -> sms.merchantRaw
                is CredoSmsParser.IncomingTransfer -> sms.senderName
                else -> null
            }
            val merchant = rawCounterparty?.let { resolveMerchant(it) }
            val amount = when (sms) {
                is CredoSmsParser.IncomingTransfer -> sms.amountMinor
                else -> -sms.amountMinor
            }
            val accountAmount = if (sms.currency == account.currency) amount else 0L

            db.transactionDao().insert(
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
            ).takeIf { it > 0 }
        }
    }

    private suspend fun resolveAccount(sms: CredoSmsParser.Sms): AccountEntity? {
        if (sms is CredoSmsParser.CardPayment) {
            val currency = sms.balanceCurrency ?: sms.currency
            db.accountDao().byCardAndCurrency(sms.cardLast4, currency).singleOrNull()?.let { return it }
        }
        if (sms is CredoSmsParser.OwnTransfer) {
            val currency = sms.balanceCurrency ?: sms.currency
            db.accountDao().byIbanAndCurrency(sms.fromIban, currency)?.let { return it }
        }
        val currency = sms.balanceCurrency ?: sms.currency
        val candidates = db.accountDao().bankAccountsByCurrency(currency)
        val account = candidates.singleOrNull() ?: return null
        if (sms is CredoSmsParser.CardPayment) {
            val groupId = account.groupId ?: return null
            val existing = db.paymentInstrumentDao().byLast4(groupId, sms.cardLast4)
            val instrumentId = existing?.id ?: db.paymentInstrumentDao().insert(
                PaymentInstrumentEntity(
                    groupId = groupId,
                    type = PaymentInstrumentType.PHYSICAL_CARD,
                    last4 = sms.cardLast4,
                ),
            )
            if (instrumentId > 0) {
                db.paymentInstrumentDao().link(InstrumentAccountLinkEntity(instrumentId, account.id))
            }
        }
        return account
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
}
