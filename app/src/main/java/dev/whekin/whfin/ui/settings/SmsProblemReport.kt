package dev.whekin.whfin.ui.settings

import android.content.Intent
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.sms.CredoSmsParser

internal object SmsProblemReport {
    const val subject = "WHFIN SMS parser report"

    fun redacted(
        appVersion: String,
        diagnostic: SmsDiagnosticEntity,
    ): String = buildString {
        appendLine(subject)
        appendLine()
        appendLine("App version: $appVersion")
        appendLine("Parser: Credo SMS / schema ${CredoSmsParser.SCHEMA_VERSION}")
        appendLine("Outcome: ${diagnostic.outcome.name}")
        diagnostic.reason?.let { appendLine("Reason: ${it.name}") }
        appendLine()
        appendLine("Redacted message:")
        append(
            "[Message text removed by WHFIN. Replace this with a redacted example if useful. " +
                "Do not include names, amounts, card or account numbers, balances, or identifiers.]",
        )
    }

    fun withOriginal(redactedReport: String, originalBody: String): String =
        redactedReport.trimEnd() +
            "\n\nOriginal SMS — deliberately included after confirmation:\n" +
            originalBody.trim()

    fun sendIntent(payload: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, payload)
}
