package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.mutation.ExpenseBeneficiary

internal data class BeneficiaryDraft(val personId: Long? = null, val name: String? = null, val half: Boolean = false) {
    fun mutation(total: Long) = ExpenseBeneficiary(personId, name, if (half) total / 2 else null)
}

@Composable
internal fun ExpenseBeneficiaryEditor(people: List<PersonEntity>, initial: BeneficiaryDraft?,
    onBack: () -> Unit, onApply: (BeneficiaryDraft?) -> Unit) {
    var selected by remember { mutableStateOf(initial?.personId) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var half by remember { mutableStateOf(initial?.half ?: false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WhfinBackButton(stringResource(R.string.action_back), onBack)
            Text(stringResource(R.string.expense_beneficiary), style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.expense_beneficiary_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinLedgerRow(stringResource(R.string.expense_for_me), onClick = { onApply(null) })
            WhfinChoiceList(people.map { WhfinChoice(it.id, it.name) }, selected, { selected = it; name = "" })
            WhfinField(name, { name = it; if (it.isNotBlank()) selected = null }, stringResource(R.string.debt_new_person), modifier = Modifier.fillMaxWidth().testTag("beneficiary-new-name"))
            if (selected != null || name.isNotBlank()) {
                WhfinSegmentedChoice(listOf(WhfinChoice(false, stringResource(R.string.expense_beneficiary_whole)),
                    WhfinChoice(true, stringResource(R.string.split_half))), half, { half = it })
                Text(stringResource(if (half) R.string.expense_beneficiary_half_hint else R.string.expense_beneficiary_full_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WhfinButton(stringResource(R.string.action_done), {
            val existing = people.singleOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            onApply(BeneficiaryDraft(selected ?: existing?.id, name.trim().takeIf { selected == null && existing == null }, half))
        }, Modifier.fillMaxWidth().padding(20.dp), enabled = selected != null || name.isNotBlank())
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Expense recipient", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Expense recipient dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Expense recipient compact large", heightDp = 500, fontScale = 1.5f)
@Composable
private fun ExpenseBeneficiaryPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    ExpenseBeneficiaryEditor(listOf(PersonEntity(id = 1, name = "Mira", color = 0)), BeneficiaryDraft(personId = 1), {}, {})
}
