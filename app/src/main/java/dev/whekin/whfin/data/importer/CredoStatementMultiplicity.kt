package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.credo.CredoRowIdentity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement

/** A complete booked file defines multiplicity; a running-balance key does not. */
internal class CredoStatementMultiplicity(private val db: WhfinDatabase) {
    suspend fun plan(statement: BankStatement, accountId: Long, existing: List<TransactionEntity>): List<StatementMerge> {
        if (statement.bank.provider != "Credo" || statement.rows.any { it.bankTransactionId != null } ||
            statement.openingBalanceMinor == null || statement.closingBalanceMinor == null) return emptyList()
        val from = statement.periodFrom ?: return emptyList()
        val to = statement.periodTo ?: return emptyList()
        val imports = db.statementImportDao().forAccount(accountId)
        // An earlier cutoff may be an incomplete view of postings already seen in a newer file.
        if (imports.any {
                it.closingBalanceMinor != null && (it.periodTo ?: Long.MIN_VALUE) > to.toEpochDay()
            }) return emptyList()
        val proven = mutableMapOf<String, Int>()
        for (record in imports) record.rowMultiplicity?.let { encoded ->
            // Corrupt evidence must not lower the number of real payments we know about.
            val counts = try { org.json.JSONObject(encoded) } catch (_: Exception) { return emptyList() }
            for (key in counts.keys()) {
                val count = counts.optInt(key, -1)
                if (count < 1) return emptyList()
                proven[key] = maxOf(proven[key] ?: 0, count)
            }
        }
        val cohorts = statement.rows.groupBy { row -> existing.filter { tx ->
            tx.currency == statement.currency && CredoSourceBridge.sameFileRow(row, tx)
        }.map { it.id }.toSet() }
        val incomePayments = db.incomeSourceDao().payments().map { it.transactionId }.toSet()
        val result = mutableListOf<StatementMerge>()
        for ((ids, incoming) in cohorts) {
            if (incoming.any { (proven[fingerprint(it)] ?: 0) > incoming.size }) continue
            if (ids.size <= incoming.size || incoming.any { it.postedDate !in from..to }) continue
            if (cohorts.keys.any { it != ids && it.any(ids::contains) }) continue
            val rows = existing.filter { it.id in ids }.sortedBy { it.id }
            // Only legacy file evidence is superseded. Bank IDs, withdrawals and explicit financial
            // decisions require a separate resolution, never an arbitrary assignment to a repeat.
            if (rows.any { tx -> tx.isVoided || tx.externalKey == null ||
                    !StatementIdentity.isStatementRowKey(tx.externalKey) ||
                    CredoRowIdentity.mobileFromKey(tx.externalKey) != null ||
                    tx.id in incomePayments || tx.transferGroupId != null || tx.correctionOfTransactionId != null ||
                    tx.correctionRevokedAt != null || tx.canceledBySmsExternalKey != null ||
                    tx.mergedIntoTransactionId != null ||
                    db.transactionDao().correctionsFor(tx.id).isNotEmpty() ||
                    db.transactionAllocationDao().forTransaction(tx.id).isNotEmpty() ||
                    db.debtDao().eventsForTransaction(tx.id).isNotEmpty()
                } || rows.mapNotNull { it.categoryId }.distinct().size > 1 ||
                rows.map { it.origAmountMinor to it.origCurrency }.distinct().size > 1) continue
            // Equal balance+ordinal can encode genuine identical rows in one snapshot. This repair
            // only handles distinct balance revisions, not removal of arbitrary bank transactions.
            val balances = rows.map { it.externalKey!!.split('|').getOrNull(5)?.toLongOrNull() }
            if (balances.any { it == null } || balances.distinct().size != rows.size) continue
            if (incoming.size > 1 && (rows.map { it.categoryId }.distinct().size > 1 || rows.any { tx ->
                    db.smsDiagnosticDao().forTransaction(tx.id).isNotEmpty() ||
                        db.bankHoldDao().forTransaction(tx.id).isNotEmpty()
                })) continue
            StatementValidator.validate(statement)
            val survivors = rows.take(incoming.size)
            rows.drop(incoming.size).forEachIndexed { index, retired ->
                result += StatementMerge(survivors[index % survivors.size].id, retired.id, requireNotNull(survivors[index % survivors.size].externalKey))
            }
        }
        return result
    }
    companion object {
        /** Complete snapshots remember real repeats even when a later file has the same cutoff. */
        fun evidence(statement: BankStatement): String? {
            if (statement.bank.provider != "Credo" || statement.openingBalanceMinor == null ||
                statement.closingBalanceMinor == null || statement.periodFrom == null || statement.periodTo == null ||
                statement.rows.any { it.bankTransactionId != null } ||
                runCatching { StatementValidator.validate(statement) }.isFailure) return null
            val counts = statement.rows.groupingBy(::fingerprint).eachCount()
            return org.json.JSONObject(counts).toString()
        }

        private fun fingerprint(row: dev.whekin.whfin.data.statement.StatementRow): String {
            // Length-delimited JSON avoids collisions from separators in bank descriptions.
            val raw = org.json.JSONArray(listOf(row.postedDate.toString(),
                (row.purchaseDate ?: row.postedDate).toString(), row.amountMinor.toString(),
                row.description, row.beneficiaryAccount)).toString()
            return java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
    }

}

/** Retired evidence remains in the backup with a link to its counted movement. */
data class StatementMerge(val transactionId: Long, val duplicateId: Long, val externalKey: String)
