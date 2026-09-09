package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.util.Locale

enum class BankSmsBank(val provider: String) {
    CREDO("Credo"), TBC("TBC");
    fun classify(body: String): BankSmsMessage.Classification = when (this) {
        CREDO -> CredoSmsParser.classify(body)
        TBC -> TbcSmsParser.classify(body)
    }
    fun key(body: String): String = if (this == CREDO) smsExternalKey(body) else "sms|tbc|" + smsExternalKey(body).removePrefix("sms|")
    /** Legacy unbound ledgers predate multi-bank SMS and belong to the Credo routing path. */
    fun accepts(account: AccountEntity, providerOrName: String?): Boolean {
        if (account.type !in setOf(dev.whekin.whfin.data.db.AccountType.BANK, dev.whekin.whfin.data.db.AccountType.SAVINGS)) return false
        val ibanBank = account.iban?.uppercase(Locale.ROOT)?.takeIf { it.length >= 6 }?.substring(4, 6)
        if (ibanBank in setOf("CD", "TB", "BG")) return ibanBank == if (this == CREDO) "CD" else "TB"
        return when (providerOrName?.lowercase(Locale.ROOT)?.filter(Char::isLetter)) {
            "tbc", "tbcbank" -> this == TBC
            "credo", "credobank", "mycredo" -> this == CREDO
            "bog", "bankofgeorgia" -> false
            else -> this == CREDO
        }
    }
    suspend fun accepts(db: WhfinDatabase, account: AccountEntity): Boolean {
        val group = account.groupId?.let { db.financialGroupDao().byId(it) }
        return accepts(account, group?.provider ?: group?.name)
    }
    companion object {
        fun fromKey(key: String) = if (key.startsWith("sms|tbc|")) TBC else CREDO
        fun fromSender(sender: String?): BankSmsBank? = when (sender?.lowercase(Locale.ROOT)?.filterNot(Char::isWhitespace)) {
            "tbcsms", "tbc" -> TBC
            "91000", "credobank", "credo", "mycredo" -> CREDO
            else -> null
        }
    }
}
