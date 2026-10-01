package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.FormSaveState
import dev.whekin.whfin.ui.counterpartyLabel
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.ui.components.FormSheet

/** One scrolling catalogue beneath stable context/search; selection returns to the receipt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    item: FeedItem, categories: List<CategoryEntity>, onDismiss: () -> Unit,
    onSelect: (CategoryEntity) -> Unit, onCreateCategory: (String, CategoryKind, String, Int) -> Unit,
    formState: FormSaveState = FormSaveState(),
    onCreateAndSelect: ((String, CategoryKind, String, Int) -> Unit)? = null,
) {
    var creating by rememberSaveable(item.tx.id) { mutableStateOf(false) }
    var query by rememberSaveable(item.tx.id) { mutableStateOf("") }
    var name by rememberSaveable(item.tx.id) { mutableStateOf("") }
    val kind = if (item.tx.amountMinor >= 0) CategoryKind.INCOME else CategoryKind.EXPENSE
    val initialIcon = if (kind == CategoryKind.EXPENSE) "VolunteerActivism" else "Work"
    val initialColor = if (kind == CategoryKind.EXPENSE) 0xFFD16D5A.toInt() else 0xFF78906F.toInt()
    var icon by rememberSaveable(item.tx.id) { mutableStateOf(initialIcon) }
    var color by rememberSaveable(item.tx.id) { mutableIntStateOf(initialColor) }
    val title = item.transferSummary ?: (item.merchant?.displayName ?: item.tx.rawCounterparty)?.let { counterpartyLabel(it) }
        ?: stringResource(R.string.feed_no_description)
    val amount = transactionPresentationAmount(item.tx)
    val context = "$title · ${formatMinor(amount.minor, amount.currency, withSign = true)}"
    val tree = dev.whekin.whfin.data.categorization.CategoryTree(categories)
    val words = query.trim().lowercase().replace('ё', 'е').split(Regex("\\s+")).filter(String::isNotBlank)
    val visible = categories.filter { category ->
        !category.isSystem && category.kind == kind && words.all { word ->
            (tree.qualifiedName(category.id) ?: category.name).lowercase().replace('ё', 'е').contains(word)
        }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    if (creating) {
        FormSheet(stringResource(R.string.category_new), { creating = false },
            stringResource(if (formState.busy) R.string.form_saving else if (onCreateAndSelect != null) R.string.category_create_and_select else R.string.category_create),
            name.isNotBlank(), {
                if (onCreateAndSelect != null) onCreateAndSelect(name.trim(), kind, icon, color)
                else { onCreateCategory(name.trim(), kind, icon, color); creating = false; query = "" }
            }, busy = formState.busy, dirty = name.isNotBlank() || icon != initialIcon || color != initialColor) {
            Text(context, style = MaterialTheme.typography.bodySmall)
            if (formState.failed) Text(stringResource(R.string.form_save_failed), color = MaterialTheme.colorScheme.error)
            WhfinField(name, { name = it.take(32) }, stringResource(R.string.category_name), modifier = Modifier.fillMaxWidth())
            CategoryAppearancePicker(icon, color, { icon = it }, { color = it }, enabled = !formState.busy)
        }
        return
    }
    val busy = formState.busy
    WhfinPickerSheet(
        title = stringResource(R.string.category_picker_title), context = context, query = query,
        searchHint = stringResource(R.string.category_search), clearDescription = stringResource(R.string.search_clear),
        onQuery = { query = it }, onDismiss = onDismiss, busy = busy,
        problem = if (formState.failed) stringResource(R.string.form_save_failed) else null,
        createDescription = stringResource(R.string.category_new), onCreate = { creating = true },
        listModifier = Modifier.testTag("transaction-category-list"),
    ) {
                if (item.merchant != null && query.isBlank()) item { Text(stringResource(R.string.category_rule_scope),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)) }
                if (visible.isEmpty()) item {
                    Text(stringResource(R.string.category_search_empty), style = MaterialTheme.typography.bodyMedium)
                    WhfinButton(stringResource(R.string.category_new), { creating = true }, style = WhfinActionStyle.Quiet,
                        enabled = !busy, leadingIcon = Icons.Default.Add)
                }
                items(visible, key = { it.id }) { category ->
                    WhfinLedgerRow(tree.qualifiedName(category.id) ?: category.name, titleMaxLines = 3, icon = CategoryIcons.resolve(category.icon),
                        iconTint = androidx.compose.ui.graphics.Color(category.color),
                        trailing = if (category.id == item.tx.categoryId) {{ Icon(Icons.Default.Check, null) }} else null,
                        onClick = if (busy) null else {{ focus.clearFocus(); keyboard?.hide(); onSelect(category) }},
                        modifier = Modifier.testTag("transaction-category-${category.id}").semantics {
                            selected = category.id == item.tx.categoryId
                        }, divider = true)
                }
    }
}
