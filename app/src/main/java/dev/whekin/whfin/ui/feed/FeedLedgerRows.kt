package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ExpandMore
import dev.whekin.whfin.ui.counterpartyLabel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.sms.needsRoutingDecision
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.data.db.TxSource

@Composable
internal fun DayHeader(
    day: LocalDate,
    expensesByCurrency: Map<String, Long>,
    gelFromConversions: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // День недели полезен: операции вспоминают как «в субботу», а не по номеру дня. Год печатаем
    // только для прошлых лет, иначе он занимает место в каждом заголовке текущего года.
    val label = when (day) {
        LocalDate.now() -> stringResource(R.string.date_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> day.format(
            DateTimeFormatter.ofPattern(
                if (day.year == LocalDate.now().year) "EEE, d MMM" else "EEE, d MMM yyyy",
            ),
        )
    }
    val directGel = expensesByCurrency["GEL"] ?: 0L
    val totalGel = directGel + gelFromConversions
    val hasBreakdown = expensesByCurrency.keys.any { it != "GEL" } || gelFromConversions > 0L
    // День без расходов (только переводы/доход) не должен печатать бессмысленный "0.00 ₾".
    // Если в GEL нечего показать, ведём итогом единственную иностранную валюту дня.
    val foreignTotals = expensesByCurrency.filterKeys { it != "GEL" }
    val totalCurrency = when {
        totalGel > 0L -> "GEL"
        foreignTotals.size == 1 -> foreignTotals.keys.first()
        else -> null
    }
    val totalText = when {
        totalGel > 0L -> formatMinor(totalGel, "GEL")
        foreignTotals.size == 1 -> foreignTotals.entries.first()
            .let { (currency, total) -> formatMinor(total, currency) }
        else -> null
    }
    val showBreakdown = hasBreakdown && (totalText != null || foreignTotals.size > 1)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                Text(label.uppercase(), Modifier.padding(start = 9.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (totalText != null || showBreakdown) Row(
                Modifier.then(if (showBreakdown) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onToggle) else Modifier)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (totalText != null) WhfinAmount(totalText,
                    symbol = totalCurrency?.let(::currencySymbol),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                if (showBreakdown) Icon(Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded && showBreakdown) Text(
            buildList {
                if (directGel > 0L) add(formatMinor(directGel, "GEL"))
                if (gelFromConversions > 0L) add("FX ${formatMinor(gelFromConversions, "GEL")}")
                expensesByCurrency.entries.filter { it.key != "GEL" }.sortedBy { it.key }
                    .forEach { (currency, total) -> add(formatMinor(total, currency)) }
            }.joinToString("  ·  "),
            modifier = Modifier.align(Alignment.End).padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** БАНКОВСКИЙ КАПС -> Title Case: "OPENAI *CHATGPT SUBSCR" читается тяжело. */
private fun humanizeTitle(raw: String): String {
    val letters = raw.filter(Char::isLetter)
    if (letters.isEmpty() || letters != letters.uppercase()) return raw
    return raw.lowercase().split(" ").joinToString(" ") { word ->
        // Первая БУКВА слова, а не первый символ ("*chatgpt" -> "*Chatgpt")
        val index = word.indexOfFirst(Char::isLetter)
        if (index < 0) word
        else word.take(index) + word[index].titlecase() + word.drop(index + 1)
    }
}

@Composable
internal fun UnroutedOperationRow(
    operation: UnroutedOperation,
    onClick: () -> Unit,
) {
    val diagnostic = operation.diagnostic
    val grouped = diagnostic.kind == SmsDiagnosticKind.OWN_TRANSFER ||
        diagnostic.kind == SmsDiagnosticKind.CURRENCY_EXCHANGE
    val title = diagnostic.counterparty?.let { humanizeTitle(counterpartyLabel(it)) } ?: stringResource(
        when (diagnostic.kind) {
            SmsDiagnosticKind.CARD_PAYMENT -> R.string.sms_kind_card
            SmsDiagnosticKind.OUTGOING_TRANSFER -> R.string.sms_kind_outgoing
            SmsDiagnosticKind.INCOMING_TRANSFER -> R.string.sms_kind_incoming
            SmsDiagnosticKind.DEPOSIT_TOP_UP -> R.string.sms_kind_deposit_top_up
            SmsDiagnosticKind.BILL_PAYMENT -> R.string.sms_kind_bill
            SmsDiagnosticKind.CASH_DEPOSIT -> R.string.sms_kind_cash_deposit
            SmsDiagnosticKind.INTEREST -> R.string.sms_kind_interest
            SmsDiagnosticKind.OWN_TRANSFER -> R.string.sms_kind_own_transfer
            SmsDiagnosticKind.CURRENCY_EXCHANGE -> R.string.sms_kind_exchange
            SmsDiagnosticKind.IGNORED, SmsDiagnosticKind.UNRECOGNIZED -> R.string.feed_unrouted_operation
        },
    )
    val bankName = dev.whekin.whfin.data.sms.BankSmsBank.fromKey(diagnostic.externalKey).provider
    val cardHint = diagnostic.cardLast4?.let {
        stringResource(R.string.sms_card_suffix, it)
    }
    val awaitingBankMatch = diagnostic.reason == SmsDiagnosticReason.STATEMENT_COVERS_PERIOD
    val statusColor = if (awaitingBankMatch) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.tertiary
    val routingLabel = stringResource(when {
        awaitingBankMatch -> R.string.feed_unrouted_awaiting_bank_match
        grouped -> R.string.feed_unrouted_choose_accounts
        else -> R.string.feed_unrouted_choose_account
    })
    val currency = diagnostic.currency ?: diagnostic.balanceCurrency ?: "—"
    val amount = diagnostic.amountMinor ?: 0L
    val signedAmount = when (diagnostic.kind) {
        SmsDiagnosticKind.CARD_PAYMENT,
        SmsDiagnosticKind.OUTGOING_TRANSFER -> -kotlin.math.abs(amount)
        SmsDiagnosticKind.INCOMING_TRANSFER,
        SmsDiagnosticKind.DEPOSIT_TOP_UP -> kotlin.math.abs(amount)
        else -> kotlin.math.abs(amount)
    }
    val withSign = diagnostic.kind != SmsDiagnosticKind.OWN_TRANSFER &&
        diagnostic.kind != SmsDiagnosticKind.CURRENCY_EXCHANGE

    Surface(
        onClick = onClick,
        enabled = diagnostic.needsRoutingDecision(),
        modifier = Modifier.fillMaxWidth().testTag("unrouted-operation-${diagnostic.id}"),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = .11f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (grouped) Icons.Default.SwapHoriz else Icons.Default.Sms,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f),
                    )
                    Text(
                        listOfNotNull(bankName, stringResource(R.string.feed_bank_sms_source), cardHint)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    WhfinAmount(
                        text = formatMinor(signedAmount, currency, withSign = withSign),
                        symbol = currencySymbol(currency),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (
                        diagnostic.secondaryAmountMinor != null &&
                        diagnostic.secondaryCurrency != null
                    ) {
                        WhfinAmount(
                            text = "→ ${formatMinor(
                                diagnostic.secondaryAmountMinor,
                                diagnostic.secondaryCurrency,
                            )}",
                            symbol = currencySymbol(diagnostic.secondaryCurrency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier.size(6.dp).background(
                                statusColor,
                                CircleShape,
                            ),
                        )
                        Text(
                            routingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
        }
    }
}

@Composable
internal fun FeedRow(
    item: FeedItem,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val tx = item.tx
    val isTransfer = tx.isTransfer || tx.transferGroupId != null
    val title = item.transferSummary
        ?: (if (isTransfer) stringResource(R.string.feed_own_transfer) else null)
        ?: (item.merchant?.displayName ?: tx.rawCounterparty)?.let { humanizeTitle(counterpartyLabel(it)) }
        ?: tx.note?.let(::humanizeTitle)
        ?: item.category?.let { category ->
            if (category.isSystem && category.name == dev.whekin.whfin.data.db.CategorySeeder.UNACCOUNTED) {
                stringResource(R.string.category_unaccounted)
            } else {
                category.name
            }
        }
        ?: stringResource(R.string.feed_no_description)
    val categoryName = when {
        tx.isTransfer || tx.transferGroupId != null -> stringResource(R.string.feed_transfer)
        item.category != null ->
            if (item.category.isSystem && item.category.name == dev.whekin.whfin.data.db.CategorySeeder.UNACCOUNTED) {
                stringResource(R.string.category_unaccounted)
            } else {
                item.category.name
            }
        else -> stringResource(R.string.feed_uncategorized)
    }
    // Источник: кеш/название счёта, для карточного счёта — маска карты
    val sourceHint = item.cardHint ?: item.account?.name
    val splitHint = item.splitOnPeople.firstOrNull()?.let { (name, _) ->
        if (item.splitOnPeople.size > 1) "$name +${item.splitOnPeople.size - 1}" else name
    }
    val subtitle = listOfNotNull(categoryName, sourceHint, splitHint).joinToString(" · ")
    val amountColor = when {
        tx.isTransfer || tx.transferGroupId != null -> MaterialTheme.colorScheme.onSurfaceVariant
        tx.amountMinor > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("feed-row-${item.tx.id}").combinedClickable(
            onClick = onClick,
            onLongClickLabel = stringResource(R.string.transactions_select_action),
            onLongClick = onLongClick,
        ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Маркер категории — круг с тихой заливкой, как в статистике и формах. Обведённый
            // квадрат делал иконку самым тяжёлым элементом строки и спорил с ledger-сеткой.
            Surface(shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else
                    item.category?.let { Color(it.color).copy(alpha = .14f) } ?: MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else
                            CategoryIcons.resolve(item.category?.icon, isTransfer = tx.isTransfer),
                        contentDescription = if (selected) stringResource(R.string.transactions_selected) else null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else
                            item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (item.transferSummary != null || LocalDensity.current.fontScale >= 1.3f) 2 else 1,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (item.isDebt) Text(
                    stringResource(R.string.debt_person_owes, item.debtPersonName ?: "—", formatMinor(item.debtMinor ?: 0L, tx.currency)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                WhfinAmount(
                    formatMinor(
                        if (isTransfer) kotlin.math.abs(tx.amountMinor) else tx.amountMinor,
                        tx.currency,
                        withSign = !isTransfer,
                    ),
                    symbol = currencySymbol(tx.currency),
                    color = amountColor,
                )
                if (isTransfer && item.destinationAmountMinor != null && item.destinationCurrency != null &&
                    item.destinationCurrency != tx.currency) {
                    WhfinAmount(
                        "→ ${formatMinor(item.destinationAmountMinor, item.destinationCurrency)}",
                        symbol = currencySymbol(item.destinationCurrency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Оплата через авто-конвертацию: реальная цена в исходной валюте
                if (item.fundedByConversionMinor != null && item.fundedByConversionCurrency != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            "≈ ${formatMinor(item.fundedByConversionMinor, item.fundedByConversionCurrency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tx.source == TxSource.SMS || tx.status == TxStatus.PENDING) {
                    // SMS provenance waits for a statement even after an owner reviews a legacy
                    // pending row. Opening the receipt is the path to the separate review action.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                        Text(stringResource(if (tx.source == TxSource.BANK_HOLD) R.string.bank_hold_status else if (tx.source == TxSource.SMS) R.string.status_waiting_statement else R.string.status_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
        )
    }
    }
}
