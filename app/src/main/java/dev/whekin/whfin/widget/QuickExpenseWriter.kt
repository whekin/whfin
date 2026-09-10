package dev.whekin.whfin.widget

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.*

/** Account fallback, a new beneficiary, the expense and its shares commit together. */
internal suspend fun writeQuickExpense(db: WhfinDatabase, amountMinor: Long, currency: String,
    accountId: Long?, description: String?, categoryId: Long?, beneficiary: ExpenseBeneficiary?): Long = db.withTransaction {
    require(amountMinor > 0)
    val requested = accountId?.let { db.accountDao().byId(it) }?.takeIf { it.currency == currency }
    val account = requested ?: db.accountDao().allActive().firstOrNull { it.type == AccountType.CASH && it.currency == currency }
        ?: db.accountDao().insert(AccountEntity(name = if (currency == "GEL") "Cash" else "Cash $currency",
            type = AccountType.CASH, currency = currency, sortOrder = 1000)).let { requireNotNull(db.accountDao().byId(it)) }
    TransactionMutationModule(db).createManual(ManualMutation(account.id, -amountMinor,
        occurredAt = System.currentTimeMillis(), note = description, categoryId = categoryId), beneficiary = beneficiary)
}
