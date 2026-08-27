package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Layout only: the feature owns its dialog, workspace identity, draft and side effects. */
@Composable
fun WhfinFullScreenForm(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    back: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                WhfinIconButton(if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    closeDescription, onClose, outlined = false)
                Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("whfin-form-scroll").padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                footer()
                WhfinButton(primaryLabel, onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Calendar in the form's own scene: no second dialog and no competing keyboard window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhfinCalendar(state: DatePickerState, modifier: Modifier = Modifier) {
    DatePicker(
        state = state,
        modifier = modifier,
        title = null,
        headline = null,
        showModeToggle = false,
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun WhfinTimelineScrubber(index: Int, count: Int, description: String, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (count < 2) return
    Slider(
        value = index.coerceIn(0, count - 1).toFloat(),
        onValueChange = { onSelect(it.roundToInt()) },
        valueRange = 0f..(count - 1).toFloat(),
        steps = count - 2,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
    )
}
