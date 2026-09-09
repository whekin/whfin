package dev.whekin.whfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
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
    WhfinLedgerGroup(modifier.fillMaxWidth().selectableGroup()) {
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
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
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

/**
 * A short, mutually exclusive choice. Reflow to named radio rows when labels cannot fit;
 * never shrink or ellipsize a setting just to preserve the segmented appearance.
 */
@Composable
fun <T> WhfinSegmentedChoice(
    options: List<WhfinChoice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val slotWidth = (maxWidth - 8.dp) / options.size
        val available = with(density) { (slotWidth - 24.dp).toPx() }
        val fits = options.all {
            it.supportingText == null && measurer.measure(it.label, style).size.width <= available
        }
        if (!fits) {
            WhfinChoiceList(options, selected, onSelect)
        } else {
            Row(
                Modifier.fillMaxWidth().selectableGroup()
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(4.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f)
                            .then(Modifier.background(
                                if (isSelected) WhfinThemeTokens.raisedSurface
                                else androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.shapes.small,
                            ))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) },
                            )
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = style,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
