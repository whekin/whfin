package dev.whekin.whfin.ui.bank

import android.content.Context
import android.content.Intent
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.FinancialGroupType

enum class SupportedBankApp(val packageName: String, val displayName: String) {
    CREDO("ge.qwerty.credo", "MyCredo"),
    TBC("com.icomvision.bsc.tbc", "TBC Bank"),
}

internal fun supportedBankApp(provider: String?): SupportedBankApp? = when (
    provider?.lowercase()?.filter(Char::isLetterOrDigit)
) {
    "credo", "credobank", "mycredo" -> SupportedBankApp.CREDO
    "tbc", "tbcbank" -> SupportedBankApp.TBC
    else -> null
}

/** Bank identity comes from the ledger's group, never from an editable account nickname. */
internal fun bankAppForGroup(group: FinancialGroupEntity?): SupportedBankApp? =
    group?.takeIf { it.type == FinancialGroupType.BANK && !it.isArchived }
        ?.let { supportedBankApp(it.provider ?: it.name) }

internal fun bankAppsForReserve(
    accounts: List<AccountEntity>,
    groups: List<FinancialGroupEntity>,
    currency: String,
    isLaunchable: (String) -> Boolean,
): List<SupportedBankApp> {
    val groupById = groups.associateBy { it.id }
    return accounts.asSequence()
        .filter { it.currency == currency && it.fundRole == FundRole.RESERVE }
        .mapNotNull { account -> account.groupId?.let(groupById::get) }
        .mapNotNull(::bankAppForGroup)
        .distinct()
        .filter { isLaunchable(it.packageName) }
        .toList()
}

internal fun Context.canLaunch(app: SupportedBankApp): Boolean =
    packageManager.getLaunchIntentForPackage(app.packageName) != null

internal fun Context.launchBank(app: SupportedBankApp): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { startActivity(intent); true }.getOrDefault(false)
}
