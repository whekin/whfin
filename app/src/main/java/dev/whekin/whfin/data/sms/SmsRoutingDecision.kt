package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason

/** A missing ledger is a choice only while the bank has not already covered the operation. */
private fun needsRoutingDecision(outcome: SmsDiagnosticOutcome, reason: SmsDiagnosticReason?): Boolean =
    reason != SmsDiagnosticReason.STATEMENT_COVERS_PERIOD && outcome in setOf(
        SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
        SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
    )

fun SmsDiagnosticEntity.needsRoutingDecision(): Boolean = needsRoutingDecision(outcome, reason)
fun SmsImportResult.needsRoutingDecision(): Boolean = needsRoutingDecision(outcome, reason)
