package dev.whekin.whfin.ui.setup

/** Typed setup destinations; saved keys retain the earlier string-stack format across upgrades. */
internal enum class SetupPage(val savedKey: String) {
    Account("account"), Suggestions("suggestions"), Credo("credo"), Tbc("tbc"),
    Lock("lock"), Settings("settings"), Statements("statements"), Backup("backup"),
    Messages("messages"), Accounts("accounts"), Overview("overview"),
    Income("income"), Savings("savings"), Debts("debts"), Categories("categories"),
    Intelligence("intelligence"), People("people"), Privacy("privacy"),
    About("about"), Corrections("corrections"), Health("health"),
    History("history"), Push("push");

    companion object {
        fun fromSaved(value: String?): SetupPage? = entries.firstOrNull { it.savedKey == value }
    }
}
