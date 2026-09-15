package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
internal fun BankSyncSheet(times: List<Pair<String, Long?>>, onDismiss: () -> Unit,
    statuses: List<dev.whekin.whfin.data.sync.BankSyncStatus> = emptyList(), onSync: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BankSyncContent(times, onSync, statuses)
    }
}

@Composable
internal fun BankSyncContent(times: List<Pair<String, Long?>>, onSync: (String) -> Unit,
    statuses: List<dev.whekin.whfin.data.sync.BankSyncStatus> = emptyList()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (statuses.isEmpty()) R.string.bank_sync_title else R.string.bank_sync_status_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (statuses.isEmpty()) R.string.bank_sync_hint else R.string.bank_sync_status_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val banks = (times + statuses.map { it.bank to null }).distinctBy { it.first }
        if (banks.isEmpty()) dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
        for ((bank, timestamp) in banks) {
            val status = statuses.firstOrNull { it.bank == bank }
            val age = timestamp?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0) / 86_400_000).toInt() }
            val description = if (status != null) bankSyncDescription(status) else when {
                age == null -> stringResource(R.string.bank_sync_first)
                age == 0 -> stringResource(R.string.bank_sync_today)
                age >= 7 -> stringResource(R.string.bank_sync_week_due, age)
                else -> stringResource(R.string.bank_sync_age, age)
            }
            Column(Modifier.fillMaxWidth().clickable { onSync(bank) }.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    dev.whekin.whfin.ui.banks.BankBrand(bank)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun bankSyncDescription(status: dev.whekin.whfin.data.sync.BankSyncStatus): String {
    val phase = stringResource(when (status.phase) {
        dev.whekin.whfin.data.sync.SyncPhase.AUTHORIZING -> R.string.bank_sync_authorizing
        dev.whekin.whfin.data.sync.SyncPhase.CONFIRMATION -> R.string.bank_sync_confirmation
        dev.whekin.whfin.data.sync.SyncPhase.READING -> R.string.bank_sync_reading
        dev.whekin.whfin.data.sync.SyncPhase.MATCHING -> R.string.bank_sync_matching
        dev.whekin.whfin.data.sync.SyncPhase.HISTORY -> R.string.bank_sync_history
        dev.whekin.whfin.data.sync.SyncPhase.COMPLETE -> R.string.bank_sync_complete
        dev.whekin.whfin.data.sync.SyncPhase.ATTENTION -> R.string.bank_sync_attention
        dev.whekin.whfin.data.sync.SyncPhase.INTERRUPTED -> R.string.bank_sync_interrupted
    })
    return if (status.active && status.total > 0) phase + "\n" + stringResource(R.string.bank_sync_account_progress, status.current, status.total) else phase
}

@Preview(locale = "ru")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(fontScale = 1.5f, locale = "ru", heightDp = 400)
@Composable
private fun BankSyncPreview() = WhfinTheme {
    Surface { BankSyncContent(listOf("Credo" to (System.currentTimeMillis() - 8 * 86_400_000L), "TBC" to null), {}) }
}
