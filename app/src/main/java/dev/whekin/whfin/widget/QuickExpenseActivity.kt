package dev.whekin.whfin.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.categorization.CategorySuggester
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinAmountKeypad
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinIconButton

class QuickExpenseActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CURRENCY = "currency"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_SOURCE_LABEL = "source_label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            WhfinTheme {
                val uiPreferences = remember { UiPreferences(applicationContext) }
                val quickExpenseKeypadEnabled by uiPreferences.quickExpenseKeypadEnabled
                    .collectAsState(initial = true)
                // Категории и статистика грузятся асинхронно и не задерживают
                // первый кадр с фокусом суммы.
                val suggestions by produceState<Pair<List<CategoryEntity>, CategorySuggester>?>(null) {
                    val db = (application as WhfinApp).userDb
                    value = withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        val samples = db.transactionDao()
                            .observeCategorySamples(now - CategorySuggester.LOOKBACK_MILLIS)
                            .firstOrNull()
                            .orEmpty()
                        val suggester = CategorySuggester(samples, now)
                        val expense = db.categoryDao().all()
                            .filter { it.kind == CategoryKind.EXPENSE && !it.isSystem }
                        suggester.rankCategories(expense) to suggester
                    }
                }
                QuickExpenseScreen(
                    initialCurrency = intent.getStringExtra(EXTRA_CURRENCY) ?: "GEL",
                    sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL)
                        ?: getString(R.string.widget_source_cash),
                    sourceAccountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L).takeIf { it > 0 },
                    categories = suggestions?.first.orEmpty(),
                    suggester = suggestions?.second,
                    quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
                    onDismiss = ::finish,
                    onSave = ::save,
                )
            }
        }
    }

    private fun save(
        amountMinor: Long,
        currency: String,
        accountId: Long?,
        description: String?,
        categoryId: Long?,
    ) {
        lifecycleScope.launch {
            val db = (application as WhfinApp).userDb
            withContext(Dispatchers.IO) {
                val requested = accountId?.let { db.accountDao().byId(it) }?.takeIf { it.currency == currency }
                val account = requested ?: db.accountDao().allActive().firstOrNull { it.type == AccountType.CASH && it.currency == currency }
                    ?: db.accountDao().insert(AccountEntity(
                        name = if (currency == "GEL") "Cash" else "Cash $currency",
                        type = AccountType.CASH, currency = currency, sortOrder = 1000,
                    )).let { db.accountDao().byId(it)!! }
                db.transactionDao().insert(TransactionEntity(
                    accountId = account.id,
                    amountMinor = -kotlin.math.abs(amountMinor),
                    currency = currency,
                    occurredAt = System.currentTimeMillis(),
                    note = description,
                    categoryId = categoryId,
                    status = TxStatus.MANUAL,
                    source = TxSource.MANUAL,
                    createdAt = System.currentTimeMillis(),
                ))
            }
            Toast.makeText(this@QuickExpenseActivity, R.string.quick_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun QuickExpenseScreen(
    initialCurrency: String,
    sourceLabel: String,
    sourceAccountId: Long?,
    categories: List<CategoryEntity>,
    suggester: CategorySuggester?,
    quickExpenseKeypadEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long?, String?, Long?) -> Unit,
) {
    val currency = initialCurrency
    var calculator by remember { mutableStateOf(AmountCalculator()) }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    val minor = parseToMinor(calculator.resolvedText())?.takeIf { it > 0L }
    val imeVisible = WindowInsets.isImeVisible
    val amountFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountContentDescription = stringResource(R.string.tx_amount)

    LaunchedEffect(quickExpenseKeypadEnabled) {
        if (!quickExpenseKeypadEnabled) amountFocusRequester.requestFocus()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hiddenSheetOffset = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    var expandedSheetOffset by remember(hiddenSheetOffset) {
        mutableFloatStateOf(hiddenSheetOffset)
    }
    var currentSheetOffset by remember(hiddenSheetOffset) {
        mutableFloatStateOf(hiddenSheetOffset)
    }
    LaunchedEffect(sheetState, hiddenSheetOffset) {
        snapshotFlow { runCatching { sheetState.requireOffset() }.getOrNull() }
            .filterNotNull()
            .collect { offset ->
                currentSheetOffset = offset
                expandedSheetOffset = minOf(expandedSheetOffset, offset)
            }
    }
    val scrimProgress = sheetScrimProgress(
        offset = currentSheetOffset,
        expandedOffset = expandedSheetOffset,
        hiddenOffset = hiddenSheetOffset,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * scrimProgress),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.testTag("quick-expense-drag-handle"),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(Icons.Default.Payments, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.quick_expense), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "$sourceLabel · $currency",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                WhfinIconButton(Icons.Default.Close, stringResource(R.string.action_cancel), onDismiss, outlined = false)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = if (quickExpenseKeypadEnabled) {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "${calculator.expression.orEmpty()} ${calculator.display.ifEmpty { "0.00" }} $currency"
                    }
                } else Modifier,
            ) {
                if (quickExpenseKeypadEnabled) {
                    Column(Modifier.weight(1f)) {
                        calculator.expression?.let { expression ->
                            Text(
                                expression,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            calculator.display.ifEmpty { "0.00" },
                            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (calculator.error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                } else {
                    BasicTextField(
                        value = calculator.display,
                        onValueChange = { calculator = AmountCalculator(input = normalizeAmountInput(it)) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(amountFocusRequester)
                            .semantics { contentDescription = amountContentDescription }
                            .testTag("quick-expense-system-amount"),
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFeatureSettings = "tnum",
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        singleLine = true,
                        decorationBox = { input ->
                            if (calculator.display.isEmpty()) {
                                Text(
                                    "0.00",
                                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else input()
                        },
                    )
                }
                Text(currency, style = MaterialTheme.typography.titleLarge)
            }
            if (categories.isNotEmpty()) {
                // Ряд живёт вместе с суммой: введённая сумма пере-ранжирует подсказки.
                // Выбор фиксирует порядок на момент тапа, чтобы кружок не прыгал под пальцем.
                var lockedOrder by remember { mutableStateOf<List<CategoryEntity>?>(null) }
                val displayed = lockedOrder ?: remember(categories, suggester, minor) {
                    if (suggester == null || minor == null) categories
                    else suggester.rankCategories(categories, -minor, currency)
                }
                QuickCategoryRow(
                    categories = displayed,
                    selectedId = categoryId,
                    onSelect = { selected ->
                        if (categoryId == selected.id) {
                            categoryId = null
                            lockedOrder = null
                        } else {
                            categoryId = selected.id
                            lockedOrder = displayed
                        }
                    },
                )
            }
            WhfinField(
                value = description,
                onValueChange = { description = it.take(80) },
                label = stringResource(R.string.quick_description),
                modifier = Modifier.fillMaxWidth(),
            )
            if (quickExpenseKeypadEnabled && !imeVisible) {
                WhfinAmountKeypad(
                    deleteContentDescription = stringResource(R.string.quick_delete_digit),
                    onKey = { calculator = calculator.press(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            WhfinButton(
                label = stringResource(R.string.action_save),
                onClick = {
                    minor?.let {
                        onSave(it, currency, sourceAccountId, description.trim().takeIf(String::isNotEmpty), categoryId)
                    }
                },
                enabled = minor != null,
                modifier = Modifier.fillMaxWidth().testTag("quick-expense-save"),
            )
        }
    }
}

internal fun sheetScrimProgress(
    offset: Float,
    expandedOffset: Float,
    hiddenOffset: Float,
): Float {
    val travel = hiddenOffset - expandedOffset
    if (!travel.isFinite() || travel <= 0f) return 0f
    return ((hiddenOffset - offset) / travel).coerceIn(0f, 1f)
}

@Preview(name = "quick_expense_light", widthDp = 360, heightDp = 780)
@Preview(
    name = "quick_expense_dark",
    widthDp = 360,
    heightDp = 780,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "quick_expense_font_150", widthDp = 360, heightDp = 780, fontScale = 1.5f)
@Preview(name = "quick_expense_compact", widthDp = 360, heightDp = 600)
@Composable
private fun QuickExpensePreview() {
    WhfinTheme {
        QuickExpenseScreen(
            initialCurrency = "GEL",
            sourceLabel = "Cash",
            sourceAccountId = null,
            categories = listOf(
                CategoryEntity(
                    id = 1,
                    name = "Groceries",
                    kind = CategoryKind.EXPENSE,
                    icon = "ShoppingCart",
                    color = 0xFF6F8D60.toInt(),
                ),
                CategoryEntity(
                    id = 2,
                    name = "Eating out",
                    kind = CategoryKind.EXPENSE,
                    icon = "Restaurant",
                    color = 0xFFC16B4D.toInt(),
                ),
                CategoryEntity(
                    id = 3,
                    name = "Transport",
                    kind = CategoryKind.EXPENSE,
                    icon = "DirectionsBus",
                    color = 0xFFB17D3F.toInt(),
                ),
            ),
            suggester = null,
            onDismiss = {},
            onSave = { _, _, _, _, _ -> },
        )
    }
}

@Preview(name = "quick_expense_system_keyboard", widthDp = 360, heightDp = 780)
@Composable
private fun QuickExpenseSystemKeyboardPreview() {
    WhfinTheme {
        QuickExpenseScreen(
            initialCurrency = "GEL",
            sourceLabel = "Cash",
            sourceAccountId = null,
            categories = emptyList(),
            suggester = null,
            quickExpenseKeypadEnabled = false,
            onDismiss = {},
            onSave = { _, _, _, _, _ -> },
        )
    }
}

/** Ряд круглых icon-first категорий: опциональный, повторный тап снимает выбор. */
@Composable
private fun QuickCategoryRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (CategoryEntity) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // LazyRow якорит скролл за ключом первого видимого элемента: при пере-ранжировке
    // новый лидер уезжает влево за viewport. Возвращаем ленту к началу.
    LaunchedEffect(categories) { listState.scrollToItem(0) }
    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(categories.size, key = { categories[it].id }) { index ->
            val category = categories[index]
            val selected = selectedId == category.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(64.dp),
            ) {
                Surface(
                    onClick = { onSelect(category) },
                    shape = CircleShape,
                    color = if (selected) Color(category.color).copy(alpha = .22f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, Color(category.color))
                    } else null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        CategoryIcons.resolve(category.icon), category.name,
                        tint = Color(category.color),
                        modifier = Modifier.padding(11.dp).size(20.dp),
                    )
                }
                Text(
                    category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
