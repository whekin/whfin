package dev.whekin.whfin.ui.settings

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.importer.CredoDuplicateReview
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.formatMinor

@Composable
internal fun CredoDuplicateReviewSheet(
    preview: CredoDuplicateReview.Preview,
    busy: Boolean = false,
    error: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember(preview) { mutableStateOf(emptySet<Long>()) }
    val pairs = preview.pairs.filter { it.original.id in selected }
    val totals = runCatching { pairs.groupBy { it.original.currency }.map { (currency, rows) ->
        formatMinor(rows.fold(0L) { sum, p -> Math.subtractExact(sum, p.original.amountMinor) }, currency)
    }.joinToString(" + ") }.getOrNull()
    FormSheet(stringResource(R.string.credo_duplicate_review), { if (!busy) onDismiss() },
        stringResource(R.string.credo_duplicate_merge_count, selected.size), selected.isNotEmpty() && totals != null && !busy && !error,
        { onConfirm(selected) }) {
        Text(stringResource(R.string.credo_duplicate_batch_body))
        if (preview.pairs.isEmpty()) Text(stringResource(R.string.credo_duplicate_empty))
        else WhfinButton(stringResource(if (selected.size == preview.pairs.size) R.string.credo_duplicate_clear else R.string.credo_duplicate_all),
            { selected = if (selected.size == preview.pairs.size) emptySet() else preview.pairs.map { it.original.id }.toSet() },
            style = WhfinActionStyle.Secondary, enabled = !busy)
        WhfinCheckList(preview.pairs.map { p -> WhfinChoice(p.original.id,
            "${p.original.rawCounterparty} · ${formatMinor(p.original.amountMinor, p.original.currency)}",
            "${LedgerCalendar.dayOf(p.original.occurredAt)} · ${p.accountLabel}") }, selected,
            { selected = if (it in selected) selected - it else selected + it }, enabled = !busy)
        if (selected.isNotEmpty() && totals != null) Text(stringResource(R.string.credo_duplicate_batch_effect, selected.size, totals))
        if (error) Text(stringResource(R.string.credo_balance_changed), color = MaterialTheme.colorScheme.error)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Light", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Large", fontScale = 1.5f)
@androidx.compose.ui.tooling.preview.Preview(name = "Compact", heightDp = 480)
@Composable
private fun DuplicateReviewPreview() {
    dev.whekin.whfin.ui.theme.WhfinTheme {
        CredoDuplicateReviewSheet(CredoDuplicateReview.Preview(emptyList(), emptyList(), emptyList()), onDismiss = {}, onConfirm = {})
    }
}
