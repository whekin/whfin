package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.ui.components.FormSheet
import androidx.compose.runtime.saveable.rememberSaveable
import dev.whekin.whfin.ui.FormSaveState
import dev.whekin.whfin.ui.OnFormSaved
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
    val formState by viewModel.formSaveState.collectAsState()
    CategoriesScreen(
        rows = rows,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onSetParent = viewModel::setParent,
        onMove = viewModel::move,
        onDelete = viewModel::delete,
        formState = formState,
        onSaveDefinition = viewModel::save,
    )
}

@Composable
fun CategoriesScreen(
    rows: List<CategoryRow>?,
    onCreate: (String, CategoryKind, String, Int) -> Unit,
    onUpdate: (CategoryEntity, String, String, Int) -> Unit,
    onSetParent: (CategoryEntity, Long?) -> Unit = { _, _ -> },
    onMove: (CategoryEntity, Int) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    formState: FormSaveState? = null,
    onSaveDefinition: ((CategoryEntity, String, String, Int, Long?, Int) -> Unit)? = null,
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingKind by rememberSaveable { mutableStateOf<CategoryKind?>(null) }
    if (formState != null) OnFormSaved(formState) { editingId = null; creatingKind = null }

    // До первого Room-snapshot ничего не показываем — не мигаем пустыми секциями.
    if (rows == null) {
        dev.whekin.whfin.core.ui.WhfinSkeleton(stringResource(R.string.categories_title), Modifier.fillMaxWidth().padding(20.dp)) {
            repeat(5) { dev.whekin.whfin.core.ui.WhfinSkeletonLedgerRow() }
        }
        return
    }

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
                        onClick = if (system) null else { { editingId = row.category.id } },
                        divider = true,
                        // A child is offset rather than re-labelled: the group is directly above it,
                        // so repeating its name on every row would only make the list wordier.
                        modifier = if (row.isChild) Modifier.padding(start = 24.dp) else Modifier,
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

    editingId?.let { id ->
        // Живая позиция строки: после move показываем актуальное состояние из rows.
        val current = rows.firstOrNull { it.category.id == id }
        if (current == null) {
            editingId = null
        } else {
            EditCategorySheet(
                row = current,
                siblings = rows.filter { it.category.kind == current.category.kind },
                onDismiss = { editingId = null },
                formState = formState ?: FormSaveState(),
                onSave = { expected, name, icon, color, parent, moveBy ->
                    if (onSaveDefinition != null) onSaveDefinition(expected, name, icon, color, parent, moveBy)
                    else {
                        onUpdate(current.category, name, icon, color)
                        if (parent != current.category.parentId) onSetParent(current.category, parent)
                        if (moveBy != 0) onMove(current.category, moveBy)
                        editingId = null
                    }
                },
                onDelete = {
                    onDelete(current.category)
                    if (formState == null) editingId = null
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
                if (formState == null) creatingKind = null
            },
            formState = formState ?: FormSaveState(),
        )
    }
}

@Composable
internal fun EditCategorySheet(
    row: CategoryRow,
    siblings: List<CategoryRow>,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity, String, String, Int, Long?, Int) -> Unit,
    onDelete: () -> Unit,
    formState: FormSaveState = FormSaveState(),
) {
    val expected = rememberSaveable(row.category.id, saver = CategoryDefinitionSaver) { row.category }
    var name by rememberSaveable(row.category.id) { mutableStateOf(expected.name) }
    var icon by rememberSaveable(row.category.id) { mutableStateOf(expected.icon) }
    var color by rememberSaveable(row.category.id) { mutableIntStateOf(expected.color) }
    var parentId by rememberSaveable(row.category.id) { mutableStateOf(expected.parentId) }
    var moveBy by rememberSaveable(row.category.id) { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val orderedSiblings = siblings.filter { it.category.parentId == parentId || it.category.id == row.category.id }
        .sortedWith(compareBy<CategoryRow> { it.category.sortOrder }.thenBy { it.category.id })
    val index = orderedSiblings.indexOfFirst { it.category.id == row.category.id }
    // Only a category that is not itself a group can join one, and it cannot join itself.
    val groups = if (row.isGroup) emptyList() else siblings
        .filter { !it.category.isSystem && it.category.parentId == null && it.category.id != row.category.id }
    val ownGroupLabel = stringResource(R.string.categories_group_none)

    FormSheet(
        title = stringResource(R.string.categories_edit_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(if (formState.busy) R.string.form_saving else R.string.action_save),
        primaryEnabled = name.isNotBlank() && row.category == expected, busy = formState.busy,
        dirty = name != expected.name || icon != expected.icon || color != expected.color || parentId != expected.parentId || moveBy != 0,
        onPrimary = { onSave(expected, name, icon, color, parentId, moveBy) },
    ) {
        if (formState.failed) Text(stringResource(R.string.form_save_failed), color = MaterialTheme.colorScheme.error)
        if (row.category != expected) Text(stringResource(R.string.category_changed), color = MaterialTheme.colorScheme.error)
        WhfinField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = stringResource(R.string.category_name),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryAppearancePicker(icon, color, { icon = it }, { color = it }, enabled = !formState.busy)
        if (groups.isNotEmpty()) {
            WhfinFieldLabel(stringResource(R.string.categories_group_label))
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = ownGroupLabel,
                        selected = parentId == null,
                        onClick = { parentId = null; moveBy = 0 },
                    )
                }
                items(groups, key = { it.category.id }) { candidate ->
                    WhfinFilterPill(
                        label = candidate.category.name,
                        selected = parentId == candidate.category.id,
                        onClick = { parentId = candidate.category.id; moveBy = 0 },
                    )
                }
            }
        }
        Text(stringResource(R.string.category_draft_position, index + moveBy + 1, orderedSiblings.size),
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WhfinButton(
                label = stringResource(R.string.categories_move_up),
                onClick = { moveBy-- },
                enabled = !formState.busy && index + moveBy > 0,
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
            WhfinButton(
                label = stringResource(R.string.categories_move_down),
                onClick = { moveBy++ },
                enabled = !formState.busy && index + moveBy in 0 until orderedSiblings.lastIndex,
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

private val CategoryDefinitionSaver = androidx.compose.runtime.saveable.listSaver<CategoryEntity, Any?>(
    save = { listOf(it.id, it.name, it.kind.name, it.icon, it.color, it.isSystem, it.sortOrder, it.parentId) },
    restore = { CategoryEntity(id = it[0] as Long, name = it[1] as String, kind = CategoryKind.valueOf(it[2] as String),
        icon = it[3] as String, color = it[4] as Int, isSystem = it[5] as Boolean, sortOrder = it[6] as Int, parentId = it[7] as Long?) },
)

@Composable
private fun CreateCategorySheet(
    kind: CategoryKind,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int) -> Unit,
    formState: FormSaveState = FormSaveState(),
) {
    var name by rememberSaveable(kind) { mutableStateOf("") }
    var icon by rememberSaveable(kind) {
        mutableStateOf(if (kind == CategoryKind.EXPENSE) "ShoppingCart" else "Work")
    }
    var color by rememberSaveable(kind) {
        mutableIntStateOf(if (kind == CategoryKind.EXPENSE) 0xFFD16D5A.toInt() else 0xFF78906F.toInt())
    }
    FormSheet(
        title = stringResource(R.string.category_new),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.category_create),
        primaryEnabled = name.isNotBlank(), busy = formState.busy, dirty = name.isNotBlank(),
        onPrimary = { onCreate(name, icon, color) },
    ) {
        if (formState.failed) Text(stringResource(R.string.form_save_failed), color = MaterialTheme.colorScheme.error)
        WhfinField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = stringResource(R.string.category_name),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryAppearancePicker(icon, color, { icon = it }, { color = it }, enabled = !formState.busy)
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
