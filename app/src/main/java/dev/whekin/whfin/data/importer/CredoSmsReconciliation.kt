package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import java.time.Instant
import java.time.ZoneId

/** Matches a complete cohort before rows can claim individual SMS evidence. */
internal class CredoSmsReconciliation(private val db: WhfinDatabase, private val zone: ZoneId) {
    suspend fun ownTransfers(statement: BankStatement, accountId: Long): Map<String, TransactionEntity> {
        if (statement.bank.provider != "Credo") return emptyMap()
        val result = mutableMapOf<String, TransactionEntity>()
        val cohorts = keyedRows(statement).entries.filter { it.value.operation == StatementOperation.OWN_TRANSFER && it.value.beneficiaryAccount != null }
            .groupBy { Triple(it.value.postedDate, it.value.amountMinor, it.value.beneficiaryAccount) }
        for ((shape, rows) in cohorts) {
            val candidates = db.transactionDao().reconciliationCandidates(accountId,
                shape.first.atStartOfDay(zone).toInstant().toEpochMilli(),
                shape.first.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
                .filter { it.source == TxSource.SMS && it.isTransfer && it.amountMinor != Long.MIN_VALUE && it.amountMinor == shape.second }
            val matching = candidates.filter { tx ->
                val groupId = tx.transferGroupId
                if (groupId == null || db.transactionDao().transferGroupById(groupId)?.type == TransferGroupType.OWN_LINK) false
                else {
                    val peer = db.transactionDao().byTransferGroup(groupId).filter { !it.isVoided && it.accountId != accountId }.singleOrNull()
                    peer != null && peer.currency == statement.currency && peer.amountMinor == -tx.amountMinor &&
                        db.accountDao().byId(peer.accountId)?.iban == shape.third
                }
            }.sortedWith(compareBy({ it.occurredAt }, { it.id }))
            if (matching.isEmpty() || matching.size != rows.size) continue
            // Equal multiplicity and both ledger sides are required. Do not assign different owner
            // categories or financial links to indistinguishable postings by their order.
            if (matching.size > 1 && (matching.map { it.categoryId }.distinct().size > 1 || matching.any {
                db.transactionAllocationDao().forTransaction(it.id).isNotEmpty() || db.debtDao().eventsForTransaction(it.id).isNotEmpty()
            })) continue
            rows.zip(matching).forEach { (row, tx) -> result[row.key] = tx }
        }
        return result
    }

    suspend fun transfers(statement: BankStatement, accountId: Long): Map<String, TransactionEntity> {
        if (statement.bank.provider != "Credo") return emptyMap()
        val evidence = db.smsDiagnosticDao().activeEvidenceForAccount(accountId)
        val rows = keyedRows(statement).filterValues { it.operation in setOf(StatementOperation.TRANSFER_OUT, StatementOperation.TRANSFER_IN) }
        val choices = rows.mapValues { (_, row) ->
            evidence.filter { message ->
                val wantedKind = if (row.operation == StatementOperation.TRANSFER_OUT) SmsDiagnosticKind.OUTGOING_TRANSFER else SmsDiagnosticKind.INCOMING_TRANSFER
                val occurred = message.occurredAt
                val amount = message.amountMinor
                if (message.kind != wantedKind || message.currency != statement.currency || occurred == null || amount == null || amount == Long.MIN_VALUE) false
                else {
                    val signed = if (row.amountMinor < 0) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                    val printedDay = Instant.ofEpochMilli(occurred).atZone(zone).toLocalDate()
                    val receivedDay = Instant.ofEpochMilli(message.receivedAt).atZone(zone).toLocalDate()
                    val possibleDays = if (printedDay > receivedDay) setOf(printedDay, receivedDay) else setOf(printedDay)
                    signed == row.amountMinor && possibleDays.any {
                        kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it, row.postedDate)) <= 1
                    }
                }
            }.mapNotNull { it.transactionId }.distinct()
        }
        val result = mutableMapOf<String, TransactionEntity>()
        for ((key, ids) in choices) {
            val row = rows.getValue(key)
            val id = ids.singleOrNull() ?: continue
            if (choices.values.count { id in it } != 1) continue
            val tx = db.transactionDao().byId(id) ?: continue
            if (tx.isTransfer || tx.amountMinor != row.amountMinor ||
                (tx.counterpartyIban != null && tx.counterpartyIban != row.beneficiaryAccount)) continue
            result[key] = tx
        }
        return result
    }

    suspend fun aggregates(statement: BankStatement, accountId: Long): Map<String, List<TransactionEntity>> {
        if (statement.bank.provider != "Credo") return emptyMap()
        val result = mutableMapOf<String, List<TransactionEntity>>()
        val byDay = keyedRows(statement).entries.filter { it.value.operation == StatementOperation.CARD_PAYMENT }
            .groupBy { it.value.purchaseDate ?: it.value.postedDate }
        for ((day, rows) in byDay) {
            val candidates = db.transactionDao().reconciliationCandidates(accountId,
                day.atStartOfDay(zone).toInstant().toEpochMilli(),
                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
                .filter { it.source == TxSource.SMS && !it.isTransfer && it.transferGroupId == null && it.amountMinor < 0 }
            for ((key, row) in rows) {
                if (row.amountMinor >= 0 || row.merchantRaw == null) continue
                // No subset search: another charge at this merchant makes aggregation ambiguous.
                if (rows.count { MerchantNormalizer.equivalent(it.value.merchantRaw, row.merchantRaw) } != 1) continue
                val cohort = candidates.filter { MerchantNormalizer.equivalent(it.rawCounterparty, row.merchantRaw) }
                if (cohort.size < 2) continue
                val sum = try { cohort.fold(0L) { total, tx -> Math.addExact(total, tx.amountMinor) } }
                    catch (_: ArithmeticException) { continue }
                if (sum != row.amountMinor || cohort.any { it.currency != statement.currency }) continue
                val evidence = cohort.map { db.smsDiagnosticDao().forTransaction(it.id) }
                if (evidence.any { it.size != 1 }) continue
                val messages = evidence.flatten()
                if (messages.any { it.kind != SmsDiagnosticKind.CARD_PAYMENT || it.currency != statement.currency || it.cardLast4 == null } ||
                    messages.map { it.cardLast4 }.distinct().size != 1) continue
                if (cohort.mapNotNull { it.categoryId }.distinct().size > 1 || cohort.any {
                    db.transactionAllocationDao().forTransaction(it.id).isNotEmpty() ||
                        db.debtDao().eventsForTransaction(it.id).isNotEmpty()
                }) throw InvalidStatementException("A consolidated card charge has conflicting categories, splits or debt links.")
                result[key] = cohort.sortedWith(compareBy({ it.occurredAt }, { it.id }))
            }
        }
        return result
    }

    private fun keyedRows(statement: BankStatement): Map<String, StatementRow> {
        val identity = StatementIdentity.of(statement)
        return statement.rows.associateBy(identity::rowKey)
    }
}
