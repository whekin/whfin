package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.PersonRole
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import android.content.res.Configuration

private val PersonColors = listOf(0xFF78906F, 0xFFD16D5A, 0xFFE0A246, 0xFF5D7F91, 0xFF8873A8, 0xFF4C956C).map(Long::toInt)

@Composable
fun PeopleRoute(viewModel: PeopleViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsState()
    PeopleScreen(
        rows = rows,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onArchive = viewModel::archive,
    )
}

@Composable
fun PeopleScreen(
    rows: List<PersonListRow>?,
    onCreate: (String, PersonRole?, Int) -> Unit,
    onUpdate: (PersonEntity, String, PersonRole?, Int) -> Unit,
    onArchive: (PersonEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<PersonListRow?>(null) }
    var creating by remember { mutableStateOf(false) }

    // До первого Room-snapshot ничего не показываем — не мигаем пустым состоянием.
    if (rows == null) return

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.people_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WhfinSectionLabel(stringResource(R.string.people_title))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                WhfinLedgerRow(
                    title = row.person.name,
                    supportingText = personSummary(row),
                    supportingMaxLines = 3,
                    markerColor = Color(row.person.color),
                    onClick = { editing = row },
                    divider = true,
                )
            }
            WhfinLedgerRow(
                title = stringResource(R.string.people_new),
                icon = Icons.Default.Add,
                onClick = { creating = true },
            )
        }
    }

    editing?.let { row ->
        val current = rows.firstOrNull { it.person.id == row.person.id }
        if (current == null) {
            editing = null
        } else {
            PersonSheet(
                title = stringResource(R.string.people_edit_title),
                initial = current.person,
                primaryLabel = stringResource(R.string.action_save),
                onDismiss = { editing = null },
                onPrimary = { name, role, color ->
                    onUpdate(current.person, name, role, color)
                    editing = null
                },
                archiveRow = current,
                onArchive = {
                    onArchive(current.person)
                    editing = null
                },
            )
        }
    }

    if (creating) {
        PersonSheet(
            title = stringResource(R.string.people_new),
            initial = null,
            primaryLabel = stringResource(R.string.people_create),
            onDismiss = { creating = false },
            onPrimary = { name, role, color ->
                onCreate(name, role, color)
                creating = false
            },
        )
    }
}

@Composable
private fun personSummary(row: PersonListRow): String? {
    fun amounts(values: List<Pair<String, Long>>) =
        values.joinToString(", ") { (currency, amount) -> formatMinor(amount, currency) }
    val parts = buildList {
        if (row.owesMe.isNotEmpty()) add(stringResource(R.string.people_owes_you, amounts(row.owesMe)))
        if (row.iOwe.isNotEmpty()) add(stringResource(R.string.people_you_owe, amounts(row.iOwe)))
        if (row.spentThisMonth.isNotEmpty()) {
            add(stringResource(R.string.people_spent_month, amounts(row.spentThisMonth)))
        }
        row.person.role?.let { add(stringResource(it.labelResource())) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun PersonRole.labelResource(): Int = when (this) {
    PersonRole.PARTNER -> R.string.person_role_partner
    PersonRole.FAMILY -> R.string.person_role_family
    PersonRole.FRIEND -> R.string.person_role_friend
    PersonRole.COLLEAGUE -> R.string.person_role_colleague
    PersonRole.OTHER -> R.string.person_role_other
}

@Composable
private fun PersonSheet(
    title: String,
    initial: PersonEntity?,
    primaryLabel: String,
    onDismiss: () -> Unit,
    onPrimary: (String, PersonRole?, Int) -> Unit,
    archiveRow: PersonListRow? = null,
    onArchive: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role) }
    var color by remember { mutableIntStateOf(initial?.color ?: PersonColors.first()) }
    var confirmArchive by remember { mutableStateOf(false) }

    WhfinFormSheet(
        title = title,
        onDismiss = onDismiss,
        primaryLabel = primaryLabel,
        primaryEnabled = name.isNotBlank(),
        onPrimary = { onPrimary(name, role, color) },
    ) {
        WhfinField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = stringResource(R.string.people_name),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonRole.entries.forEach { value ->
                FilterChip(
                    selected = role == value,
                    onClick = { role = if (role == value) null else value },
                    label = { Text(stringResource(value.labelResource())) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonColors.forEach { value ->
                Surface(
                    onClick = { color = value },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color(value),
                            border = if (value == color) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else null,
                            modifier = Modifier.size(30.dp),
                        ) {}
                    }
                }
            }
        }
        if (onArchive != null) {
            WhfinButton(
                label = stringResource(R.string.people_archive),
                onClick = { confirmArchive = true },
                style = WhfinActionStyle.DestructiveSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmArchive && archiveRow != null && onArchive != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.people_archive_confirm_title, archiveRow.person.name)) },
            text = {
                Text(
                    stringResource(
                        if (archiveRow.hasOpenDebts) R.string.people_archive_confirm_body_debts
                        else R.string.people_archive_confirm_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    onArchive()
                }) { Text(stringResource(R.string.people_archive), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Preview(name = "People light", widthDp = 400, heightDp = 700, showBackground = true)
@Preview(name = "People dark", widthDp = 400, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "People font 1.5", widthDp = 400, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun PeopleScreenPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PeopleScreen(
                rows = listOf(
                    PersonListRow(
                        person = PersonEntity(id = 1, name = "Alisa", role = PersonRole.FRIEND, color = 0xFF78906F.toInt()),
                        owesMe = listOf("GEL" to 10_000L),
                        iOwe = emptyList(),
                        spentThisMonth = listOf("GEL" to 4_550L),
                        hasOpenDebts = true,
                    ),
                    PersonListRow(
                        person = PersonEntity(id = 2, name = "Nika", role = null, color = 0xFF5D7F91.toInt()),
                        owesMe = emptyList(),
                        iOwe = listOf("USD" to 2_000L),
                        spentThisMonth = emptyList(),
                        hasOpenDebts = true,
                    ),
                ),
                onCreate = { _, _, _ -> },
                onUpdate = { _, _, _, _ -> },
                onArchive = {},
            )
        }
    }
}
