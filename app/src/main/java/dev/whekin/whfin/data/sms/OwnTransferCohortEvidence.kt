package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class OwnTransferCohortKey(val bank: BankSmsBank, val day: LocalDate,
    val currency: String, val amount: Long, val fromIban: String, val toIban: String)
internal data class OwnTransferCohortMatch(val accountId: Long, val sourceTransactionIds: Set<Long>)

/** Confirms equal multiplicity of complete bank pairs, without inventing per-message identity. */
internal class OwnTransferCohortEvidence(private val db: WhfinDatabase, private val zone: ZoneId) {
    fun key(message: SmsDiagnosticEntity): OwnTransferCohortKey? {
        if (message.kind != SmsDiagnosticKind.OWN_TRANSFER) return null
        val amount = message.amountMinor?.takeIf { it > 0 } ?: return null
        return OwnTransferCohortKey(BankSmsBank.fromKey(message.externalKey),
            Instant.ofEpochMilli(message.occurredAt ?: return null).atZone(zone).toLocalDate(),
            message.currency ?: return null, amount, message.fromIban ?: return null, message.toIban ?: return null)
    }
    suspend fun find(messages: List<SmsDiagnosticEntity>): OwnTransferCohortMatch? {
        if (messages.size < 2 || messages.map { it.externalKey }.distinct().size != messages.size) return null
        val key = key(messages.first()) ?: return null
        if (messages.any { key(it) != key }) return null
        val accounts = db.accountDao().bankAccountsByCurrency(key.currency).filter { key.bank.accepts(db, it) }
        val from = accounts.filter { it.iban == key.fromIban }.singleOrNull() ?: return null
        val to = accounts.filter { it.iban == key.toIban }.singleOrNull() ?: return null
        if (from.id == to.id) return null
        val start = key.day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = key.day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val received = db.transactionDao().statementCandidates(to.id, start, end)
            .filter { it.isTransfer && it.amountMinor == key.amount }
        val sent = db.transactionDao().statementCandidates(from.id, start, end)
            .filter { it.isTransfer && it.amountMinor == -key.amount }
        // All matching withdrawals must have exactly one opposite bank leg. No subset selection.
        if (sent.size != messages.size) return null
        val groups = mutableSetOf<Long>()
        val keys = messages.map { it.externalKey }.toSet()
        for (source in sent) {
            val groupId = source.transferGroupId ?: return null
            if (!groups.add(groupId)) return null
            val type = db.transactionDao().transferGroupById(groupId)?.type ?: return null
            if (type !in setOf(TransferGroupType.TRANSFER, TransferGroupType.SAVINGS, TransferGroupType.CARD_TOPUP)) return null
            if (received.count { it.transferGroupId == groupId } != 1) return null
            if (db.smsDiagnosticDao().forTransaction(source.id).any { it.externalKey !in keys }) return null
        }
        return OwnTransferCohortMatch(from.id, sent.map { it.id }.toSet())
    }
    suspend fun validFor(message: SmsDiagnosticEntity): Boolean {
        val key = key(message) ?: return false
        val cohort = db.smsDiagnosticDao().ownTransferCohortCandidates().filter { key(it) == key }
        return find(cohort) != null
    }
    suspend fun hasPeers(message: SmsDiagnosticEntity): Boolean {
        val key = key(message) ?: return false
        return db.smsDiagnosticDao().ownTransferCohortCandidates().any {
            it.externalKey != message.externalKey && key(it) == key
        }
    }
    suspend fun reservedSourceIds(): Set<Long> = db.smsDiagnosticDao().ownTransferCohortCandidates()
        .groupBy { key(it) }.filterKeys { it != null }.values
        .filter { rows -> rows.any { it.outcome == SmsDiagnosticOutcome.MATCHED_GROUP } }
        .flatMap { find(it)?.sourceTransactionIds.orEmpty() }.toSet()
}
