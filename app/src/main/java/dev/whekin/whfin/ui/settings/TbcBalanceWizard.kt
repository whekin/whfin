package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.importer.TbcSyncResult

/** Each currency gets a deliberate review. Only the final action applies the complete batch. */
@Composable
internal fun TbcBalanceWizard(
    result: TbcSyncResult,
    drafts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    error: String?,
    onConfirm: (List<Pair<String, Long>>) -> Unit,
    onOpenStatements: () -> Unit,
    onRefresh: () -> Unit,
    onShowResults: () -> Unit,
    onHideKeyboard: () -> Unit,
    onDone: (() -> Unit)?,
) {
    val waiting = result.needsStatement
    var showHelp by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var confirmSkip by remember { mutableStateOf(false) }
    val remote = waiting[index.coerceIn(waiting.indices)]
    val hasHistory = result.initialHistories.any { it.remote.key == remote.key }
    val valid = hasHistory && parseBookedBalance(drafts[remote.key].orEmpty()) != null
    val ready = waiting.mapNotNull { account ->
        if (result.initialHistories.none { it.remote.key == account.key }) null
        else parseBookedBalance(drafts[account.key].orEmpty())?.let { account.key to it }
    }
    if (confirmSkip && onDone != null) WhfinConfirmDialog(
        title = stringResource(R.string.tbc_continue_later_title),
        body = stringResource(R.string.tbc_continue_later_body, waiting.size),
        confirmLabel = stringResource(R.string.tbc_continue_later),
        dismissLabel = stringResource(R.string.action_cancel),
        confirmStyle = WhfinActionStyle.Secondary,
        onConfirm = { confirmSkip = false; onDone() }, onDismiss = { confirmSkip = false },
    )
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        // A new currency starts at its own heading, even after the previous page was scrolled.
        key(remote.key) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WhfinSectionLabel(stringResource(R.string.tbc_balance_step, index + 1, waiting.size))
                Text(remote.name.ifBlank { "TBC" }, style = MaterialTheme.typography.headlineSmall)
                Text("${remote.currency} · •${remote.iban.takeLast(4)}", style = MaterialTheme.typography.titleMedium)
                error?.let { WhfinNotice(stringResource(R.string.credo_sync_error_title),
                    stringResource(tbcErrorText(it)), kind = WhfinNoticeKind.Error) }
                if (hasHistory) {
                    WhfinField(drafts[remote.key].orEmpty(), { drafts[remote.key] = it },
                        "${stringResource(R.string.tbc_booked_balance_field)} · ${remote.currency}",
                        keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.tbc_balance_guide), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (remote.balanceMinor != null) Text(stringResource(R.string.tbc_balance_prefilled),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.tbc_balance_batch_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else WhfinNotice(stringResource(R.string.tbc_section_attention),
                    stringResource(R.string.tbc_balance_unavailable), kind = WhfinNoticeKind.Attention)
                if (result.errors.isNotEmpty()) WhfinNotice(
                    title = stringResource(R.string.tbc_other_accounts_attention),
                    body = stringResource(R.string.tbc_other_accounts_attention_body),
                    kind = WhfinNoticeKind.Attention,
                    actionLabel = stringResource(R.string.tbc_read_details), onAction = onShowResults,
                )
                WhfinButton(stringResource(R.string.tbc_balance_help), { showHelp = !showHelp }, style = WhfinActionStyle.Quiet)
                if (showHelp) Text(stringResource(R.string.tbc_balance_help_body), style = MaterialTheme.typography.bodyMedium)
                WhfinButton(stringResource(R.string.tbc_result_options), { showOptions = !showOptions }, style = WhfinActionStyle.Quiet)
                if (showOptions) {
                    WhfinButton(stringResource(R.string.statements_upload), onOpenStatements, style = WhfinActionStyle.Secondary)
                    WhfinButton(stringResource(R.string.tbc_refresh_read), onRefresh, style = WhfinActionStyle.Quiet)
                    if (result.reports.isNotEmpty() || result.errors.isNotEmpty())
                        WhfinButton(stringResource(R.string.tbc_read_details), onShowResults, style = WhfinActionStyle.Quiet)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index > 0) WhfinButton(stringResource(R.string.tbc_balance_previous),
                { onHideKeyboard(); onIndexChange(index - 1) }, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            val last = index == waiting.lastIndex
            WhfinButton(stringResource(if (last) R.string.tbc_confirm_balances else R.string.tbc_balance_next, waiting.size),
                { onHideKeyboard(); if (last) onConfirm(ready) else onIndexChange(index + 1) }, Modifier.fillMaxWidth(),
                enabled = valid && (!last || ready.size == waiting.size))
            if (onDone != null) WhfinButton(stringResource(R.string.tbc_continue_later),
                { onHideKeyboard(); confirmSkip = true }, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TBC balances light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "TBC balances dark", widthDp = 400, heightDp = 850,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "TBC balances RU compact", widthDp = 360, heightDp = 560,
    locale = "ru", fontScale = 1.5f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TbcBalanceGuidePreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    androidx.compose.material3.Surface {
        val remotes = listOf("GEL", "USD").map { currency ->
            dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", currency,
                "Everyday", balanceMinor = if (currency == "GEL") 128740L else 0L)
        }
        val result = TbcSyncResult(needsStatement = remotes, initialHistories = remotes.map {
            dev.whekin.whfin.data.importer.TbcInitialHistory(it, java.time.LocalDate.of(2025, 9, 1),
                java.time.LocalDate.of(2026, 9, 1), emptyList())
        })
        TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected, syncResult = result), true, onDone = {})
    }
}
