package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole

/**
 * Where the money for a card top-up should come from.
 *
 * A low card is not news to the person: they know the card is empty, and the answer is always to move
 * money onto it. Home therefore names the likely source itself instead of handing over an empty
 * transfer form. The order encodes how the money actually moves: the same bank first, because a
 * top-up inside one bank is instant and free; then anything that is not locked by a term, since
 * breaking a term is a decision and not a top-up; then money set aside as reserve, which is exactly
 * what a top-up is for; then whatever simply holds the most.
 *
 * A ledger with nothing in it is never proposed: a prefilled transfer that cannot be made is worse
 * than no prefill at all. A term deposit is still offered when nothing else has money, because a
 * proposal the person can change beats a form they have to fill from scratch.
 */
internal fun cardTopUpSource(
    accounts: List<AccountEntity>,
    balancesMinor: Map<Long, Long>,
    target: AccountEntity,
): AccountEntity? = accounts
    .filter { candidate ->
        candidate.id != target.id &&
            candidate.type != AccountType.CRYPTO &&
            candidate.currency.equals(target.currency, ignoreCase = true) &&
            (balancesMinor[candidate.id] ?: 0L) > 0L
    }
    .sortedWith(
        compareByDescending<AccountEntity> { it.groupId != null && it.groupId == target.groupId }
            .thenByDescending { it.bankProduct != BankProduct.TERM_DEPOSIT }
            .thenByDescending { it.fundRole == FundRole.RESERVE }
            .thenByDescending { balancesMinor[it.id] ?: 0L },
    )
    .firstOrNull()
