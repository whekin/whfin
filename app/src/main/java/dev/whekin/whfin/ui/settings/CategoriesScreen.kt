package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.components.CategoryAppearancePicker
import dev.whekin.whfin.ui.theme.WhfinTheme
import android.content.res.Configuration

@Composable
fun CategoriesRoute(viewModel: CategoriesViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsState()
    CategoriesScreen(
        rows = rows,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onMove = viewModel::move,
        onDelete = viewModel::delete,
    )
}

@Composable
fun CategoriesScreen(
    rows: List<CategoryRow>?,
    onCreate: (String, CategoryKind, String, Int) -> Unit,
    onUpdate: (CategoryEntity, String, String, Int) -> Unit,
    onMove: (CategoryEntity, Int) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryRow?>(null) }
    var creatingKind by remember { mutableStateOf<CategoryKind?>(null) }

    // До первого Room-snapshot ничего не показываем — не мигаем пустыми секциями.
    if (rows == null) return

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        listOf(
            CategoryKind.EXPENSE to R.string.categories_expense,
            CategoryKind.INCOME to R.string.categories_income,
        ).forEach { (kind, label) ->
            val section = rows.filter { it.category.kind == kind }
            WhfinSectionLabel(stringResource(label))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                section.forEach { row ->
                    val system = row.category.isSystem
                    WhfinLedgerRow(
                        title = row.category.name,
                        supportingText = if (system) {
                            stringResource(R.string.categories_system_hint)
                        } else {
                            pluralStringResource(R.plurals.categories_usage, row.uses, row.uses)
                        },
                        icon = CategoryIcons.resolve(row.category.icon),
                        iconTint = Color(row.category.color),
                        trailing = if (system) {
                            { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else null,
                        onClick = if (system) null else { { editing = row } },
                        divider = true,
                    )
                }
                WhfinLedgerRow(
                    title = stringResource(R.string.category_new),
                    icon = Icons.Default.Add,
                    onClick = { creatingKind = kind },
                )
            }
        }
    }

    editing?.let { row ->
        // Живая позиция строки: после move показываем актуальное состояние из rows.
        val current = rows.firstOrNull { it.category.id == row.category.id }
        if (current == null) {
            editing = null
        } else {
            EditCategorySheet(
                row = current,
                siblings = rows.filter { it.category.kind == current.category.kind },
                onDismiss = { editing = null },
                onSave = { name, icon, color ->
                    onUpdate(current.category, name, icon, color)
                    editing = null
                },
                onMove = { delta -> onMove(current.category, delta) },
                onDelete = {
                    onDelete(current.category)
                    editing = null
                },
            )
        }
    }

    creatingKind?.let { kind ->
        CreateCategorySheet(
            kind = kind,
            onDismiss = { creatingKind = null },
            onCreate = { name, icon, color ->
                onCreate(name, kind, icon, color)
                creatingKind = null
            },
        )
    }
}

@Composable
private fun EditCategorySheet(
    row: CategoryRow,
    siblings: List<CategoryRow>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(row.category.name) }
    var icon by remember { mutableStateOf(row.category.icon) }
    var color by remember { mutableIntStateOf(row.category.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val index = siblings.indexOfFirst { it.category.id == row.category.id }

    WhfinFormSheet(
        title = stringResource(R.string.categories_edit_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = name.isNotBlank(),
        onPrimary = { onSave(name, icon, color) },
    ) {
        WhfinField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = stringResource(R.string.category_name),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryAppearancePicker(icon, color, { icon = it }, { color = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WhfinButton(
                label = stringResource(R.string.categories_move_up),
                onClick = { onMove(-1) },
                enabled = index > 0,
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
            WhfinButton(
                label = stringResource(R.string.categories_move_down),
                onClick = { onMove(1) },
                enabled = index in 0 until siblings.lastIndex,
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
        }
        WhfinButton(
            label = stringResource(R.string.categories_delete),
            onClick = { confirmDelete = true },
            style = WhfinActionStyle.DestructiveSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmDelete) {
        WhfinConfirmDialog(
            title = stringResource(R.string.categories_delete_confirm_title, row.category.name),
            body = if (row.uses > 0) {
                pluralStringResource(R.plurals.categories_delete_confirm_body, row.uses, row.uses)
            } else {
                stringResource(R.string.categories_delete_confirm_body_unused)
            },
            confirmLabel = stringResource(R.string.categories_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                    confirmDelete = false
                    onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CreateCategorySheet(
    kind: CategoryKind,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember {
        mutableStateOf(if (kind == CategoryKind.EXPENSE) "ShoppingCart" else "Work")
    }
    var color by remember {
        mutableIntStateOf(if (kind == CategoryKind.EXPENSE) 0xFFD16D5A.toInt() else 0xFF78906F.toInt())
    }
    WhfinFormSheet(
        title = stringResource(R.string.category_new),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.category_create),
        primaryEnabled = name.isNotBlank(),
        onPrimary = { onCreate(name, icon, color) },
    ) {
        WhfinField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = stringResource(R.string.category_name),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryAppearancePicker(icon, color, { icon = it }, { color = it })
    }
}

@Preview(name = "Categories light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Categories dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Categories font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    WhfinTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            CategoriesScreen(
                rows = listOf(
                    CategoryRow(CategoryEntity(id = 1, name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0xFF78906F.toInt(), sortOrder = 0), 42),
                    CategoryRow(CategoryEntity(id = 2, name = "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0xFF5D7F91.toInt(), sortOrder = 1), 7),
                    CategoryRow(CategoryEntity(id = 3, name = "Unaccounted", kind = CategoryKind.EXPENSE, icon = "Sell", color = 0xFFE0A246.toInt(), isSystem = true, sortOrder = 2), 3),
                    CategoryRow(CategoryEntity(id = 4, name = "Salary", kind = CategoryKind.INCOME, icon = "Work", color = 0xFF4C956C.toInt(), sortOrder = 3), 12),
                ),
                onCreate = { _, _, _, _ -> },
                onUpdate = { _, _, _, _ -> },
                onMove = { _, _ -> },
                onDelete = {},
            )
        }
    }
}
