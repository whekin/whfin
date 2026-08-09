package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement

class AmbiguousBankLedgerException(message: String) : Exception(message)

/** What a statement does to the ledger it belongs to, before a single row is read. */
enum class LedgerEffect {
    /** The ledger already exists with this IBAN. */
    UNCHANGED,

    /** An SMS-created ledger that never had an IBAN gets one. */
    ADOPTED,

    /** Nothing matches, so the import brings a ledger into existence. */
    CREATED,
}

internal data class ResolvedBankLedger(
    val account: AccountEntity,
    val created: Boolean,
    val adopted: Boolean,
)

/** Which ledger a statement belongs to, decided without writing anything. */
internal sealed interface BankLedgerPlan {
    val effect: LedgerEffect

    /** The ledger involved: the one that exists, or the one that would be created. */
    val ledgerName: String

    /** The row this statement would land in; null while that row does not exist yet. */
    val account: AccountEntity?

    data class Existing(override val account: AccountEntity) : BankLedgerPlan {
        override val effect = LedgerEffect.UNCHANGED
        override val ledgerName: String get() = account.name
    }

    data class Adopt(override val account: AccountEntity, val iban: String) : BankLedgerPlan {
        override val effect = LedgerEffect.ADOPTED
        override val ledgerName: String get() = account.name
    }

    data class Create(
        val groupId: Long?,
        val bank: BankProfile,
        val currency: String,
        val iban: String,
    ) : BankLedgerPlan {
        override val effect = LedgerEffect.CREATED
        override val account: AccountEntity? get() = null
        override val ledgerName: String get() = "${bank.displayName} $currency •${iban.takeLast(4)}"
    }
}

/**
 * Resolves one statement to exactly one currency ledger.
 *
 * SMS routing can create an unbound ledger before WHFIN knows its IBAN. A later statement adopts that
 * row only when the choice is unique; guessing between two same-currency ledgers would be worse than a
 * visible import failure.
 *
 * Deciding and writing are separate here for the same reason they are in [ImportPlanner]: a file the
 * user has not accepted yet must be able to answer "this creates an account" without creating one.
 */
internal class BankLedgerResolver(private val db: WhfinDatabase) {

    /** Reads only. Throws when the statement cannot be attributed to a single ledger. */
    suspend fun plan(statement: BankStatement): BankLedgerPlan {
        db.accountDao().byIbanAndCurrency(statement.accountIban, statement.currency)?.let {
            return BankLedgerPlan.Existing(it)
        }
        val bank = statement.bank
        // No group means no ledgers to adopt, so the group is created together with the ledger.
        val group = db.financialGroupDao().byProvider(FinancialGroupType.BANK, bank.provider)
            ?: return BankLedgerPlan.Create(null, bank, statement.currency, statement.accountIban)
        val unbound = db.accountDao().unboundBankLedgers(group.id, statement.currency)
        if (unbound.size > 1) {
            throw AmbiguousBankLedgerException(
                "Several ${bank.displayName} ${statement.currency} ledgers need an IBAN before import.",
            )
        }
        unbound.singleOrNull()?.let { return BankLedgerPlan.Adopt(it, statement.accountIban) }
        return BankLedgerPlan.Create(group.id, bank, statement.currency, statement.accountIban)
    }

    suspend fun resolve(statement: BankStatement): ResolvedBankLedger =
        when (val plan = plan(statement)) {
            is BankLedgerPlan.Existing -> ResolvedBankLedger(plan.account, created = false, adopted = false)
            is BankLedgerPlan.Adopt -> {
                val adopted = plan.account.copy(iban = plan.iban)
                db.accountDao().update(adopted)
                ResolvedBankLedger(adopted, created = false, adopted = true)
            }
            is BankLedgerPlan.Create -> {
                val groupId = plan.groupId ?: db.financialGroupDao().insert(
                    FinancialGroupEntity(
                        name = plan.bank.displayName,
                        type = FinancialGroupType.BANK,
                        provider = plan.bank.provider,
                    ),
                )
                val id = db.accountDao().insert(
                    AccountEntity(
                        name = plan.ledgerName,
                        type = AccountType.BANK,
                        groupId = groupId,
                        currency = plan.currency,
                        iban = plan.iban,
                    ),
                )
                ResolvedBankLedger(requireNotNull(db.accountDao().byId(id)), created = true, adopted = false)
            }
        }
}
