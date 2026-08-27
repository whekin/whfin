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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
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
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.core.ui.rememberWhfinBackGesture
import dev.whekin.whfin.core.ui.whfinPredictiveBack
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame

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

/**
 * Счёт, с которого форма начинает. Ручная операция почти всегда про наличные, поэтому порядок
 * `sortOrder, id` из базы использовать нельзя: первым там может оказаться любой счёт, в том числе
 * watch-only кошелёк, чей баланс всё равно приходит из блокчейна, а не из записанной операции.
 */
internal fun defaultManualAccount(accounts: List<AccountEntity>): AccountEntity? {
    fun pick(predicate: (AccountEntity) -> Boolean): AccountEntity? =
        accounts.firstOrNull { predicate(it) && it.currency == "GEL" }
            ?: accounts.firstOrNull(predicate)
    return pick { it.type == AccountType.CASH }
        ?: pick { it.type == AccountType.BANK }
        ?: pick { it.type == AccountType.SAVINGS }
        ?: accounts.firstOrNull { it.type != AccountType.CRYPTO }
        ?: accounts.firstOrNull()
}

/**
 * Ранжирование категорий для формы. По умолчанию — порядок, полученный от вызывающего
 * (`categoriesByUsage`); экран может подставить `CategorySuggester`, чтобы учитывать введённую
 * сумму и валюту так же, как quick-entry из виджета.
 */
typealias CategoryRanker = (List<CategoryEntity>, Long?, String?) -> List<CategoryEntity>

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
    rankCategories: CategoryRanker = { list, _, _ -> list },
) {
    val sources = remember(accounts) { accountSources(accounts) }
    val initial = remember(accounts) { defaultManualAccount(accounts) }
    val editingKind = when {
        editing?.tx?.isTransfer == true || editing?.tx?.transferGroupId != null -> ManualKind.TRANSFER
        (editing?.tx?.amountMinor ?: -1) >= 0 -> ManualKind.INCOME
        else -> ManualKind.EXPENSE
    }
    val initialKind = if (editing != null) editingKind else ManualKind.EXPENSE
    var kind by remember(editing?.tx?.id) { mutableStateOf(initialKind) }
    var amountText by remember(editing?.tx?.id) { mutableStateOf(editing?.tx?.amountMinor?.let(::minorInput).orEmpty()) }
    var accountId by remember(accounts, editing?.tx?.id) {
        mutableStateOf(editing?.tx?.accountId ?: initial?.id)
    }
    var destinationId by remember(editing?.tx?.id) {
        mutableStateOf(editing?.destinationAccountId)
    }
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
        day != LocalDate.now() || kind != initialKind
    val requestClose = { if (dirty) confirmDiscard = true else onDismiss() }
    val requestDialogDismiss = {
        if (showAllCategories) showAllCategories = false else requestClose()
    }

    fun save() {
        if (!valid) return
        val savedAmountMinor = amountMinor
        if (kind == ManualKind.DEBT) {
            val time = if (day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
            onSaveDebt(NewDebt(
                personId = debtPersonId, personName = debtPersonName.takeIf { debtPersonId == null },
                direction = debtDirection, amountMinor = savedAmountMinor, currency = if (debtHasMovement) account?.currency ?: debtCurrency else debtCurrency,
                accountId = account?.id?.takeIf { debtHasMovement },
                occurredAt = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note = note.trim().takeIf(String::isNotEmpty),
            ))
            return
        }
        // Only a transfer has a receiving side. An expense that still carried a destination amount
        // described money arriving somewhere it never went, and the ledger rightly refused it.
        val transfer = kind == ManualKind.TRANSFER
        val result = ManualTransaction(
            accountId = account!!.id,
            destinationAccountId = destinationId.takeIf { transfer },
            destinationAmountMinor = when {
                !transfer -> null
                conversion -> destinationMinor
                else -> savedAmountMinor
            },
            amountMinor = if (kind == ManualKind.INCOME) savedAmountMinor else -savedAmountMinor,
            categoryId = categoryId.takeIf { kind != ManualKind.TRANSFER },
            note = note.trim().takeIf(String::isNotEmpty), day = day,
        )
        if (editing != null) onUpdate(editing, result) else onSave(result)
    }

    Dialog(
        onDismissRequest = requestDialogDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        WhfinDialogSystemBars()
        // The composer answers the Back pull like every other page: it insets under the finger and
        // comes back if the finger lifts early. A dirty form still asks before discarding — the
        // gesture only decides when to ask, never what the answer is.
        val backGesture = rememberWhfinBackGesture(enabled = true) { requestDialogDismiss() }
        DemoWorkspaceFrame {
            Surface(
                Modifier.fillMaxSize().whfinPredictiveBack(backGesture),
                color = MaterialTheme.colorScheme.background,
            ) {
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
                val kindFade = WhfinMotion.quick<Float>()
                AnimatedContent(kind, modifier = Modifier.weight(1f), transitionSpec = { fadeIn(kindFade) togetherWith fadeOut(kindFade) }, label = "composer-kind") { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (current == ManualKind.DEBT && !debtHasMovement) DebtAmountEditor(amountText, { amountText = it }, debtCurrency) { debtCurrency = it }
                        else AmountEditor(
                            value = amountText,
                            onValue = { amountText = it },
                            account = account,
                            autoFocus = shouldAutoFocusAmount,
                            onAutoFocused = { shouldAutoFocusAmount = false },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        when (current) {
                            ManualKind.EXPENSE -> ExpenseLayout(
                                categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem }, categoryId, amountMinor,
                                account?.currency, rankCategories,
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
    }

    if (showTypeMenu) ModalBottomSheet(
        onDismissRequest = { showTypeMenu = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
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
    if (confirmDiscard) WhfinConfirmDialog(
        title = stringResource(R.string.discard_transaction_title),
        body = stringResource(R.string.discard_transaction_body),
        confirmLabel = stringResource(R.string.discard_action),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
        onDismiss = { confirmDiscard = false },
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
        WhfinChoiceRail {
            items(listOf("GEL", "USD", "EUR"), key = { it }) { item ->
                WhfinFilterPill(
                    label = item,
                    selected = currency == item,
                    onClick = { onCurrency(item) },
                )
            }
        }
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
    WhfinChoiceRail {
        item {
            WhfinFilterPill(
                label = stringResource(R.string.debt_they_owe),
                selected = direction == DebtDirection.THEY_OWE_ME,
                onClick = { onDirection(DebtDirection.THEY_OWE_ME) },
            )
        }
        item {
            WhfinFilterPill(
                label = stringResource(R.string.debt_i_owe),
                selected = direction == DebtDirection.I_OWE_THEM,
                onClick = { onDirection(DebtDirection.I_OWE_THEM) },
            )
        }
    }
    SectionLabel(stringResource(R.string.debt_person))
    WhfinChoiceRail {
        items(people, key = { it.id }) { person ->
            WhfinFilterPill(
                label = person.name,
                selected = personId == person.id,
                onClick = { onPerson(person.id) },
            )
        }
    }
    WhfinField(
        personName,
        onPersonName,
        stringResource(R.string.debt_new_person),
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel(stringResource(R.string.debt_money_movement))
    WhfinChoiceRail {
        item {
            WhfinFilterPill(
                label = stringResource(R.string.debt_through_account),
                selected = hasMovement,
                onClick = { onMovement(true) },
            )
        }
        item {
            WhfinFilterPill(
                label = stringResource(R.string.debt_no_movement),
                selected = !hasMovement,
                onClick = { onMovement(false) },
            )
        }
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

/**
 * Валюта здесь только показывается. Раньше рядом с суммой стоял второй «переключатель», который
 * молча прокручивал ledger’ы того же источника — при живом выборе источник→валюта ниже это давало
 * два разных способа поменять одно и то же и незаметную подмену счёта.
 */
@Composable private fun AmountEditor(
    value: String,
    onValue: (String) -> Unit,
    account: AccountEntity?,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
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
            placeholder = {
                Text(
                    "0.00",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f),
                )
            },
            textStyle = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
            modifier = Modifier.weight(1f).focusRequester(focusRequester))
        Text(
            account?.currency.orEmpty(),
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable private fun ExpenseLayout(categories: List<CategoryEntity>, selected: Long?, amount: Long?,
    currency: String?, rankCategories: CategoryRanker,
    sources: List<AccountSource>, accountId: Long?, day: LocalDate, note: String,
    onCategory: (Long) -> Unit, onMore: () -> Unit, onAccount: (Long) -> Unit, onCreateCashCurrency: (String) -> Unit,
    onDate: () -> Unit, onNote: (String) -> Unit) {
    // Один механизм выбора: выбранная категория и подсказки живут в одном ряду, «Ещё» открывает
    // полный список. Прежде над этим рядом стояла ещё и отдельная строка «Выбрать категорию».
    val chosen = categories.firstOrNull { it.id == selected }
    val suggestions = remember(categories, chosen?.id, amount, currency) {
        val ranked = rankCategories(categories, amount, currency)
        (listOfNotNull(chosen) + ranked.filterNot { it.id == chosen?.id }).take(3)
    }
    SectionLabel(stringResource(R.string.tx_detail_category))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { item ->
            CategoryTile(item, Modifier.weight(1f), selected = item.id == selected) { onCategory(item.id) }
        }
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
    if (conversion) WhfinField(
        received,
        { onDestinationAmount(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12)) },
        stringResource(R.string.tx_amount_received),
        suffix = destination?.currency.orEmpty(),
        keyboardType = KeyboardType.Decimal,
        modifier = Modifier.fillMaxWidth(),
    )
    DateTile(day, Modifier.fillMaxWidth(), onDate)
    NoteField(note, onNote)
}

/**
 * Внутри формы подписи полей тихие: капс с трекингом остаётся ярлыком книги (день, раздел экрана).
 * Раньше `КАТЕГОРИЯ`, `НАПРАВЛЕНИЕ`, `ЧЕЛОВЕК`, `ДВИЖЕНИЕ ДЕНЕГ` капсом шли подряд и телеграфировали.
 */
@Composable private fun SectionLabel(text: String) = WhfinFieldLabel(text)

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

@Composable private fun CategoryTile(item: CategoryEntity, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    // Two lines, because a quarter of the screen width cuts "Subscriptions" mid-word and the
    // suggestion then asks the user to recognise a category by its first eight letters.
    Column(modifier.clip(MaterialTheme.shapes.medium).heightIn(min = 78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color(item.color).copy(alpha = if (selected) .24f else .14f),
            border = if (selected) BorderStroke(1.5.dp, Color(item.color)) else null,
        ) {
            Icon(CategoryIcons.resolve(item.icon), null, tint = Color(item.color), modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable private fun MoreTile(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).heightIn(min = 78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Text(
            stringResource(R.string.categories_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    if (open) ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
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
                    val currencies = if (isCash) {
                        (listOf("GEL", "USD", "EUR") + available.map { it.currency }).distinct()
                    } else {
                        available.map { it.currency }
                    }
                    WhfinChoiceRail {
                        items(currencies, key = { it }) { currency ->
                            val ledger = available.firstOrNull { it.currency == currency }
                            WhfinFilterPill(
                                label = currency,
                                selected = ledger?.id == selectedId,
                                onClick = {
                                    if (ledger != null) onSelect(ledger.id) else onCreateCashCurrency(currency)
                                    open = false
                                },
                            )
                        }
                    }
                    if (isCash) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhfinField(
                            customCurrency,
                            { customCurrency = it.filter(Char::isLetter).uppercase().take(8) },
                            stringResource(R.string.currency_add),
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
    WhfinField(
        value = value,
        onValueChange = onValue,
        label = null,
        placeholder = stringResource(R.string.tx_note),
        leadingIcon = Icons.AutoMirrored.Filled.Notes,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).onFocusChanged {
            if (it.isFocused) scope.launch { delay(280); bringIntoView.bringIntoView() }
        },
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
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(stringResource(R.string.category_choose), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (kind == CategoryKind.EXPENSE) R.string.categories_expense else R.string.categories_income),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Создание — компактное действие заголовка: раньше внизу висела широкая кнопка,
            // из-за которой сетка из десяти категорий разъезжалась половиной пустого экрана.
            if (!creating && categories.isNotEmpty()) WhfinIconButton(
                Icons.Default.Add,
                stringResource(R.string.category_new),
                { creating = true },
                outlined = false,
            )
        }
        if (creating) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.category_new), style = MaterialTheme.typography.headlineMedium)
                WhfinField(
                    name,
                    { name = it.take(32) },
                    stringResource(R.string.category_name),
                    modifier = Modifier.fillMaxWidth(),
                )
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
            CategoryGrid(categories, selected, onSelect,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp))
            if (categories.isEmpty()) WhfinButton(
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
                    AmountEditor("23.60", {}, account)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ExpenseLayout(
                        categories, 1, 2_360, "GEL", { list, _, _ -> list },
                        listOf(source), 1, LocalDate.now(), "", {}, {}, {}, {}, {}, {},
                    )
                }
                WhfinButton("Save", {}, Modifier.fillMaxWidth().padding(20.dp))
            }
        }
    }
}
