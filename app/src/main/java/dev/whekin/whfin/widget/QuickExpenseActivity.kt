package dev.whekin.whfin.widget

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.whekin.whfin.core.ui.WhfinButton
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
        window.setSoftInputMode(
            window.attributes.softInputMode or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            WhfinTheme {
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

@Composable
private fun QuickExpenseScreen(
    initialCurrency: String,
    sourceLabel: String,
    sourceAccountId: Long?,
    categories: List<CategoryEntity>,
    suggester: CategorySuggester?,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long?, String?, Long?) -> Unit,
) {
    val currency = initialCurrency
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    val minor = parseToMinor(amount)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Surface(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 18.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                        placeholder = { Text("0.00", style = MaterialTheme.typography.displayLarge) },
                        textStyle = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    Text(currency, style = MaterialTheme.typography.titleLarge)
                }
                if (categories.isNotEmpty()) {
                    // Ряд живёт вместе с суммой: введённая сумма пере-ранжирует подсказки,
                    // но пока категория выбрана — порядок заморожен, чтобы выбор не прыгал.
                    val ranked = remember(categories, suggester, minor, categoryId) {
                        if (categoryId != null || suggester == null || minor == null) categories
                        else suggester.rankCategories(categories, -minor, currency)
                    }
                    var frozen by remember { mutableStateOf(categories) }
                    if (categoryId == null) frozen = ranked
                    QuickCategoryRow(
                        categories = frozen,
                        selectedId = categoryId,
                        onSelect = { selected ->
                            categoryId = if (categoryId == selected.id) null else selected.id
                        },
                    )
                }
                WhfinField(
                    value = description,
                    onValueChange = { description = it.take(80) },
                    label = stringResource(R.string.quick_description),
                    modifier = Modifier.fillMaxWidth(),
                )
                WhfinButton(
                    label = stringResource(R.string.action_save),
                    onClick = {
                        minor?.let {
                            onSave(it, currency, sourceAccountId, description.trim().takeIf(String::isNotEmpty), categoryId)
                        }
                    },
                    enabled = minor != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Ряд круглых icon-first категорий: опциональный, повторный тап снимает выбор. */
@Composable
private fun QuickCategoryRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (CategoryEntity) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
