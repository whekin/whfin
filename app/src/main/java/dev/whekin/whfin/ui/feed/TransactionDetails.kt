package dev.whekin.whfin.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.ui.text.input.KeyboardType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.ui.counterpartyLabel
import dev.whekin.whfin.ui.parseToMinor
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.whekin.whfin.core.ui.WhfinActionMenu
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.theme.WhfinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsSheet(
    item: FeedItem,
    onDismiss: () -> Unit,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCorrect: (() -> Unit)? = null,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    onOwnTransfer: (() -> Unit)? = null,
    onClearOwnTransfer: (() -> Unit)? = null,
    categoryAcknowledgement: Long? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        TransactionDetailsContent(
            item = item,
            modifier = Modifier.navigationBarsPadding(),
            onChangeCategory = onChangeCategory,
            onDelete = onDelete,
            onCorrect = onCorrect,
            onEdit = onEdit,
            onDebt = onDebt,
            onClearDebt = onClearDebt,
            onSplit = onSplit,
            onClearSplit = onClearSplit,
            onChangeStatus = onChangeStatus,
            onConfirm = onConfirm,
            onOwnTransfer = onOwnTransfer,
            onClearOwnTransfer = onClearOwnTransfer,
            categoryAcknowledgement = categoryAcknowledgement,
        )
    }
}

@Composable
private fun TransactionDetailsContent(
    item: FeedItem,
    modifier: Modifier = Modifier,
    onChangeCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCorrect: (() -> Unit)? = null,
    onEdit: (() -> Unit)?,
    onDebt: (() -> Unit)?,
    onClearDebt: (() -> Unit)?,
    onSplit: (() -> Unit)? = null,
    onClearSplit: (() -> Unit)? = null,
    onChangeStatus: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    onOwnTransfer: (() -> Unit)? = null,
    onClearOwnTransfer: (() -> Unit)? = null,
    categoryAcknowledgement: Long? = null,
) {
    val tx = item.tx
    val presentationAmount = transactionPresentationAmount(tx)
    val isTransfer = tx.isTransfer || tx.transferGroupId != null
    var showBankDetails by remember(tx.id) { mutableStateOf(false) }
    var actionMenuExpanded by remember(tx.id) { mutableStateOf(false) }
    val genericTitle = stringResource(
        when {
            isTransfer -> R.string.tx_transfer
            tx.amountMinor >= 0 -> R.string.tx_income
            else -> R.string.tx_expense
        },
    )
    val title = item.transferSummary
        ?: item.merchant?.displayName?.let { counterpartyLabel(it) }
        ?: tx.rawCounterparty?.let { counterpartyLabel(it) }
        ?: tx.note?.takeIf { it.isNotBlank() }
        ?: item.category?.name
        ?: genericTitle
    val dateAndAccount = listOfNotNull(
        item.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
        item.account?.name,
    ).joinToString(" · ")
    val accent = item.category?.let { Color(it.color) } ?: when {
        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        tx.amountMinor >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val hasBankDetails = item.account?.iban != null || tx.source != dev.whekin.whfin.data.db.TxSource.MANUAL ||
        tx.rawCounterparty != null || tx.counterpartyIban != null || tx.note != null ||
        tx.origAmountMinor != null || item.fundedByConversionMinor != null
    // Подтверждение pending-черновика — самое частое решение в этой раскладке, поэтому он
    // стоит первым и единственным залитым действием, а не спрятан за отдельным status-листом.
    val confirmPending = onConfirm?.takeIf { tx.status == TxStatus.PENDING && tx.source != TxSource.BANK_HOLD }
    // Correcting an imported row lives in the overflow beside Delete, where the rare answers are,
    // and stays out of the rail: it carries the longest label in the sheet and was already listed
    // in both places, so on a real phone it pushed the everyday answers past the right edge.
    val hasQuickActions = confirmPending != null || onEdit != null || onDebt != null ||
        onClearDebt != null || onSplit != null || onClearSplit != null ||
        onOwnTransfer != null || onClearOwnTransfer != null

    LazyColumn(
        modifier.fillMaxWidth().heightIn(max = 680.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "transaction-heading") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = .14f),
                    contentColor = accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            CategoryIcons.resolve(item.category?.icon, isTransfer = isTransfer),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                    WhfinAmount(
                        formatMinor(presentationAmount.minor, presentationAmount.currency),
                        symbol = currencySymbol(presentationAmount.currency),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (item.destinationAmountMinor != null && item.destinationCurrency != null) {
                        WhfinAmount(
                            "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                            symbol = currencySymbol(item.destinationCurrency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        dateAndAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null || onCorrect != null) {
                    Box {
                        WhfinIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.transaction_actions),
                            onClick = { actionMenuExpanded = true },
                            outlined = false,
                        )
                        WhfinActionMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            onDelete?.let { delete -> DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.transaction_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    actionMenuExpanded = false
                                    delete()
                                },
                            ) }
                            onCorrect?.let { correct -> DropdownMenuItem(
                                text = { Text(stringResource(R.string.transaction_correct)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    actionMenuExpanded = false
                                    correct()
                                },
                            ) }
                        }
                    }
                }
            }
        }
        if (!isTransfer) item(key = "transaction-category") {
            if (categoryAcknowledgement != null) androidx.compose.runtime.key(categoryAcknowledgement) {
                dev.whekin.whfin.core.ui.WhfinAcknowledgement(
                    CategoryIcons.resolve(item.category?.icon), stringResource(R.string.category_remembered_feedback),
                    Modifier.padding(bottom = 10.dp),
                )
            }
            WhfinLedgerGroup {
                dev.whekin.whfin.core.ui.WhfinLedgerRow(
                    title = item.category?.name ?: stringResource(R.string.category_assign_hint),
                    icon = CategoryIcons.resolve(item.category?.icon),
                    iconTint = item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
                    onClick = onChangeCategory,
                    trailing = if (onChangeCategory != null) {{ Icon(Icons.Default.Edit, null) }} else null,
                    modifier = Modifier.testTag("transaction-category"),
                )
            }
        }
        item(key = "transaction-summary") {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Bank evidence stays unverified until a statement replaces it. A legacy pending
                // SMS may also be marked reviewed below, but that does not verify the bank posting.
                DetailEditableRow(
                    label = stringResource(R.string.tx_detail_status),
                    value = if (tx.source == TxSource.BANK_HOLD) stringResource(R.string.bank_hold_status) else if (tx.source == TxSource.SMS) {
                        stringResource(R.string.status_waiting_statement)
                    } else {
                        tx.status.label()
                    },
                    onClick = onChangeStatus.takeIf { tx.source != TxSource.BANK_HOLD && tx.source != TxSource.SMS },
                )
                if (item.isDebt) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        stringResource(R.string.debt_label),
                        stringResource(
                            R.string.debt_person_owes,
                            item.debtPersonName ?: "—",
                            formatMinor(item.debtMinor ?: 0L, tx.currency),
                        ),
                    )
                }
                item.splitOnPeople.forEach { (name, amount) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        stringResource(R.string.split_on_person, name),
                        formatMinor(amount, tx.currency),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (tx.note?.isNotBlank() == true && tx.note != title) {
            item(key = "transaction-note") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WhfinSectionLabel(stringResource(R.string.tx_note))
                    Text(tx.note, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (hasQuickActions) item(key = "transaction-actions") {
            // The rail used to scroll sideways, so whether an answer existed depended on whether the
            // reader thought to drag it: at ordinary phone density the fourth action sat past the
            // right edge, and the longest label pushed it there. Every answer is visible now — two
            // per row, wrapping — and the one action that is primary when it applies leads on its
            // own line. Rare repairs and deletion stay in the overflow beside the heading.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmPending != null) DetailQuickAction(
                    Icons.Default.CheckCircle,
                    stringResource(if (tx.source == TxSource.SMS) R.string.transaction_mark_reviewed else R.string.transaction_confirm),
                    confirmPending,
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val secondary = buildList<Triple<ImageVector, String, () -> Unit>> {
                    if (onEdit != null) add(
                        Triple(Icons.Default.Edit, stringResource(R.string.action_edit), onEdit),
                    )
                    if (onClearOwnTransfer != null) add(
                        Triple(
                            Icons.Default.SwapHoriz,
                            stringResource(R.string.own_transfer_unlink),
                            onClearOwnTransfer,
                        ),
                    ) else if (onOwnTransfer != null) add(
                        Triple(
                            Icons.Default.SwapHoriz,
                            stringResource(R.string.own_transfer_action),
                            onOwnTransfer,
                        ),
                    )
                    if (onClearDebt != null) add(
                        Triple(Icons.Default.PersonAdd, stringResource(R.string.debt_clear), onClearDebt),
                    ) else if (onDebt != null) add(
                        Triple(Icons.Default.PersonAdd, stringResource(R.string.debt_action_short), onDebt),
                    )
                    if (onClearSplit != null) add(
                        Triple(
                            Icons.AutoMirrored.Filled.CallSplit,
                            stringResource(R.string.split_clear),
                            onClearSplit,
                        ),
                    ) else if (onSplit != null) add(
                        Triple(
                            Icons.AutoMirrored.Filled.CallSplit,
                            stringResource(R.string.split_action_short),
                            onSplit,
                        ),
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    secondary.forEach { (icon, label, action) ->
                        DetailQuickAction(icon, label, action, modifier = Modifier.weight(1f))
                    }
                    // An odd count would stretch the last cell across the row and make it read as a
                    // heavier action than its neighbours.
                    if (secondary.size % 2 == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (hasBankDetails) item(key = "transaction-bank-details-toggle") {
            TextButton(onClick = { showBankDetails = !showBankDetails }) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = if (showBankDetails) 180f else 0f),
                )
                Text(stringResource(R.string.tx_detail_more))
            }
        }
        if (hasBankDetails && showBankDetails) item(key = "transaction-bank-details") {
            WhfinLedgerGroup {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    item.account?.iban?.let { DetailRow("IBAN", it) }
                    DetailRow(stringResource(R.string.tx_detail_source), tx.source.name.lowercase().replaceFirstChar(Char::titlecase))
                    tx.rawCounterparty?.let { DetailRow(stringResource(R.string.tx_detail_counterparty), it) }
                    tx.counterpartyIban?.let { DetailRow(stringResource(R.string.tx_detail_counterparty_iban), it) }
                    tx.note?.let { DetailRow(stringResource(R.string.tx_detail_bank_description), it) }
                    if (tx.origAmountMinor != null && tx.origCurrency != null) DetailRow(
                        stringResource(R.string.tx_detail_original_amount),
                        formatMinor(kotlin.math.abs(tx.origAmountMinor), tx.origCurrency),
                    )
                    if (item.fundedByConversionMinor != null && item.fundedByConversionCurrency != null) DetailRow(
                        stringResource(R.string.tx_detail_converted_from),
                        formatMinor(item.fundedByConversionMinor, item.fundedByConversionCurrency),
                    )
                }
            }
        }

    }
}

@Preview(name = "Transaction details", widthDp = 400, heightDp = 620, showBackground = true)
@Preview(
    name = "Transaction details dark",
    widthDp = 400,
    heightDp = 620,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Transaction details font 1.5",
    widthDp = 400,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(name = "Transaction details compact", widthDp = 400, heightDp = 480, showBackground = true)
@Composable
private fun TransactionDetailsPreview() {
    val account = AccountEntity(
        id = 1,
        name = "Cash",
        type = AccountType.CASH,
        currency = "GEL",
    )
    val category = CategoryEntity(
        id = 1,
        name = "Eating out",
        kind = CategoryKind.EXPENSE,
        icon = "Restaurant",
        color = 0xFFC45D3A.toInt(),
    )
    val item = FeedItem(
        tx = TransactionEntity(
            id = 1,
            accountId = account.id,
            amountMinor = -2_000,
            currency = "GEL",
            occurredAt = System.currentTimeMillis(),
            categoryId = category.id,
            status = TxStatus.MANUAL,
            source = TxSource.MANUAL,
        ),
        merchant = null,
        category = category,
        account = account,
        cardHint = null,
        day = LocalDate.of(2026, 7, 19),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionDetailsContent(
                item = item,
                modifier = Modifier.fillMaxSize(),
                onChangeCategory = {},
                onDelete = {},
                onEdit = {},
                onDebt = {},
                onClearDebt = null,
                onSplit = {},
                onChangeStatus = {},
            )
        }
    }
}

@Preview(name = "Transaction details pending", widthDp = 400, heightDp = 620, showBackground = true)
@Preview(
    name = "Transaction details pending dark",
    widthDp = 400,
    heightDp = 620,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Transaction details pending font 1.5",
    widthDp = 400,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun TransactionDetailsPendingPreview() {
    val account = AccountEntity(
        id = 1,
        name = "Everyday",
        type = AccountType.BANK,
        currency = "GEL",
        iban = "GE00CD0000000000000001",
    )
    val category = CategoryEntity(
        id = 1,
        name = "Eating out",
        kind = CategoryKind.EXPENSE,
        icon = "Restaurant",
        color = 0xFFC45D3A.toInt(),
    )
    val item = FeedItem(
        tx = TransactionEntity(
            id = 2,
            accountId = account.id,
            amountMinor = -1_270,
            currency = "GEL",
            occurredAt = System.currentTimeMillis(),
            rawCounterparty = "COURTYARD COFFEE",
            categoryId = category.id,
            status = TxStatus.PENDING,
            source = TxSource.SMS,
        ),
        merchant = null,
        category = category,
        account = account,
        cardHint = "••0000",
        day = LocalDate.of(2026, 7, 19),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TransactionDetailsContent(
                item = item,
                modifier = Modifier.fillMaxSize(),
                onChangeCategory = {},
                onDelete = null,
                onEdit = null,
                onDebt = {},
                onClearDebt = null,
                onSplit = {},
                onChangeStatus = {},
                onConfirm = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionStatusSheet(
    current: TxStatus?,
    onDismiss: () -> Unit,
    onSelect: (TxStatus) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.transaction_status_title), style = MaterialTheme.typography.headlineSmall)
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                TxStatus.entries.forEachIndexed { index, status ->
                    WhfinLedgerRow(
                        title = status.label(),
                        supportingText = stringResource(status.descriptionResource()),
                        icon = Icons.Default.TaskAlt,
                        trailing = if (status == current) {{ Icon(Icons.Default.Check, null) }} else null,
                        onClick = { onSelect(status) },
                        divider = index != TxStatus.entries.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TxStatus.label(): String = stringResource(
    when (this) {
        TxStatus.PENDING -> R.string.status_pending
        TxStatus.CONFIRMED -> R.string.status_confirmed
        TxStatus.MANUAL -> R.string.status_manual
    },
)

private fun TxStatus.descriptionResource(): Int = when (this) {
    TxStatus.PENDING -> R.string.status_pending_description
    TxStatus.CONFIRMED -> R.string.status_confirmed_description
    TxStatus.MANUAL -> R.string.status_manual_description
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.58f))
    }
}

/**
 * Редактируемая строка сводки без chevron читалась как статичная запись базы, поэтому статус
 * и категорию не находили. Тихий chevron возвращает affordance, не превращая строку в кнопку.
 */
@Composable
private fun DetailEditableRow(label: String, value: String, onClick: (() -> Unit)?) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.58f))
        if (onClick != null) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = contentColor)
            // A label that has to fit half a row must be allowed to use two lines rather than
            // silently lose its tail; these are the words that say what the action does.
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtPersonSheet(
    item: FeedItem,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSelect: (PersonEntity) -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.debt_who_owes), style = MaterialTheme.typography.headlineSmall)
            Text(
                formatMinor(kotlin.math.abs(item.tx.amountMinor), item.tx.currency),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            people.forEach { person ->
                Surface(
                    onClick = { onSelect(person) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (person.name == item.debtPersonName) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(person.color).copy(alpha = .22f), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase()) }
                        }
                        Text(person.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            WhfinField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.debt_new_person),
                modifier = Modifier.fillMaxWidth(),
            )
            if (name.isNotBlank()) WhfinButton(
                stringResource(R.string.debt_add_and_select), { onAdd(name) }, Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class SplitMode { HALF, FULL, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitSheet(
    item: FeedItem,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onAddPerson: (String, (Long) -> Unit) -> Unit,
    onSave: (List<SplitShare>) -> Unit,
) {
    val total = kotlin.math.abs(item.tx.amountMinor)
    // Предзаполнение из существующей разбивки (одна персона — быстрый путь)
    val existing = item.splitOnPeople.firstOrNull()
    var selectedPersonId by remember {
        mutableStateOf(people.firstOrNull { it.name == existing?.first }?.id ?: people.firstOrNull()?.id)
    }
    var mode by remember {
        mutableStateOf(
            when (existing?.second) {
                null -> SplitMode.HALF
                total -> SplitMode.FULL
                total / 2 -> SplitMode.HALF
                else -> SplitMode.CUSTOM
            },
        )
    }
    var customText by remember {
        mutableStateOf(existing?.second?.let { (it / 100.0).toString() } ?: "")
    }
    var newName by remember { mutableStateOf("") }

    val onThemMinor = when (mode) {
        SplitMode.HALF -> total / 2
        SplitMode.FULL -> total
        SplitMode.CUSTOM -> parseToMinor(customText)?.coerceIn(0, total) ?: 0L
    }
    val purpose = if (mode == SplitMode.FULL) AllocationPurpose.GIFT else AllocationPurpose.SHARED
    val canSave = selectedPersonId != null && onThemMinor > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.split_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                formatMinor(total, item.tx.currency),
                style = MaterialTheme.typography.displaySmall,
            )

            Text(stringResource(R.string.split_with_whom), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinChoiceRail {
                items(people, key = { it.id }) { person ->
                    WhfinFilterPill(
                        label = person.name,
                        selected = selectedPersonId == person.id,
                        onClick = { selectedPersonId = person.id },
                    )
                }
            }
            WhfinField(
                value = newName,
                onValueChange = { newName = it },
                label = stringResource(R.string.debt_new_person),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (newName.isNotBlank()) TextButton(onClick = {
                        onAddPerson(newName.trim()) { id -> selectedPersonId = id; newName = "" }
                    }) { Text(stringResource(R.string.action_add)) }
                },
            )

            Text(stringResource(R.string.split_how_much), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_half),
                        selected = mode == SplitMode.HALF,
                        onClick = { mode = SplitMode.HALF },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_full),
                        selected = mode == SplitMode.FULL,
                        onClick = { mode = SplitMode.FULL },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.split_custom),
                        selected = mode == SplitMode.CUSTOM,
                        onClick = { mode = SplitMode.CUSTOM },
                    )
                }
            }
            if (mode == SplitMode.CUSTOM) {
                WhfinField(
                    value = customText,
                    onValueChange = { customText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                    label = stringResource(R.string.split_amount_on_them),
                    suffix = item.tx.currency,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Подсказка по итогу: на человека / на себя
            if (canSave) {
                val onMe = total - onThemMinor
                Text(
                    stringResource(
                        R.string.split_preview,
                        formatMinor(onThemMinor, item.tx.currency),
                        formatMinor(onMe, item.tx.currency),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            WhfinButton(
                stringResource(R.string.action_save),
                onClick = {
                    val id = selectedPersonId ?: return@WhfinButton
                    onSave(listOf(SplitShare(personId = id, amountMinor = onThemMinor, purpose = purpose)))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
        }
    }
}
