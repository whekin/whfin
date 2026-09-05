package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One option in a [WhfinChoiceList]. */
data class WhfinChoice<T>(
    val value: T,
    val label: String,
    val supportingText: String? = null,
)

/**
 * A choice between behaviours, read as a choice rather than as a row of buttons.
 *
 * Stacked full-width pills made the selected option a filled primary block — the same weight, colour
 * and shape as the sheet's own Save. Three of them above the real action read as four competing
 * commands, when only one of them commands anything. A mark says "this one is chosen" without
 * borrowing the appearance of "press this".
 */
@Composable
fun <T> WhfinChoiceList(
    options: List<WhfinChoice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    WhfinLedgerGroup(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) },
                    )
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (isSelected) Icons.Default.RadioButtonChecked
                    else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    if (option.supportingText != null) Text(
                        option.supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < options.lastIndex) HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
