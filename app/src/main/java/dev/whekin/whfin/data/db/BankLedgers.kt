package dev.whekin.whfin.data.db

/** The only bank whose SMS and statements are wired up today. */
const val CREDO_PROVIDER = "Credo"

/**
 * Adds one currency ledger to [provider]'s bank group and returns its id, creating the group when
 * this is its first ledger. The IBAN stays empty on purpose: a statement import fills it in later
 * and reconciles against this ledger instead of creating a second one.
 *
 * The caller owns the transaction, because a group without its first ledger is not a valid state.
 */
suspend fun WhfinDatabase.insertBankLedger(
    provider: String,
    name: String,
    currency: String,
): Long {
    val groupId = financialGroupDao().byProvider(FinancialGroupType.BANK, provider)?.id
        ?: financialGroupDao().insert(
            FinancialGroupEntity(
                name = provider,
                type = FinancialGroupType.BANK,
                provider = provider,
            ),
        )
    return accountDao().insert(
        AccountEntity(
            name = name,
            type = AccountType.BANK,
            currency = currency,
            groupId = groupId,
        ),
    )
}
