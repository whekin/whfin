package dev.whekin.whfin.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.components.CategoryGrid
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.ui.parseToMinor
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.whekin.whfin.data.debt.NewDebt
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.ui.theme.WhfinTheme

data class ManualTransaction(
    val accountId: Long,
    val destinationAccountId: Long? = null,
    val destinationAmountMinor: Long? = null,
    val amountMinor: Long,
    val categoryId: Long?,
    val note: String?,
    val day: LocalDate,
)

private enum class ManualKind(val label: Int, val title: Int) {
    EXPENSE(R.string.tx_expense, R.string.new_expense),
    INCOME(R.string.tx_income, R.string.new_income),
    TRANSFER(R.string.tx_transfer, R.string.new_transfer),
    DEBT(R.string.debt_label, R.string.new_debt),
}

private data class AccountSource(val key: String, val label: String, val accounts: List<AccountEntity>)

private fun accountSources(accounts: List<AccountEntity>) = accounts.groupBy { account ->
    when {
        account.type == AccountType.CASH -> "cash"
        account.groupId != null -> "group:${account.groupId}:${account.type}"
        else -> "account:${account.id}"
    }
}.map { (key, values) ->
    val first = values.first()
    val clean = first.name.replace(Regex("\\s+${Regex.escape(first.currency)}(?:\\s*[·•].*)?$", RegexOption.IGNORE_CASE), "")
        .ifBlank { first.name }
    AccountSource(
        key,
        if (first.type == AccountType.BANK && first.iban != null) "$clean •${first.iban.takeLast(4)}" else clean,
        values.sortedWith(compareBy<AccountEntity> { if (it.currency == "GEL") 0 else 1 }.thenBy { it.currency }),
    )
}

private fun suggestedCategories(categories: List<CategoryEntity>, amount: Long?): List<CategoryEntity> {
    val large = setOf("Home", "Devices", "LocalShipping", "Savings")
    val small = setOf("ShoppingCart", "DirectionsBus", "Restaurant", "DeliveryDining")
    return categories.withIndex().sortedByDescending { (index, item) ->
        var score = 1000 - index * 10
        if (amount != null && amount in 1..3_000 && item.icon in large) score -= 600
        if ((amount ?: 0) >= 30_000 && item.icon in large) score += 450
        if ((amount ?: 0) >= 30_000 && item.icon in small) score -= 280
        score
    }.take(3).map { it.value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    people: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSave: (ManualTransaction) -> Unit,
    onSaveDebt: (NewDebt) -> Unit,
    editing: FeedItem? = null,
    onUpdate: (FeedItem, ManualTransaction) -> Unit = { _, _ -> },
    onCreateCategory: (String, CategoryKind, String, Int) -> Unit = { _, _, _, _ -> },
    onCreateCashCurrency: (String) -> Unit = {},
) {
    val sources = remember(accounts) { accountSources(accounts) }
    val initial = sources.firstOrNull()?.accounts?.firstOrNull { it.currency == "GEL" }
        ?: sources.firstOrNull()?.accounts?.firstOrNull()
    val editingKind = when {
        editing?.tx?.isTransfer == true || editing?.tx?.transferGroupId != null -> ManualKind.TRANSFER
        (editing?.tx?.amountMinor ?: -1) >= 0 -> ManualKind.INCOME
        else -> ManualKind.EXPENSE
    }
    var kind by remember(editing?.tx?.id) { mutableStateOf(if (editing == null) ManualKind.EXPENSE else editingKind) }
    var amountText by remember(editing?.tx?.id) { mutableStateOf(editing?.tx?.amountMinor?.let(::minorInput).orEmpty()) }
    var accountId by remember(accounts, editing?.tx?.id) { mutableStateOf(editing?.tx?.accountId ?: initial?.id) }
    var destinationId by remember(editing?.tx?.id) { mutableStateOf(editing?.destinationAccountId) }
    var destinationAmount by remember(editing?.tx?.id) { mutableStateOf(editing?.destinationAmountMinor?.let(::minorInput).orEmpty()) }
    var categoryId by remember(editing?.tx?.id) { mutableStateOf(editing?.tx?.categoryId) }
    var note by remember(editing?.tx?.id) { mutableStateOf(editing?.tx?.note.orEmpty()) }
    var day by remember(editing?.tx?.id) { mutableStateOf(editing?.day ?: LocalDate.now()) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showAllCategories by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var debtDirection by remember { mutableStateOf(DebtDirection.THEY_OWE_ME) }
    var debtPersonId by remember(people) { mutableStateOf(people.firstOrNull()?.id) }
    var debtPersonName by remember { mutableStateOf("") }
    var debtHasMovement by remember(sources) { mutableStateOf(sources.isNotEmpty()) }
    var debtCurrency by remember { mutableStateOf(initial?.currency ?: "GEL") }
    var requestedCashCurrency by remember { mutableStateOf<String?>(null) }
    var shouldAutoFocusAmount by remember(editing?.tx?.id) { mutableStateOf(editing == null) }

    LaunchedEffect(accounts, requestedCashCurrency) {
        val currency = requestedCashCurrency ?: return@LaunchedEffect
        accounts.firstOrNull { it.type == AccountType.CASH && it.currency == currency }?.let {
            accountId = it.id
            requestedCashCurrency = null
        }
    }
    val createCashCurrency: (String) -> Unit = { raw ->
        val currency = raw.trim().uppercase().take(8)
        if (currency.isNotEmpty()) {
            requestedCashCurrency = currency
            onCreateCashCurrency(currency)
        }
    }

    val account = accounts.firstOrNull { it.id == accountId }
    val destination = accounts.firstOrNull { it.id == destinationId }
    val amountMinor = parseToMinor(amountText)
    val destinationMinor = parseToMinor(destinationAmount)
    val conversion = kind == ManualKind.TRANSFER && destination != null && destination.currency != account?.currency
    val valid = amountMinor != null && (kind == ManualKind.DEBT || account != null) &&
        (kind != ManualKind.TRANSFER || destination != null) && (!conversion || destinationMinor != null)
        && (kind != ManualKind.DEBT || debtPersonId != null || debtPersonName.isNotBlank())
    val dirty = amountText.isNotBlank() || destinationAmount.isNotBlank() || categoryId != null || note.isNotBlank() ||
        day != LocalDate.now() || kind != ManualKind.EXPENSE
    val requestClose = { if (dirty) confirmDiscard = true else onDismiss() }

    fun save() {
        if (!valid) return
        if (kind == ManualKind.DEBT) {
            val time = if (day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
            onSaveDebt(NewDebt(
                personId = debtPersonId, personName = debtPersonName.takeIf { debtPersonId == null },
                direction = debtDirection, amountMinor = amountMinor!!, currency = if (debtHasMovement) account?.currency ?: debtCurrency else debtCurrency,
                accountId = account?.id?.takeIf { debtHasMovement },
                occurredAt = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note = note.trim().takeIf(String::isNotEmpty),
            ))
            return
        }
        val result = ManualTransaction(
            accountId = account!!.id,
            destinationAccountId = destinationId,
            destinationAmountMinor = if (conversion) destinationMinor else amountMinor,
            amountMinor = if (kind == ManualKind.INCOME) amountMinor!! else -amountMinor!!,
            categoryId = categoryId.takeIf { kind != ManualKind.TRANSFER },
            note = note.trim().takeIf(String::isNotEmpty), day = day,
        )
        if (editing != null) onUpdate(editing, result) else onSave(result)
    }

    Dialog(onDismissRequest = requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WhfinDialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showAllCategories) {
                CategorySelectorScreen(
                    categories = categories.filter {
                        it.kind == (if (kind == ManualKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE) && !it.isSystem
                    },
                    selected = categoryId,
                    onBack = { showAllCategories = false },
                    onSelect = { categoryId = it.id; showAllCategories = false },
                    kind = if (kind == ManualKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                    onCreate = onCreateCategory,
                )
            } else Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            ) {
                ComposerHeader(kind, requestClose, { if (editing == null) showTypeMenu = true }, editing != null)
                AnimatedContent(kind, modifier = Modifier.weight(1f), transitionSpec = { fadeIn(WhfinMotion.quick()) togetherWith fadeOut(WhfinMotion.quick()) }, label = "composer-kind") { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (current == ManualKind.DEBT && !debtHasMovement) DebtAmountEditor(amountText, { amountText = it }, debtCurrency) { debtCurrency = it }
                        else AmountEditor(
                            value = amountText,
                            onValue = { amountText = it },
                            account = account,
                            source = sources.firstOrNull { source -> source.accounts.any { it.id == accountId } },
                            autoFocus = shouldAutoFocusAmount,
                            onAutoFocused = { shouldAutoFocusAmount = false },
                            onCurrency = { accountId = it.id },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        when (current) {
                            ManualKind.EXPENSE -> ExpenseLayout(
                                categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem }, categoryId, amountMinor,
                                sources, accountId, day, note,
                                onCategory = { categoryId = it }, onMore = { showAllCategories = true },
                                onAccount = { accountId = it }, onCreateCashCurrency = createCashCurrency,
                                onDate = { showDatePicker = true }, onNote = { note = it },
                            )
                            ManualKind.INCOME -> IncomeLayout(
                                categories.filter { it.kind == CategoryKind.INCOME && !it.isSystem }, categoryId,
                                sources, accountId, day, note,
                                onCategory = { categoryId = it }, onAccount = { accountId = it }, onCreateCashCurrency = createCashCurrency,
                                onDate = { showDatePicker = true }, onNote = { note = it },
                            )
                            ManualKind.TRANSFER -> TransferLayout(
                                sources, accountId, destinationId, destinationAmount, destination,
                                day, note, conversion,
                                onFrom = { accountId = it; if (destinationId == it) destinationId = null },
                                onTo = { destinationId = it }, onDestinationAmount = { destinationAmount = it },
                                onCreateCashCurrency = createCashCurrency,
                                onSwap = { val old = accountId; accountId = destinationId; destinationId = old },
                                onDate = { showDatePicker = true }, onNote = { note = it },
                            )
                            ManualKind.DEBT -> DebtLayout(
                                people, debtDirection, debtPersonId, debtPersonName, debtHasMovement,
                                sources, accountId, day, note,
                                onDirection = { debtDirection = it }, onPerson = { debtPersonId = it; debtPersonName = "" },
                                onPersonName = { debtPersonName = it; if (it.isNotBlank()) debtPersonId = null },
                                onMovement = { debtHasMovement = it && sources.isNotEmpty() }, onAccount = { accountId = it },
                                onCreateCashCurrency = createCashCurrency,
                                onDate = { showDatePicker = true }, onNote = { note = it },
                            )
                        }
                    }
                }
                Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.background) {
                    WhfinButton(
                        label = stringResource(if (editing == null) R.string.action_save else R.string.action_save_changes),
                        onClick = ::save,
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (showTypeMenu) ModalBottomSheet(onDismissRequest = { showTypeMenu = false }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.choose_transaction_type), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp))
            ManualKind.entries.forEach { value ->
                val icon = when (value) { ManualKind.EXPENSE -> Icons.Default.ArrowUpward; ManualKind.INCOME -> Icons.Default.ArrowDownward; ManualKind.TRANSFER -> Icons.Default.SwapHoriz; ManualKind.DEBT -> Icons.Default.Handshake }
                SelectorRow(stringResource(value.label), icon, MaterialTheme.colorScheme.primary, {
                    kind = value; categoryId = null; showTypeMenu = false
                }, kind == value)
            }
        }
    }
    if (showDatePicker) DateSelector(day, { showDatePicker = false }) { day = it; showDatePicker = false }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_transaction_title)) },
        text = { Text(stringResource(R.string.discard_transaction_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard_action), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun minorInput(value: Long): String {
    val absolute = kotlin.math.abs(value)
    return if (absolute % 100L == 0L) (absolute / 100L).toString()
    else "%d.%02d".format(absolute / 100L, absolute % 100L)
}

@Composable private fun DebtAmountEditor(value: String, onValue: (String) -> Unit, currency: String, onCurrency: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value, { onValue(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12)) },
                placeholder = { Text("0.00", style = MaterialTheme.typography.displayLarge) },
                textStyle = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent), modifier = Modifier.weight(1f))
            Text(currency, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("GEL", "USD", "EUR").forEach { item -> FilterChip(currency == item, { onCurrency(item) }, { Text(item) }) } }
    }
}

@Composable private fun DebtLayout(
    people: List<PersonEntity>, direction: DebtDirection, personId: Long?, personName: String,
    hasMovement: Boolean, sources: List<AccountSource>, accountId: Long?, day: LocalDate, note: String,
    onDirection: (DebtDirection) -> Unit, onPerson: (Long) -> Unit, onPersonName: (String) -> Unit,
    onMovement: (Boolean) -> Unit, onAccount: (Long) -> Unit, onCreateCashCurrency: (String) -> Unit,
    onDate: () -> Unit, onNote: (String) -> Unit,
) {
    SectionLabel(stringResource(R.string.debt_direction))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(direction == DebtDirection.THEY_OWE_ME, { onDirection(DebtDirection.THEY_OWE_ME) }, { Text(stringResource(R.string.debt_they_owe)) })
        FilterChip(direction == DebtDirection.I_OWE_THEM, { onDirection(DebtDirection.I_OWE_THEM) }, { Text(stringResource(R.string.debt_i_owe)) })
    }
    SectionLabel(stringResource(R.string.debt_person))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(people) { person -> FilterChip(personId == person.id, { onPerson(person.id) }, { Text(person.name) }) }
    }
    OutlinedTextField(personName, onPersonName, label = { Text(stringResource(R.string.debt_new_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    SectionLabel(stringResource(R.string.debt_money_movement))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(hasMovement, { onMovement(true) }, { Text(stringResource(R.string.debt_through_account)) })
        FilterChip(!hasMovement, { onMovement(false) }, { Text(stringResource(R.string.debt_no_movement)) })
    }
    if (hasMovement) CompactAccountSelector(stringResource(R.string.tx_account), sources, accountId, Modifier.fillMaxWidth(), onAccount, onCreateCashCurrency = onCreateCashCurrency)
    DateTile(day, Modifier.fillMaxWidth(), onDate)
    NoteField(note, onNote)
}

@Composable private fun ComposerHeader(kind: ManualKind, onClose: () -> Unit, onType: () -> Unit, editing: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        WhfinIconButton(Icons.Default.Close, stringResource(R.string.action_cancel), onClose, Modifier.align(Alignment.CenterStart), outlined = false)
        Row(Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = !editing, onClick = onType).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (editing) R.string.transaction_edit else kind.title), style = MaterialTheme.typography.titleLarge)
            if (!editing) Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable private fun AmountEditor(
    value: String,
    onValue: (String) -> Unit,
    account: AccountEntity?,
    source: AccountSource?,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
    onCurrency: (AccountEntity) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
            onAutoFocused()
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, { onValue(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12)) },
            placeholder = { Text("0.00", style = MaterialTheme.typography.displayLarge) },
            textStyle = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
            modifier = Modifier.weight(1f).focusRequester(focusRequester))
        Row(
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = (source?.accounts?.size ?: 0) > 1) {
                val list = source!!.accounts
                val index = list.indexOfFirst { it.id == account?.id }.coerceAtLeast(0)
                onCurrency(list[(index + 1) % list.size])
            }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(account?.currency.orEmpty(), style = MaterialTheme.typography.titleMedium)
            if ((source?.accounts?.size ?: 0) > 1) Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
        }
    }
}

@Composable private fun ExpenseLayout(categories: List<CategoryEntity>, selected: Long?, amount: Long?, sources: List<AccountSource>, accountId: Long?, day: LocalDate, note: String,
    onCategory: (Long) -> Unit, onMore: () -> Unit, onAccount: (Long) -> Unit, onCreateCashCurrency: (String) -> Unit,
    onDate: () -> Unit, onNote: (String) -> Unit) {
    val chosen = categories.firstOrNull { it.id == selected }
    SectionLabel(stringResource(R.string.tx_detail_category))
    SelectorRow(chosen?.name ?: stringResource(R.string.category_choose), chosen?.let { CategoryIcons.resolve(it.icon) } ?: Icons.Default.Category,
        chosen?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onMore)
    SectionLabel(stringResource(R.string.recent_categories))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestedCategories(categories, amount).forEach { item -> CategoryTile(item, Modifier.weight(1f)) { onCategory(item.id) } }
        MoreTile(Modifier.weight(1f), onMore)
    }
    Column(Modifier.fillMaxWidth()) {
        CompactAccountSelector(stringResource(R.string.tx_account), sources, accountId, Modifier.fillMaxWidth(), onAccount, onCreateCashCurrency = onCreateCashCurrency)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DateTile(day, Modifier.fillMaxWidth(), onDate)
    }
    NoteField(note, onNote)
}

@Composable private fun IncomeLayout(categories: List<CategoryEntity>, selected: Long?, sources: List<AccountSource>, accountId: Long?, day: LocalDate, note: String,
    onCategory: (Long) -> Unit, onAccount: (Long) -> Unit, onCreateCashCurrency: (String) -> Unit, onDate: () -> Unit, onNote: (String) -> Unit) {
    SectionLabel(stringResource(R.string.income_source))
    CategoryGrid(categories, selected, { onCategory(it.id) }, maxHeight = 210.dp)
    Column(Modifier.fillMaxWidth()) {
        CompactAccountSelector(stringResource(R.string.to_account), sources, accountId, Modifier.fillMaxWidth(), onAccount, onCreateCashCurrency = onCreateCashCurrency)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DateTile(day, Modifier.fillMaxWidth(), onDate)
    }
    NoteField(note, onNote)
}

@Composable private fun TransferLayout(sources: List<AccountSource>, from: Long?, to: Long?, received: String, destination: AccountEntity?, day: LocalDate, note: String, conversion: Boolean,
    onFrom: (Long) -> Unit, onTo: (Long) -> Unit, onDestinationAmount: (String) -> Unit, onSwap: () -> Unit,
    onCreateCashCurrency: (String) -> Unit, onDate: () -> Unit, onNote: (String) -> Unit) {
    CompactAccountSelector(stringResource(R.string.from_account), sources, from, Modifier.fillMaxWidth(), onFrom, onCreateCashCurrency = onCreateCashCurrency)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(onClick = onSwap, enabled = to != null) { Icon(Icons.Default.SwapVert, null) }
    }
    CompactAccountSelector(stringResource(R.string.to_account), sources, to, Modifier.fillMaxWidth(), onTo, exclude = from, onCreateCashCurrency = onCreateCashCurrency)
    if (conversion) OutlinedTextField(received, { onDestinationAmount(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12)) },
        label = { Text(stringResource(R.string.tx_amount_received)) }, suffix = { Text(destination?.currency.orEmpty()) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
    DateTile(day, Modifier.fillMaxWidth(), onDate)
    NoteField(note, onNote)
}

@Composable private fun SectionLabel(text: String) = WhfinSectionLabel(text)

@Composable private fun SelectorRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, selected: Boolean = false) {
    WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = selected) {
        WhfinLedgerRow(
            title = label,
            icon = icon,
            iconTint = tint,
            trailing = { Icon(if (selected) Icons.Default.Check else Icons.Default.ChevronRight, null) },
            onClick = onClick,
        )
    }
}

@Composable private fun CategoryTile(item: CategoryEntity, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(item.color).copy(alpha = .14f)) {
            Icon(CategoryIcons.resolve(item.icon), null, tint = Color(item.color), modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun MoreTile(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Text(stringResource(R.string.categories_more), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CompactAccountSelector(
    label: String,
    sources: List<AccountSource>,
    selectedId: Long?,
    modifier: Modifier,
    onSelect: (Long) -> Unit,
    exclude: Long? = null,
    onCreateCashCurrency: (String) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var customCurrency by remember { mutableStateOf("") }
    val account = sources.flatMap { it.accounts }.firstOrNull { it.id == selectedId }
    val source = sources.firstOrNull { it.accounts.any { account -> account.id == selectedId } }
    Row(
        modifier.clickable { open = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(accountIcon(account?.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(source?.label ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(account?.currency ?: "—", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
        }
        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.money_source_choose), style = MaterialTheme.typography.headlineSmall)
            sources.forEach { item ->
                val available = item.accounts.filter { it.id != exclude }.distinctBy { it.currency }
                val isCash = item.key == "cash"
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(accountIcon(item.accounts.firstOrNull()?.type), null, tint = MaterialTheme.colorScheme.primary)
                        Text(item.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currencies = if (isCash) (listOf("GEL", "USD", "EUR") + available.map { it.currency }).distinct()
                            else available.map { it.currency }
                        currencies.forEach { currency ->
                            val ledger = available.firstOrNull { it.currency == currency }
                            FilterChip(
                                selected = ledger?.id == selectedId,
                                onClick = {
                                    if (ledger != null) onSelect(ledger.id) else onCreateCashCurrency(currency)
                                    open = false
                                },
                                label = { Text(currency) },
                            )
                        }
                    }
                    if (isCash) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            customCurrency, { customCurrency = it.filter(Char::isLetter).uppercase().take(8) },
                            label = { Text(stringResource(R.string.currency_add)) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledIconButton(
                            onClick = { onCreateCashCurrency(customCurrency); open = false },
                            enabled = customCurrency.length >= 3,
                        ) { Icon(Icons.Default.Add, stringResource(R.string.currency_add)) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun accountIcon(type: AccountType?) = when (type) { AccountType.BANK, AccountType.SAVINGS -> Icons.Default.AccountBalance; AccountType.CRYPTO -> Icons.Default.CurrencyBitcoin; else -> Icons.Default.AccountBalanceWallet }

@Composable private fun DateTile(day: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tx_detail_date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (day == LocalDate.now()) stringResource(R.string.date_today) else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable private fun NoteField(value: String, onValue: (String) -> Unit) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value, onValue,
        placeholder = { Text(stringResource(R.string.tx_note)) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).onFocusChanged {
            if (it.isFocused) scope.launch { delay(280); bringIntoView.bringIntoView() }
        },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable private fun CategorySelectorScreen(
    categories: List<CategoryEntity>, selected: Long?, onBack: () -> Unit,
    onSelect: (CategoryEntity) -> Unit, kind: CategoryKind,
    onCreate: (String, CategoryKind, String, Int) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(if (kind == CategoryKind.EXPENSE) "VolunteerActivism" else "Work") }
    var color by remember { mutableIntStateOf(if (kind == CategoryKind.EXPENSE) 0xFFD16D5A.toInt() else 0xFF78906F.toInt()) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WhfinBackButton(stringResource(R.string.action_back), onBack)
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.category_choose), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (kind == CategoryKind.EXPENSE) R.string.categories_expense else R.string.categories_income),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (creating) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.category_new), style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(name, { name = it.take(32) }, label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(color).copy(alpha = .16f)) {
                        Icon(CategoryIcons.resolve(icon), null, tint = Color(color), modifier = Modifier.padding(14.dp))
                    }
                    Text(stringResource(if (kind == CategoryKind.EXPENSE) R.string.categories_expense else R.string.categories_income))
                }
                CategoryAppearancePicker(icon, color, { icon = it }, { color = it })
                Spacer(Modifier.weight(1f))
                WhfinButton(
                    stringResource(R.string.category_create),
                    { onCreate(name.trim(), kind, icon, color); creating = false },
                    Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank(),
                )
            }
        } else {
            CategoryGrid(categories, selected, onSelect, maxHeight = 560.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
            Spacer(Modifier.weight(1f))
            WhfinButton(
                stringResource(R.string.category_new),
                { creating = true },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.Default.Add,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateSelector(day: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text(stringResource(R.string.action_done)) } }) { DatePicker(state) }
}

@Preview(name = "Composer expense", widthDp = 400, heightDp = 900, showBackground = true)
@Preview(name = "Composer dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Composer font 1.5", widthDp = 400, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
private fun ComposerContentPreview() {
    val account = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, currency = "GEL")
    val source = AccountSource("cash", "Cash", listOf(account))
    val categories = listOf(
        CategoryEntity(1, "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0xFF4C956C.toInt()),
        CategoryEntity(2, "Eating out", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0xFFD16D5A.toInt()),
        CategoryEntity(3, "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0xFFE0A246.toInt()),
    )
    WhfinTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                ComposerHeader(ManualKind.EXPENSE, {}, {}, false)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AmountEditor("23.60", {}, account, source, onCurrency = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ExpenseLayout(categories, 1, 2_360, listOf(source), 1, LocalDate.now(), "", {}, {}, {}, {}, {}, {})
                }
                WhfinButton("Save", {}, Modifier.fillMaxWidth().padding(20.dp))
            }
        }
    }
}
