package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementRow
import dev.whekin.whfin.data.tbc.TbcRowIdentity
import java.time.Instant
import java.util.Locale

/** Links independently named mobile/file evidence only when correspondence is unique both ways. */
internal open class ApiSourceBridge(private val ids: dev.whekin.whfin.data.statement.ApiRowIdentity, private val bank: String) {
    fun plan(statement: BankStatement, existing: List<TransactionEntity>): Map<String, PlannedRow> {
        val identity = StatementIdentity.of(statement)
        val incoming = statement.rows.associateBy(identity::rowKey)
        if (incoming.size != statement.rows.size) conflict()
        val known = mutableMapOf<String, PlannedRow>()
        val unmatched = linkedMapOf<String, StatementRow>()
        val claimed = mutableSetOf<Long>()
        for ((key, row) in incoming) {
            val id = row.bankTransactionId
            val mobile = ids.isMobileId(id)
            val matches = existing.filter { tx ->
                if (mobile) ids.hasMobile(tx.externalKey, requireNotNull(id)) else ids.hasFile(tx.externalKey, key)
            }
            if (matches.size > 1) conflict()
            val found = matches.singleOrNull()
            if (found != null) {
                if (bank == "Credo" && id == null && !sameFileRow(row, found)) {
                    // A balance-based legacy key may be reused by a different merchant after the
                    // bank reorders equal-sized charges. It is a locator, not proof of identity.
                    unmatched[key] = row
                    continue
                }
                claimed += found.id
                if (mobile && !ids.isMobileOnly(found.externalKey)) {
                    // The file is richer evidence. Mobile data must not erase its running balance,
                    // counterparties, or explicit correction/void decisions.
                    if (!found.isVoided && !sameMoneyAndDay(row, found)) conflict()
                    known[key] = PlannedRow.Duplicate(row, requireNotNull(found.externalKey))
                }
                continue
            }
            unmatched[key] = row
        }
        // Credo XLSX can reorder postings within a day. Its fallback key contains the running
        // balance, so a revised balance is not by itself evidence of another purchase. Claim only
        // a unique exact file description/date/money counterpart, in both directions. Keep the
        // original key (including any API alias) so older overlapping files remain idempotent.
        if (bank == "Credo") {
            val fileChoices = unmatched.filterValues { it.bankTransactionId == null }
                .mapValues { (_, row) -> existing.filter { tx ->
                    tx.id !in claimed && tx.externalKey != null && !ids.isMobileOnly(tx.externalKey) &&
                        sameFileRow(row, tx)
                } }
            for ((key, matches) in fileChoices) {
                val row = unmatched.getValue(key)
                if (matches.isEmpty()) continue
                if (key in known) continue
                if (matches.size > 1) {
                    val cohort = fileChoices.filterValues { choices -> choices.map { it.id }.toSet() == matches.map { it.id }.toSet() }
                    // Reordered identical postings still represent the same number of movements.
                    // Preserve every ID; no operation is removed or new amount inferred.
                    if (cohort.size != matches.size || matches.any { it.id in claimed } ||
                        fileChoices.filterKeys { it !in cohort }.values.any { choices -> choices.any { candidate -> candidate in matches } }) conflict()
                    cohort.keys.zip(matches.sortedBy { it.id }).forEach { (cohortKey, found) ->
                        val incomingRow = unmatched.getValue(cohortKey)
                        claimed += found.id
                        val retained = requireNotNull(found.externalKey)
                        known[cohortKey] = if (found.isVoided || found.balanceAfterMinor == incomingRow.balanceAfterMinor)
                            PlannedRow.Duplicate(incomingRow, retained)
                        else PlannedRow.Reconcile(incomingRow, retained, found.id)
                    }
                    continue
                }
                val found = matches.singleOrNull() ?: conflict()
                if (fileChoices.values.count { found in it } != 1) conflict()
                claimed += found.id
                val retainedKey = requireNotNull(found.externalKey)
                known[key] = if (found.isVoided || found.balanceAfterMinor == row.balanceAfterMinor)
                    PlannedRow.Duplicate(row, retainedKey)
                else PlannedRow.Reconcile(row, retainedKey, found.id)
            }
            known.keys.forEach(unmatched::remove)
        }
        val choices = unmatched.mapValues { (_, row) ->
            val mobile = ids.isMobileId(row.bankTransactionId)
            val pool = existing.filter { tx ->
                tx.id !in claimed && tx.externalKey != null &&
                    (if (mobile) !ids.isMobileOnly(tx.externalKey) && ids.mobileFromKey(tx.externalKey) == null
                     else ids.isMobileOnly(tx.externalKey)) &&
                    sameMoneyAndDay(row, tx)
            }
            val usePeer = bank == "Credo" && row.operation.isOwnMovement
            val samePeer = pool.filter { usePeer && row.beneficiaryAccount != null && row.beneficiaryAccount == it.counterpartyIban }
            val compatiblePeers = pool.filter { !usePeer || row.beneficiaryAccount == null || it.counterpartyIban == null || row.beneficiaryAccount == it.counterpartyIban }
            val exact = compatiblePeers.filter { descriptionMatches(row, it) }
            val exactPeer = samePeer.filter { descriptionMatches(row, it) }
            if (exactPeer.isNotEmpty()) exactPeer else if (samePeer.isNotEmpty()) samePeer else if (exact.isNotEmpty()) exact else compatiblePeers.filter { compatible(row, it) }
        }
        for ((key, row) in unmatched) {
            val matches = choices.getValue(key)
            if (matches.size > 1) conflict()
            val found = matches.singleOrNull() ?: continue
            if (choices.values.count { found in it } != 1) conflict()
            claimed += found.id
            val mobile = ids.isMobileId(row.bankTransactionId)
            val joined = if (mobile) ids.join(requireNotNull(found.externalKey), requireNotNull(row.bankTransactionId))
                else ids.join(key, requireNotNull(ids.mobileFromKey(requireNotNull(found.externalKey))))
            known[key] = if (mobile || found.isVoided) PlannedRow.LinkIdentity(row, joined, found.id)
                else PlannedRow.Reconcile(row, joined, found.id)
        }
        // An unmatched row that could be a renamed opposite-source row is not safe to insert.
        for ((key, row) in unmatched) {
            if (key in known) continue
            if (bank == "Credo" && row.bankTransactionId == null && existing.any { ids.hasFile(it.externalKey, key) }) conflict()
            val mobile = ids.isMobileId(row.bankTransactionId)
            if (existing.any { it.id !in claimed && it.externalKey != null &&
                    (if (mobile) !ids.isMobileOnly(it.externalKey) else ids.mobileFromKey(it.externalKey) != null) && potentialDuplicate(row, it) }) conflict()
        }
        return known
    }

    fun existingKey(row: StatementRow, canonical: String, keys: Set<String>): String {
        val id = row.bankTransactionId
        val found = keys.filter { if (ids.isMobileId(id)) ids.hasMobile(it, requireNotNull(id)) else ids.hasFile(it, canonical) }
        if (found.size > 1) conflict()
        return found.singleOrNull() ?: canonical
    }

    internal fun sameFileRow(row: StatementRow, tx: TransactionEntity): Boolean =
        row.amountMinor == tx.amountMinor &&
            row.postedDate == day(tx.postedAt ?: tx.occurredAt) &&
            (row.purchaseDate ?: row.postedDate) == day(tx.occurredAt) &&
            row.description.isNotBlank() &&
            row.description == (tx.note ?: tx.rawCounterparty) &&
            row.beneficiaryAccount == tx.counterpartyIban &&
            ((row.merchantRaw ?: row.beneficiaryName) == tx.rawCounterparty ||
                // Older SMS settlements kept the peer IBAN but omitted its display name.
                // Missing presentation is not a contradictory bank identity. A card merchant or
                // a different/missing peer IBAN still cannot pass this exception.
                (row.merchantRaw == null && tx.rawCounterparty == null &&
                    !row.beneficiaryAccount.isNullOrBlank() && row.beneficiaryAccount == tx.counterpartyIban))

    private fun potentialDuplicate(row: StatementRow, tx: TransactionEntity): Boolean {
        if (sameMoneyAndDay(row, tx)) return true
        if (row.amountMinor != tx.amountMinor) return false
        val distance = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(row.purchaseDate ?: row.postedDate, day(tx.occurredAt)))
        return distance <= 3 && (descriptionMatches(row, tx) ||
            MerchantNormalizer.equivalent(row.merchantRaw ?: row.beneficiaryName, tx.rawCounterparty))
    }

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(LedgerCalendar.zone).toLocalDate()
    private fun sameMoneyAndDay(row: StatementRow, tx: TransactionEntity): Boolean =
        row.amountMinor == tx.amountMinor &&
            setOfNotNull(row.postedDate, row.purchaseDate).intersect(setOf(day(tx.postedAt ?: tx.occurredAt), day(tx.occurredAt))).isNotEmpty()

    private fun compatible(row: StatementRow, tx: TransactionEntity): Boolean {
        if (!sameMoneyAndDay(row, tx)) return false
        val counterparty = row.merchantRaw ?: row.beneficiaryName
        return (counterparty != null && MerchantNormalizer.equivalent(counterparty, tx.rawCounterparty)) || descriptionMatches(row, tx)
    }

    private fun descriptionMatches(row: StatementRow, tx: TransactionEntity): Boolean {
        val a = normalized(row.description.substringBefore('\n'))
        val b = normalized(tx.note.orEmpty().substringBefore('\n'))
        return a.isNotBlank() && b.isNotBlank() && (a == b ||
            (a.length >= 12 && b.startsWith("$a,")) || (b.length >= 12 && a.startsWith("$b,")))
    }
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").replace(Regex("\\s*([,;:])\\s*"), "$1")
    private fun conflict(): Nothing = throw AmbiguousStatementIdentityException("$bank history and XLSX cannot be matched uniquely. No changes were made to this account.")
}

internal object TbcSourceBridge : ApiSourceBridge(TbcRowIdentity, "TBC")
internal object CredoSourceBridge : ApiSourceBridge(dev.whekin.whfin.data.credo.CredoRowIdentity, "Credo")
