package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.ui.feed.FeedViewModel
import dev.whekin.whfin.ui.formatMinor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CorrectionsScreen(viewModel: FeedViewModel = viewModel()) {
    val rows by viewModel.voidedImported.collectAsState()
    if (rows.isEmpty()) {
        WhfinStatePane(
            state = WhfinPaneState.Empty,
            title = stringResource(R.string.corrections_empty_title),
            body = stringResource(R.string.corrections_empty_body),
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WhfinSectionLabel(stringResource(R.string.corrections_section)) }
        items(rows, key = TransactionEntity::id) { row ->
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            row.rawCounterparty ?: row.note ?: stringResource(R.string.feed_no_description),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${formatMinor(row.amountMinor, row.currency)} · " +
                                Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.restoreImported(row.id) }) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(stringResource(R.string.account_restore))
                    }
                }
            }
        }
    }
}
