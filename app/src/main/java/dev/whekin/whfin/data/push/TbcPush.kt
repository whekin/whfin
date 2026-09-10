package dev.whekin.whfin.data.push

import dev.whekin.whfin.data.sms.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Text fields only: no PendingIntent, parcelled extras, images or opaque authentication payloads. */
data class BankPush(
    val packageName: String,
    val notificationKey: String,
    val postedAt: Long,
    val title: String = "",
    val text: String = "",
    val bigText: String = "",
    val subText: String = "",
    val lines: List<String> = emptyList(),
    val channel: String = "",
    val category: String = "",
    val groupSummary: Boolean = false,
    val truncated: Boolean = false,
) {
    override fun toString() = "BankPush(redacted)"
    fun fields() = listOf(title, text, bigText, subText) + lines
    fun json(): JSONObject = JSONObject().put("package", packageName).put("key", notificationKey)
        .put("postedAt", postedAt).put("title", title).put("text", text).put("bigText", bigText)
        .put("subText", subText).put("lines", JSONArray(lines)).put("channel", channel)
        .put("category", category).put("groupSummary", groupSummary).put("truncated", truncated)
    companion object {
        fun fromJson(o: JSONObject) = BankPush(o.getString("package"), o.getString("key"), o.getLong("postedAt"),
            o.optString("title"), o.optString("text"), o.optString("bigText"), o.optString("subText"),
            o.optJSONArray("lines")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty(),
            o.optString("channel"), o.optString("category"), o.optBoolean("groupSummary"), o.optBoolean("truncated"))
    }
}

object TbcPush {
    const val PACKAGE = "com.icomvision.bsc.tbc"
    private val auth = Regex("(?iu)\\b(?:otp|pin|password|passcode|code|log[ -]?in|authorization|sign[ -]?in|verification|authentication)\\b|парол|однораз|код|ავტორიზ|ერთჯერად|კოდი")
    private val nonExpense = Regex("(?iu)refund|reversal|deposit|received|incoming|cancel|declin|reject|ჩარიცხ|დაბრუნ|возврат|пополн|зачисл|отмен")
    fun sensitive(push: BankPush): Boolean = push.fields().any {
        auth.containsMatchIn(it) || it.trim().matches(Regex("[0-9]{4,8}"))
    }
    /** Only the two observed, amount-first card purchase templates imply an expense. */
    fun classify(push: BankPush): BankSmsMessage.Classification {
        if (push.packageName != PACKAGE || push.groupSummary || push.truncated || sensitive(push))
            return BankSmsMessage.Classification.Ignored(BankSmsMessage.IgnoreReason.UNRELATED, false)
        if (push.fields().any(nonExpense::containsMatchIn)) return BankSmsMessage.Classification.Unrecognized
        val candidates = (listOf(push.bigText, push.text, push.lines.joinToString("\n"))).filter(String::isNotBlank).distinct()
        val parsed = candidates.mapNotNull { body ->
            if (!body.trim().matches(Regex("(?s)^[0-9][0-9,.]*\\s+[A-Z]{3}\\s+.*"))) return@mapNotNull null
            (TbcSmsParser.classify(body) as? BankSmsMessage.Classification.Parsed)?.sms as? BankSmsMessage.CardPayment
        }.distinct()
        val groups = parsed.groupBy { listOf(it.amountMinor, it.currency, it.cardLast4, it.merchantRaw, it.timestamp) }
        val payment = groups.values.singleOrNull()?.let { group -> group.firstOrNull { it.balanceCurrency != null } ?: group.first() }
        return payment?.let { BankSmsMessage.Classification.Parsed(it) } ?: BankSmsMessage.Classification.Unrecognized
    }
    fun ledgerKey(push: BankPush, parsed: BankSmsMessage.Classification): String {
        val sms = (parsed as? BankSmsMessage.Classification.Parsed)?.sms as? BankSmsMessage.CardPayment
        val identity = sms?.let { listOf(it.amountMinor, it.currency, it.cardLast4, it.merchantRaw.trim(), it.timestamp).joinToString("|") }
            ?: push.fields().joinToString("\n")
        return "sms|tbc|push|" + hash(push.notificationKey + "|" + identity)
    }
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
