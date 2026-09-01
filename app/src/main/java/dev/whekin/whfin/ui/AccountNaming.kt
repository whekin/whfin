package dev.whekin.whfin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.BankProduct

/**
 * How an account is named once the screen around it has already said what it can.
 *
 * A statement names every ledger it creates "<Bank> <CUR> •<last4>", so anything printing that name
 * next to a bank heading, a currency label, or the number itself says each of them twice. This lived
 * inside the Accounts screen, which is why the SMS routing sheet — the other place that lists ledgers
 * to choose between — printed "Credo · Credo GEL •0001" over "••0001 · GEL": the bank, the number and
 * the currency, each of them twice, and the number in the notation this app uses for a card.
 */

/**
 * The part of a ledger's name that has not already been printed above it, or null.
 *
 * WHFIN writes the seeded cash names itself, in whatever language was current at the time, so such a
 * name is a placeholder rather than something its owner chose.
 */
fun ledgerOwnName(account: AccountEntity, sourceName: String?): String? {
    var name = account.name
    if (!sourceName.isNullOrBlank()) name = name.replace(sourceName, " ", ignoreCase = true)
    name = name.replace(
        Regex("(?<!\\p{L})${Regex.escape(account.currency)}(?!\\p{L})", RegexOption.IGNORE_CASE),
        " ",
    )
    account.iban?.takeLast(4)?.let { tail ->
        name = name.replace(Regex("[•·]?\\s*${Regex.escape(tail)}"), " ")
    }
    return name.replace(Regex("[\\s•·,\\-]+"), " ").trim()
        .takeIf { it.isNotBlank() && it !in SEEDED_CASH_NAMES }
}

/**
 * The account's own number, said as an account number.
 *
 * `••0001` is this app's card mask, and a deposit has no card at all — only an account. Printing the
 * account's tail in the card's notation claimed a card that does not exist.
 */
@Composable
fun accountNumberLabel(account: AccountEntity): String? =
    account.iban?.takeLast(4)?.let { stringResource(R.string.account_iban_short, it) }

@Composable
fun accountProductLabel(product: BankProduct?): String? = when (product) {
    BankProduct.CURRENT_ACCOUNT -> stringResource(R.string.account_product_current)
    BankProduct.DEMAND_DEPOSIT -> stringResource(R.string.account_product_demand_deposit)
    BankProduct.TERM_DEPOSIT -> stringResource(R.string.account_product_term_deposit)
    null -> null
}

/**
 * The two lines that name one ledger in a list of ledgers to choose between.
 *
 * Title: what the owner named it, else the number the bank gave it, else what kind of account it is.
 * Second line: only what the title and the list around it have not already said. The currency is
 * named once by the list's own label, the bank only when the rows do not all belong to one, and the
 * number never twice.
 */
@Composable
fun accountChoiceLabels(
    account: AccountEntity,
    sourceName: String?,
    showSource: Boolean,
): Pair<String, String?> {
    val own = ledgerOwnName(account, sourceName)
    val number = accountNumberLabel(account)
    val product = accountProductLabel(account.bankProduct)
    val title = own ?: number ?: product ?: account.currency
    val supporting = listOfNotNull(
        sourceName?.takeIf { showSource },
        product.takeIf { title != product },
        number.takeIf { own != null },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    return title to supporting
}

/**
 * The names WHFIN writes for a cash ledger nobody has renamed.
 *
 * They are placeholders, not descriptions: the source heading already says the same thing, in the
 * language the screen is actually being read in.
 */
private val SEEDED_CASH_NAMES = setOf("Cash", "Наличные")
