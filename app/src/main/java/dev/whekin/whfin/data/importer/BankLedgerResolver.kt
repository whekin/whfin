package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement

class AmbiguousBankLedgerException(message: String) : Exception(message)

internal data class ResolvedBankLedger(
    val account: AccountEntity,
    val created: Boolean,
    val adopted: Boolean,
)

/**
 * Resolves one statement to exactly one currency ledger.
 *
 * SMS routing can create an unbound ledger before WHFIN knows its IBAN. A later statement adopts that
 * row only when the choice is unique; guessing between two same-currency ledgers would be worse than a
 * visible import failure.
 */
internal class BankLedgerResolver(private val db: WhfinDatabase) {
    suspend fun resolve(statement: BankStatement): ResolvedBankLedger {
        db.accountDao().byIbanAndCurrency(statement.accountIban, statement.currency)?.let {
            return ResolvedBankLedger(it, created = false, adopted = false)
        }

        val bank = statement.bank
        val group = db.financialGroupDao().byProvider(FinancialGroupType.BANK, bank.provider)
        val groupId = group?.id ?: db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = bank.displayName,
                type = FinancialGroupType.BANK,
                provider = bank.provider,
            ),
        )
        val unbound = db.accountDao().unboundBankLedgers(groupId, statement.currency)
        if (unbound.size > 1) {
            throw AmbiguousBankLedgerException(
                "Several ${bank.displayName} ${statement.currency} ledgers need an IBAN before import.",
            )
        }
        unbound.singleOrNull()?.let { account ->
            val adopted = account.copy(iban = statement.accountIban)
            db.accountDao().update(adopted)
            return ResolvedBankLedger(adopted, created = false, adopted = true)
        }

        val id = db.accountDao().insert(
            AccountEntity(
                name = "${bank.displayName} ${statement.currency} •${statement.accountIban.takeLast(4)}",
                type = AccountType.BANK,
                groupId = groupId,
                currency = statement.currency,
                iban = statement.accountIban,
            ),
        )
        return ResolvedBankLedger(requireNotNull(db.accountDao().byId(id)), created = true, adopted = false)
    }
}
