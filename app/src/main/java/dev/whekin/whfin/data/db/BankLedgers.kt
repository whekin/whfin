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

/**
 * Whether this name is the one an import writes, rather than something its owner chose.
 *
 * A statement names a new ledger "<Bank> <CUR> •<last4>". That shape belongs to one currency, so it
 * must never be copied onto the other ledgers of the same IBAN — the account editor answers for the
 * whole container, and propagating this name left a USD ledger called "TBC GEL •0001". The bank name
 * is not needed to recognise it: the currency code and the account's own tail are the whole shape.
 */
fun isGeneratedLedgerName(name: String, iban: String?): Boolean {
    val tail = iban?.takeLast(4)?.takeIf { it.isNotBlank() } ?: return false
    return Regex("""^\s*\S+\s+([A-Z]{3})\s*[•·]?\s*${Regex.escape(tail)}\s*$""").matches(name.trim())
}

/** The currency such a generated name speaks for, or null when the name is not a generated one. */
fun generatedNameCurrency(name: String, iban: String?): String? {
    val tail = iban?.takeLast(4)?.takeIf { it.isNotBlank() } ?: return null
    return Regex("""^\s*\S+\s+([A-Z]{3})\s*[•·]?\s*${Regex.escape(tail)}\s*$""")
        .matchEntire(name.trim())?.groupValues?.get(1)
}

/**
 * Undoes a name one currency's ledger lent to all the others.
 *
 * The account editor answers for the whole IBAN, so saving it once copied the name the import had
 * written for the first ledger onto every currency under it, and a USD ledger ended up called
 * "TBC GEL •0001" — the bank's own naming, for the wrong money. Only a name generated for a
 * different currency is cleared; anything the owner wrote, and a generated name that still speaks
 * for its own ledger, is left exactly as it is. A cleared ledger is named by its bank, number and
 * product again, which is what the screens do with an empty name anyway.
 */
suspend fun WhfinDatabase.repairCrossCurrencyLedgerNames(): Int {
    var repaired = 0
    for (account in accountDao().allForIntegrity()) {
        if (account.type != AccountType.BANK || account.name.isBlank()) continue
        val named = generatedNameCurrency(account.name, account.iban) ?: continue
        if (named == account.currency) continue
        accountDao().update(account.copy(name = ""))
        repaired++
    }
    return repaired
}
