package dev.whekin.whfin.data.security

import android.content.Context

/** Non-secret, device-local opt-in; expiry of a cookie does not revoke the owner's choice. */
interface BankSessionChoice {
    fun read(): Boolean?
    fun problem(): String? = null
    fun reportProblem(code: String?) = Unit
    fun write(remember: Boolean)
}
class DeviceBankSessionChoice(context: Context, bank: String) : BankSessionChoice {
    private val preferences = context.getSharedPreferences("whfin_${bank}_session_options", Context.MODE_PRIVATE)
    override fun problem(): String? = preferences.getString("problem", null)
    override fun reportProblem(code: String?) { preferences.edit().putString("problem", code).apply() }
    override fun read(): Boolean? = if (preferences.contains("remember")) preferences.getBoolean("remember", false) else null
    override fun write(remember: Boolean) { preferences.edit().putBoolean("remember", remember).apply() }
}
