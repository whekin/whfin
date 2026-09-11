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
    onConfirm: (Long) -> Unit,
) {
    var selected by remember(preview) { mutableStateOf<Long?>(null) }
    val pair = preview.pairs.singleOrNull { it.original.id == selected }
    FormSheet(stringResource(R.string.credo_duplicate_review), { if (!busy) onDismiss() },
        stringResource(R.string.credo_duplicate_merge), pair != null && !busy && !error,
        { selected?.let(onConfirm) }) {
        Text(stringResource(R.string.credo_duplicate_body))
        if (preview.pairs.isEmpty()) Text(stringResource(R.string.credo_duplicate_empty))
        WhfinChoiceList(preview.pairs.map { p -> WhfinChoice(p.original.id,
            "${p.original.rawCounterparty} · ${formatMinor(p.original.amountMinor, p.original.currency)}",
            "${LedgerCalendar.dayOf(p.original.occurredAt)} · ${p.accountLabel}") }, selected,
            { if (!busy) selected = it })
        pair?.let { p ->
            Text(stringResource(R.string.credo_duplicate_effect,
                formatMinor(-p.original.amountMinor, p.original.currency),
                formatMinor(requireNotNull(p.original.balanceAfterMinor), p.original.currency),
                formatMinor(requireNotNull(p.newer.balanceAfterMinor), p.newer.currency)))
        }
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
