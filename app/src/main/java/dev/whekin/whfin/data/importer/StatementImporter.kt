package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.StatementSourceEntity
import dev.whekin.whfin.data.db.StatementSourceType
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.ReconciliationIssueEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.CredoStatementParser.OperationType
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * Пайплайн: xlsx-выписка -> Room.
 *
 * - счёт находится по IBAN или создаётся
 * - дедупликация через externalKey (повторный импорт того же файла = 0 новых строк)
 * - переводы между своими счетами и конвертации помечаются isTransfer
 * - мерчанты создаются в словаре; категория подставляется, если уже выучена
 * - pending-SMS совпадает по нормализованному мерчанту и дате покупки и заменяется строкой выписки
 */
class StatementImporter(private val db: WhfinDatabase) {

    enum class Phase { READING, IMPORTING, RECONCILING, VERIFYING }

    data class Result(
        val accountId: Long,
        val accountCreated: Boolean,
        val totalRows: Int,
        val inserted: Int,
        val duplicates: Int,
        val reconciled: Int,
        val importId: Long,
        val reviewCount: Int,
    )

    private val zone = ZoneId.of("Asia/Tbilisi")

    /** Re-runs pairing when a new currency ledger makes old conversions matchable. */
    suspend fun repairTransferGroups() {
        db.accountDao().allActive()
            .filter { it.type == AccountType.BANK && it.groupId != null }
            .groupBy { it.groupId!! }
            .forEach { (groupId, accounts) ->
                db.withTransaction {
                    val fromMillis = LocalDate.now().minusYears(3).atMillis()
                    val toMillis = LocalDate.now().plusDays(2).atMillis() - 1
                    val byId = accounts.associateBy { it.id }
                    val candidates = db.transactionDao().conversionTransfers(groupId, fromMillis, toMillis)
                        .sortedWith(compareBy<TransactionEntity> { it.occurredAt }
                            .thenBy { if (it.counterpartyIban != null) 0 else 1 })
                        .toMutableList()
                    candidates.forEach { db.transactionDao().clearTransferGroup(it.id) }
                    while (candidates.isNotEmpty()) {
                        val first = candidates.removeAt(0)
                        val firstAccount = byId[first.accountId] ?: continue
                        val index = candidates.indices.filter { index ->
                            val other = candidates[index]
                            val otherAccount = byId[other.accountId]
                            other.currency != first.currency &&
                                (first.amountMinor < 0) != (other.amountMinor < 0) &&
                                kotlin.math.abs(other.occurredAt - first.occurredAt) <= 12L * 60 * 60 * 1000 &&
                                (first.counterpartyIban == otherAccount?.iban || other.counterpartyIban == firstAccount.iban)
                        }.minByOrNull { candidateIndex ->
                            val other = candidates[candidateIndex]
                            val otherAccount = byId[other.accountId]
                            val mutualIban = first.counterpartyIban == otherAccount?.iban &&
                                other.counterpartyIban == firstAccount.iban
                            (if (mutualIban) 0L else 10L * 24 * 60 * 60 * 1000) +
                                kotlin.math.abs(other.occurredAt - first.occurredAt)
                        } ?: -1
                        if (index < 0) continue
                        val second = candidates.removeAt(index)
                        val transferGroupId = db.transactionDao().insertTransferGroup(
                            TransferGroupEntity(type = TransferGroupType.CONVERSION, createdAt = System.currentTimeMillis()),
                        )
                        db.transactionDao().setTransferGroup(first.id, transferGroupId)
                        db.transactionDao().setTransferGroup(second.id, transferGroupId)
                    }
                }
            }
    }

    suspend fun import(
        input: InputStream,
        fileName: String? = null,
        origin: StatementImportOrigin = StatementImportOrigin.FILE,
        onPhase: (Phase) -> Unit = {},
    ): Result {
        onPhase(Phase.READING)
        val statement = CredoStatementParser.parse(input)
        onPhase(Phase.IMPORTING)
        // Одна SQLite-транзакция на весь файл: иначе 1000+ отдельных коммитов = десятки секунд
        return db.withTransaction { importParsed(statement, fileName, origin, onPhase) }
    }

    private suspend fun importParsed(
        statement: CredoStatementParser.Statement,
        fileName: String?,
        origin: StatementImportOrigin,
        onPhase: (Phase) -> Unit,
    ): Result {
        var accountCreated = false
        val account = db.accountDao().byIbanAndCurrency(statement.accountIban, statement.currency) ?: run {
            accountCreated = true
            val group = db.financialGroupDao().byProvider(FinancialGroupType.BANK, "Credo")
            val groupId = group?.id ?: db.financialGroupDao().insert(
                FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
            )
            val id = db.accountDao().insert(
                AccountEntity(
                    name = "Credo ${statement.currency} •${statement.accountIban.takeLast(4)}",
                    type = AccountType.BANK,
                    groupId = groupId,
                    currency = statement.currency,
                    iban = statement.accountIban,
                ),
            )
            db.accountDao().byId(id)!!
        }

        // Начальный баланс на старт периода — иначе баланс счёта = только сумма операций.
        // isTransfer=true исключает его из статистики доходов/расходов.
        val opening = statement.openingBalanceMinor
        if (accountCreated && opening != null && opening != 0L && statement.periodFrom != null) {
            db.transactionDao().insert(
                TransactionEntity(
                    accountId = account.id,
                    amountMinor = opening,
                    currency = statement.currency,
                    occurredAt = statement.periodFrom.minusDays(1).atMillis(),
                    status = TxStatus.CONFIRMED,
                    source = TxSource.ADJUSTMENT,
                    isTransfer = true,
                    externalKey = "opening|${statement.accountIban}|${statement.periodFrom}",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        var inserted = 0
        var duplicates = 0
        var reconciled = 0
        val keyCounts = mutableMapOf<String, Int>()
        val existingKeys = db.transactionDao().allExternalKeys().toHashSet()

        for ((rowIndex, row) in statement.rows.withIndex()) {
            if (rowIndex == statement.rows.size / 2) onPhase(Phase.RECONCILING)
            val baseKey = listOf(
                statement.accountIban,
                row.postedDate,
                row.amountMinor,
                row.balanceAfterMinor ?: "",
            ).joinToString("|")
            // Одинаковые (дата, сумма, баланс) теоретически возможны — различаем порядковым номером
            val ordinal = keyCounts.merge(baseKey, 1, Int::plus)!!
            val externalKey = "stmt|$baseKey|$ordinal"

            if (externalKey in existingKeys) {
                duplicates++
                continue
            }

            val merchant = row.merchantRaw
                ?.let { resolveMerchant(it) }
                ?: row.beneficiaryName
                    ?.takeIf { row.operation != OperationType.OWN_TRANSFER }
                    ?.let { resolveMerchant(it) }

            val isOwnTransfer = row.operation == OperationType.OWN_TRANSFER ||
                row.operation == OperationType.CURRENCY_EXCHANGE ||
                row.operation == OperationType.SAVINGS_TOPUP

            val occurred = (row.purchaseDate ?: row.postedDate).atMillis()
            val pending = row.merchantRaw?.let { raw ->
                val wanted = MerchantNormalizer.normalize(raw)
                val dayStart = (row.purchaseDate ?: row.postedDate).atMillis()
                val dayEnd = (row.purchaseDate ?: row.postedDate).plusDays(1).atMillis() - 1
                val candidates = db.transactionDao().reconciliationCandidates(account.id, dayStart, dayEnd)
                    .filter { candidate ->
                        candidate.rawCounterparty?.let(MerchantNormalizer::normalize) == wanted
                    }
                candidates.filter { it.amountMinor == row.amountMinor }.singleOrNull()
                    ?: candidates.singleOrNull()
            }
            if (pending != null) {
                db.transactionDao().update(
                    pending.copy(
                        amountMinor = row.amountMinor,
                        currency = statement.currency,
                        occurredAt = occurred,
                        postedAt = row.postedDate.atMillis(),
                        merchantId = merchant?.id,
                        rawCounterparty = row.merchantRaw,
                        counterpartyIban = row.beneficiaryAccount,
                        categoryId = merchant?.categoryId,
                        note = row.description.takeIf { it != row.merchantRaw },
                        status = TxStatus.CONFIRMED,
                        source = TxSource.STATEMENT,
                        isTransfer = isOwnTransfer,
                        balanceAfterMinor = row.balanceAfterMinor,
                        externalKey = externalKey,
                    ),
                )
                existingKeys += externalKey
                reconciled++
                continue
            }
            val id = db.transactionDao().insert(
                TransactionEntity(
                    accountId = account.id,
                    amountMinor = row.amountMinor,
                    currency = statement.currency,
                    occurredAt = occurred,
                    postedAt = row.postedDate.atMillis(),
                    merchantId = merchant?.id,
                    rawCounterparty = row.merchantRaw ?: row.beneficiaryName,
                    counterpartyIban = row.beneficiaryAccount,
                    categoryId = merchant?.categoryId,
                    note = row.description.takeIf { it != row.merchantRaw },
                    status = TxStatus.CONFIRMED,
                    source = TxSource.STATEMENT,
                    isTransfer = isOwnTransfer,
                    balanceAfterMinor = row.balanceAfterMinor,
                    externalKey = externalKey,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (id > 0) {
                inserted++
                existingKeys += externalKey
            } else duplicates++
        }

        pairOwnTransfers(account, statement.periodFrom, statement.periodTo)
        onPhase(Phase.VERIFYING)
        val reviewCandidates = if (statement.periodFrom != null && statement.periodTo != null) {
            val safeTo = statement.periodTo.minusDays(3)
            if (safeTo >= statement.periodFrom) {
                db.transactionDao().reconciliationCandidates(
                    account.id,
                    statement.periodFrom.atMillis(),
                    safeTo.plusDays(1).atMillis() - 1,
                )
            } else emptyList()
        } else emptyList()
        val importId = db.statementImportDao().insert(
            run {
                val groupId = requireNotNull(account.groupId)
                val source = db.statementSourceDao().forAccount(account.id)
                val sourceId = source?.id ?: db.statementSourceDao().insert(
                    StatementSourceEntity(
                        groupId = groupId,
                        type = StatementSourceType.ACCOUNT,
                        accountId = account.id,
                        label = account.iban ?: account.name,
                    ),
                )
            StatementImportEntity(
                accountId = account.id,
                sourceId = sourceId,
                fileName = fileName,
                origin = origin,
                periodFrom = statement.periodFrom?.toEpochDay(),
                periodTo = statement.periodTo?.toEpochDay(),
                openingBalanceMinor = statement.openingBalanceMinor,
                closingBalanceMinor = statement.closingBalanceMinor,
                totalRows = statement.rows.size,
                inserted = inserted,
                duplicates = duplicates,
                reconciled = reconciled,
                reviewCount = reviewCandidates.size,
                importedAt = System.currentTimeMillis(),
            )
            },
        )
        reviewCandidates.forEach { candidate ->
            val issue = ReconciliationIssueEntity(
                accountId = account.id,
                transactionId = candidate.id,
                importId = importId,
                createdAt = System.currentTimeMillis(),
            )
            if (db.reconciliationIssueDao().insert(issue) <= 0) {
                db.reconciliationIssueDao().moveOpenToImport(candidate.id, importId)
            }
        }
        return Result(
            accountId = account.id,
            accountCreated = accountCreated,
            totalRows = statement.rows.size,
            inserted = inserted,
            duplicates = duplicates,
            reconciled = reconciled,
            importId = importId,
            reviewCount = reviewCandidates.size,
        )
    }

    private suspend fun resolveMerchant(raw: String): MerchantEntity? {
        val key = MerchantNormalizer.normalize(raw)
        if (key.isEmpty()) return null
        db.merchantDao().resolve(key)?.let { return it }
        val id = db.merchantDao().insert(
            MerchantEntity(normalizedKey = key, displayName = MerchantNormalizer.displayName(raw)),
        )
        // id == -1 при гонке (IGNORE) — перечитываем
        return if (id > 0) db.merchantDao().byKey(key) else db.merchantDao().resolve(key)
    }

    private fun LocalDate.atMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun pairOwnTransfers(account: AccountEntity, from: LocalDate?, to: LocalDate?) {
        val groupId = account.groupId ?: return
        val fromMillis = (from ?: return).minusDays(3).atMillis()
        val toMillis = (to ?: return).plusDays(4).atMillis() - 1
        // Re-evaluate imported conversions: a later currency statement can reveal a closer,
        // correct leg than the best candidate available during the first import.
        db.transactionDao().conversionTransfers(groupId, fromMillis, toMillis)
            .forEach { db.transactionDao().clearTransferGroup(it.id) }
        val candidates = db.transactionDao().ungroupedTransfers(groupId, fromMillis, toMillis)
            .sortedWith(compareBy<TransactionEntity> { it.occurredAt }
                .thenBy { if (it.counterpartyIban != null) 0 else 1 })
            .toMutableList()
        val accountsById = db.accountDao().byGroup(groupId).associateBy { it.id }
        fun isExchange(tx: TransactionEntity): Boolean = tx.note?.let { note ->
            note.contains("exchange", ignoreCase = true) || note.contains("კონვერტ", ignoreCase = true)
        } == true
        while (candidates.isNotEmpty()) {
            val first = candidates.removeAt(0)
            fun matches(other: TransactionEntity): Boolean {
                val exactTransfer = other.currency == first.currency && other.amountMinor == -first.amountMinor
                val firstAccount = accountsById[first.accountId]
                val otherAccount = accountsById[other.accountId]
                val linkedConversion = first.currency != other.currency &&
                    (first.amountMinor < 0) != (other.amountMinor < 0) &&
                    (isExchange(first) || isExchange(other)) &&
                    (first.counterpartyIban == otherAccount?.iban || other.counterpartyIban == firstAccount?.iban)
                val distance = kotlin.math.abs(other.occurredAt - first.occurredAt)
                return other.accountId != first.accountId &&
                    ((exactTransfer && distance <= 3L * 24 * 60 * 60 * 1000) ||
                        (linkedConversion && distance <= 12L * 60 * 60 * 1000))
            }
            val matchIndex = candidates.indices.filter { matches(candidates[it]) }
                .minByOrNull { candidateIndex ->
                    val other = candidates[candidateIndex]
                    val firstAccount = accountsById[first.accountId]
                    val otherAccount = accountsById[other.accountId]
                    val mutualIban = first.counterpartyIban == otherAccount?.iban &&
                        other.counterpartyIban == firstAccount?.iban
                    (if (mutualIban) 0L else 10L * 24 * 60 * 60 * 1000) +
                        kotlin.math.abs(other.occurredAt - first.occurredAt)
                }
                ?: -1
            if (matchIndex < 0) continue
            val second = candidates.removeAt(matchIndex)
            val transferGroupId = db.transactionDao().insertTransferGroup(
                TransferGroupEntity(
                    type = if (first.currency == second.currency) TransferGroupType.TRANSFER else TransferGroupType.CONVERSION,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            db.transactionDao().setTransferGroup(first.id, transferGroupId)
            db.transactionDao().setTransferGroup(second.id, transferGroupId)
        }
    }
}
