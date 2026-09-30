package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.ExpenseBeneficiary
import dev.whekin.whfin.ui.FormSaveState
import dev.whekin.whfin.ui.OnFormSaved
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.parseToMinor
import java.math.BigDecimal

@Composable
internal fun DebtPersonSheet(
    item: FeedItem, people: List<PersonEntity>, onDismiss: () -> Unit,
    onSave: (ExpenseBeneficiary) -> Unit, formState: FormSaveState = FormSaveState(),
) {
    var personId by rememberSaveable(item.tx.id) { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable(item.tx.id) { mutableStateOf(people.isEmpty()) }
    var name by rememberSaveable(item.tx.id) { mutableStateOf("") }
    val valid = if (creating) name.isNotBlank() else people.any { it.id == personId }
    OnFormSaved(formState, onDismiss)
    FormSheet(
        title = stringResource(R.string.debt_who_owes), onDismiss = onDismiss,
        primaryLabel = stringResource(if (formState.busy) R.string.form_saving else R.string.debt_action_short),
        primaryEnabled = valid, busy = formState.busy, dirty = personId != null || name.isNotBlank(),
        onPrimary = { if (valid) onSave(ExpenseBeneficiary(personId.takeUnless { creating }, name.trim().takeIf { creating })) },
        footer = {
            if (valid) Text(stringResource(R.string.debt_named_preview,
                if (creating) name.trim() else people.first { it.id == personId }.name,
                formatMinor(-item.tx.amountMinor, item.tx.currency)), style = MaterialTheme.typography.bodySmall)
        },
    ) {
        WhfinAmount(formatMinor(-item.tx.amountMinor, item.tx.currency), symbol = currencySymbol(item.tx.currency),
            style = MaterialTheme.typography.headlineMedium)
        Text(item.merchant?.displayName ?: item.tx.rawCounterparty.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        if (formState.failed) Text(stringResource(R.string.form_save_failed), color = MaterialTheme.colorScheme.error)
        AllocationPersonChooser(people, personId, { personId = it; creating = false }, creating,
            { creating = it; if (it) personId = null }, name, { name = it }, formState.busy)
    }
}

internal enum class SplitMode { HALF, FULL, CUSTOM }

@Composable
internal fun SplitSheet(
    item: FeedItem, people: List<PersonEntity>, onDismiss: () -> Unit,
    onSave: (ExpenseBeneficiary, AllocationPurpose) -> Unit, formState: FormSaveState = FormSaveState(),
) {
    // This compact editor cannot silently overwrite a previously saved multi-person allocation.
    if (item.splitAllocations.size > 1 || item.splitOnPeople.size > 1) {
        FormSheet(stringResource(R.string.split_title), onDismiss, stringResource(R.string.action_done), true, onDismiss) {
            Text(stringResource(R.string.split_multiple_preserved))
            item.splitOnPeople.forEach { (name, amount) -> WhfinLedgerRow(name,
                supportingText = formatMinor(amount, item.tx.currency)) }
        }
        return
    }
    val total = -item.tx.amountMinor
    val existing = item.splitAllocations.singleOrNull()
    val initialPerson = existing?.personId ?: item.splitOnPeople.singleOrNull()?.first?.let { name ->
        people.singleOrNull { it.name == name }?.id
    }
    val initialAmount = existing?.amountMinor?.let { -it } ?: item.splitOnPeople.singleOrNull()?.second
    val initialMode = when {
        initialAmount == null -> SplitMode.HALF
        existing?.purpose == AllocationPurpose.GIFT && initialAmount == total -> SplitMode.FULL
        initialAmount == total / 2 -> SplitMode.HALF
        else -> SplitMode.CUSTOM
    }
    var personId by rememberSaveable(item.tx.id) { mutableStateOf(initialPerson) }
    var creating by rememberSaveable(item.tx.id) { mutableStateOf(people.isEmpty()) }
    var name by rememberSaveable(item.tx.id) { mutableStateOf("") }
    var mode by rememberSaveable(item.tx.id) { mutableStateOf(initialMode) }
    var gift by rememberSaveable(item.tx.id) { mutableStateOf(existing?.purpose == AllocationPurpose.GIFT) }
    var custom by rememberSaveable(item.tx.id) { mutableStateOf(initialAmount?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    val amount = when (mode) { SplitMode.HALF -> total / 2; SplitMode.FULL -> total; SplitMode.CUSTOM -> parseToMinor(custom) }
    val amountValid = amount != null && amount in 1..total
    val person = people.firstOrNull { it.id == personId }
    val valid = amountValid && (if (creating) name.isNotBlank() else person != null)
    val dirty = personId != initialPerson || name.isNotBlank() || mode != initialMode ||
        (mode == SplitMode.CUSTOM && amount != initialAmount) || gift != (existing?.purpose == AllocationPurpose.GIFT)
    OnFormSaved(formState, onDismiss)
    FormSheet(
        title = stringResource(R.string.split_title), onDismiss = onDismiss,
        primaryLabel = stringResource(if (formState.busy) R.string.form_saving else R.string.action_save),
        primaryEnabled = valid, busy = formState.busy, dirty = dirty,
        onPrimary = {
            if (valid) onSave(ExpenseBeneficiary(personId.takeUnless { creating }, name.trim().takeIf { creating }, amount),
                if (gift || mode == SplitMode.FULL) AllocationPurpose.GIFT else AllocationPurpose.SHARED)
        },
        footer = {
            if (valid) Text(stringResource(R.string.split_named_preview, if (creating) name.trim() else person!!.name,
                formatMinor(requireNotNull(amount), item.tx.currency), formatMinor(total - amount, item.tx.currency)),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("split-summary"))
            else Text(stringResource(if (!amountValid && mode == SplitMode.CUSTOM) R.string.split_amount_invalid else R.string.split_choose_person),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        WhfinAmount(formatMinor(total, item.tx.currency), symbol = currencySymbol(item.tx.currency), style = MaterialTheme.typography.headlineMedium)
        if (formState.failed) Text(stringResource(R.string.form_save_failed), color = MaterialTheme.colorScheme.error)
        AllocationPersonChooser(people, personId, { personId = it; creating = false }, creating,
            { creating = it; if (it) personId = null }, name, { name = it }, formState.busy)
        WhfinFieldLabel(stringResource(R.string.split_how_much))
        WhfinChoiceRail(Modifier.testTag("split-mode-rail"), revealIndex = mode.ordinal) {
            items(SplitMode.entries, key = { it.name }) { option ->
                WhfinFilterPill(stringResource(when (option) {
                    SplitMode.HALF -> R.string.split_half; SplitMode.FULL -> R.string.split_gift_full; SplitMode.CUSTOM -> R.string.split_custom
                }), mode == option, { if (!formState.busy) { mode = option; gift = option == SplitMode.FULL } })
            }
        }
        if (mode == SplitMode.CUSTOM) WhfinField(custom, { if (!formState.busy) custom = it.take(14) },
            stringResource(R.string.split_amount_on_them), suffix = item.tx.currency, keyboardType = KeyboardType.Decimal,
            isError = custom.isNotBlank() && !amountValid,
            supportingText = if (custom.isNotBlank() && !amountValid) stringResource(R.string.split_amount_limit, formatMinor(total, item.tx.currency)) else null,
            modifier = Modifier.fillMaxWidth().testTag("split-amount"))
        if (gift) Text(stringResource(R.string.split_gift_hint), style = MaterialTheme.typography.bodySmall)
    }
}

/** A few familiar names first; the searchable list and new-person field appear on request. */
@Composable
private fun AllocationPersonChooser(
    people: List<PersonEntity>, selectedId: Long?, onSelect: (Long) -> Unit,
    creating: Boolean, onCreating: (Boolean) -> Unit, name: String, onName: (String) -> Unit, busy: Boolean,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    WhfinFieldLabel(stringResource(R.string.allocation_person_label))
    if (creating) {
        WhfinField(name, { if (!busy) onName(it.take(40)) }, stringResource(R.string.debt_new_person),
            modifier = Modifier.fillMaxWidth().testTag("allocation-new-person"))
        if (people.isNotEmpty()) WhfinButton(stringResource(R.string.allocation_existing_person), { onCreating(false) },
            enabled = !busy, style = WhfinActionStyle.Quiet)
    } else {
        if (searching) {
            WhfinField(query, { query = it }, stringResource(R.string.allocation_find_person), leadingIcon = Icons.Default.Search,
                modifier = Modifier.fillMaxWidth())
            val matches = people.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (matches.isEmpty()) Text(stringResource(R.string.allocation_people_empty), style = MaterialTheme.typography.bodySmall)
            matches.forEach { person -> WhfinLedgerRow(person.name,
                trailing = if (person.id == selectedId) {{ Icon(Icons.Default.Check, null) }} else null,
                onClick = { if (!busy) { focus.clearFocus(); keyboard?.hide(); onSelect(person.id); searching = false } },
                modifier = Modifier.testTag("allocation-person-${person.id}")) }
        } else {
            val quick = (people.take(3) + people.filter { it.id == selectedId }).distinctBy { it.id }
            WhfinChoiceRail(revealIndex = quick.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }) {
                items(quick, key = { it.id }) { person ->
                    WhfinFilterPill(person.name, person.id == selectedId, { if (!busy) onSelect(person.id) })
                }
            }
            if (people.size > 3) WhfinButton(stringResource(R.string.allocation_find_person), { searching = true },
                enabled = !busy, style = WhfinActionStyle.Quiet, leadingIcon = Icons.Default.Search)
        }
        WhfinButton(stringResource(R.string.debt_new_person), { onCreating(true) }, enabled = !busy,
            style = WhfinActionStyle.Quiet, leadingIcon = Icons.Default.PersonAdd)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Split light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "Split dark", widthDp = 400, heightDp = 850, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Split large compact", locale = "ru", widthDp = 400, heightDp = 640, fontScale = 1.5f)
@Composable
private fun SplitPreview() {
    dev.whekin.whfin.ui.theme.WhfinTheme {
        SplitSheet(FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL",
            occurredAt = 1000, status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null,
            day = java.time.LocalDate.of(2026, 9, 1)), listOf(PersonEntity(id = 1, name = "Mira", color = 0)), {}, { _, _ -> })
    }
}
