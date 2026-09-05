package dev.whekin.whfin.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.transfer.OwnTransferSide
import dev.whekin.whfin.ui.formatMinor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the owner chose to do with the far side of a movement. */
sealed interface OwnTransferChoice {
    data class Existing(val sides: List<OwnTransferSide>) : OwnTransferChoice

    data class Recorded(
        val accountId: Long,
        val amountMinor: Long,
        val currency: String,
        val occurredAt: Long,
    ) : OwnTransferChoice
}

/**
 * Joins one movement to its other side, or writes that side down when nothing will ever bring it.
 *
 * The two answers are one sheet because they answer one question — "where did this money go" — and
 * splitting them would make the owner decide, before seeing anything, whether the app already knows
 * about the far side. The offered rows come first because they usually exist; recording by hand is
 * the fallback for cash and for accounts nothing reads.
 */
@Composable
fun OwnTransferSheet(
    transaction: TransactionEntity,
    candidates: List<OwnTransferSide>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (OwnTransferChoice) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var selected by remember(transaction.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var recording by remember(transaction.id) { mutableStateOf(candidates.isEmpty()) }
    // The far side lands on some other account of the owner's; offering this one would let a
    // movement leave and arrive in the same place.
    val targets = accounts.filter { it.id != transaction.accountId && !it.isArchived }
    var accountId by remember(transaction.id) { mutableStateOf(targets.firstOrNull()?.id) }
    var amount by remember(transaction.id) { mutableStateOf("") }
    var day by remember(transaction.id) {
        mutableStateOf(Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate().toString())
    }
    val date = runCatching { LocalDate.parse(day) }.getOrNull()
    val amountMinor = amount.replace(',', '.').toDoubleOrNull()
        ?.let { Math.round(it * 100) }?.takeIf { it > 0L }
    val currency = targets.firstOrNull { it.id == accountId }?.currency

    WhfinFormSheet(
        title = stringResource(R.string.own_transfer_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.own_transfer_link),
        primaryEnabled = if (recording) {
            accountId != null && amountMinor != null && date != null && currency != null
        } else {
            selected.isNotEmpty()
        },
        onPrimary = {
            if (recording) {
                onConfirm(
                    OwnTransferChoice.Recorded(
                        accountId = requireNotNull(accountId),
                        amountMinor = requireNotNull(amountMinor),
                        currency = requireNotNull(currency),
                        occurredAt = requireNotNull(date).atStartOfDay(zone).toInstant().toEpochMilli(),
                    ),
                )
            } else {
                onConfirm(OwnTransferChoice.Existing(candidates.filter { it.transaction.id in selected }))
            }
        },
    ) {
        Text(stringResource(R.string.own_transfer_body), style = MaterialTheme.typography.bodyMedium)

        if (candidates.isNotEmpty()) {
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.own_transfer_mode_existing),
                        selected = !recording,
                        onClick = { recording = false },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.own_transfer_mode_record),
                        selected = recording,
                        onClick = { recording = true },
                    )
                }
            }
        }

        if (!recording) {
            // More than one may be picked: a single withdrawal often comes back as several credits,
            // and pairing them one by one would leave the rest looking like new money.
            WhfinSectionLabel(stringResource(R.string.own_transfer_pick))
            Text(
                stringResource(R.string.own_transfer_pick_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                candidates.forEachIndexed { index, side ->
                    WhfinLedgerRow(
                        title = formatMinor(side.transaction.amountMinor, side.transaction.currency),
                        supportingText = listOfNotNull(
                            side.account?.name,
                            side.transaction.rawCounterparty?.takeIf(String::isNotBlank),
                            Instant.ofEpochMilli(side.transaction.occurredAt).atZone(zone).toLocalDate().toString(),
                            stringResource(R.string.own_transfer_selected)
                                .takeIf { side.transaction.id in selected },
                        ).joinToString(" · "),
                        onClick = {
                            selected = if (side.transaction.id in selected) {
                                selected - side.transaction.id
                            } else {
                                selected + side.transaction.id
                            }
                        },
                        divider = index != candidates.lastIndex,
                    )
                }
            }
        } else {
            // With nothing to offer, say so: an empty form with no explanation reads as the app
            // having decided this movement has no other side.
            if (candidates.isEmpty()) Text(
                stringResource(R.string.own_transfer_none),
                style = MaterialTheme.typography.bodySmall,
            )
            WhfinFieldLabel(stringResource(R.string.own_transfer_account))
            WhfinChoiceRail {
                items(targets.size) { index ->
                    val account = targets[index]
                    WhfinFilterPill(
                        label = "${account.name} · ${account.currency}",
                        selected = account.id == accountId,
                        onClick = { accountId = account.id },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WhfinField(
                    value = amount,
                    onValueChange = { entry ->
                        amount = entry.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = currency?.let { stringResource(R.string.own_transfer_amount_in, it) }
                        ?: stringResource(R.string.own_transfer_amount),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                WhfinField(
                    value = day,
                    onValueChange = { day = it },
                    label = stringResource(R.string.own_transfer_date),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.own_transfer_record_effect),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(stringResource(R.string.own_transfer_effect), style = MaterialTheme.typography.bodySmall)
    }
}
