package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.ui.theme.WhfinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BankSyncSheet(times: List<Pair<String, Long?>>, onDismiss: () -> Unit, onSync: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BankSyncContent(times, onSync)
    }
}

@Composable
internal fun BankSyncContent(times: List<Pair<String, Long?>>, onSync: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.bank_sync_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.bank_sync_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (times.isEmpty()) dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
        for ((bank, timestamp) in times) {
            val age = timestamp?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0) / 86_400_000).toInt() }
            val description = when {
                age == null -> stringResource(R.string.bank_sync_first)
                age == 0 -> stringResource(R.string.bank_sync_today)
                age >= 7 -> stringResource(R.string.bank_sync_week_due, age)
                else -> stringResource(R.string.bank_sync_age, age)
            }
            WhfinLedgerRow(title = bank, supportingText = description, supportingMaxLines = 3,
                icon = Icons.Default.Sync, onClick = { onSync(bank) })
        }
    }
}

@Preview(locale = "ru")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(fontScale = 1.5f, locale = "ru", heightDp = 400)
@Composable
private fun BankSyncPreview() = WhfinTheme {
    Surface { BankSyncContent(listOf("Credo" to (System.currentTimeMillis() - 8 * 86_400_000L), "TBC" to null), {}) }
}
