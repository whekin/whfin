package dev.whekin.whfin.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity

/** System, light and dark previews are the control, not illustrations beside another control. */
@Composable
fun WhfinThemeChoice(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    require(labels.size == 3)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val widest = labels.maxOf { measurer.measure(it, labelStyle).size.width }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelRoom = with(density) { ((maxWidth - 16.dp) / 3 - 20.dp).toPx() }
        if (widest > labelRoom) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                ThemeOption(index, label, index == selectedIndex, { onSelect(index) }, Modifier.fillMaxWidth(), compact = true)
            }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                ThemeOption(index, label, index == selectedIndex, { onSelect(index) }, Modifier.weight(1f).fillMaxHeight(), compact = false)
            }
        }
    }
}

@Composable
private fun ThemeOption(index: Int, label: String, chosen: Boolean, onSelect: () -> Unit, modifier: Modifier, compact: Boolean) {
    val border by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = WhfinMotion.quick(), label = "theme-choice")
    Surface(onClick = onSelect, modifier = modifier.testTag("theme-choice-$index").semantics {
        role = Role.RadioButton; selected = chosen
    }, shape = MaterialTheme.shapes.medium,
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else WhfinThemeTokens.raisedSurface,
        border = BorderStroke(if (chosen) 2.dp else 1.dp, border)) {
        if (compact) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppearanceMiniature(index, Modifier.width(72.dp).height(48.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2)
            ThemeCheck(chosen)
        } else Column(Modifier.fillMaxHeight().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppearanceMiniature(index, Modifier.fillMaxWidth().height(64.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.weight(1f))
            ThemeCheck(chosen)
        }
    }
}

@Composable
private fun ThemeCheck(chosen: Boolean) {
    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp),
        tint = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
}

@Composable
private fun AppearanceMiniature(mode: Int, modifier: Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(8.dp.toPx())
        fun preview(left: Float, width: Float, dark: Boolean) {
            val palette = if (dark) WhfinDarkColorScheme else WhfinLightColorScheme
            drawRoundRect(palette.background, Offset(left, 0f), Size(width, size.height), r)
            val x = left + width * .14f
            drawRoundRect(palette.primary, Offset(x, size.height * .17f), Size(width * .42f, size.height * .1f), CornerRadius(3.dp.toPx()))
            repeat(2) { row ->
                drawRoundRect(palette.surfaceContainerHighest, Offset(x, size.height * (.42f + row * .23f)),
                    Size(width * .72f, size.height * .12f), CornerRadius(3.dp.toPx()))
            }
        }
        if (mode == 0) { preview(0f, size.width / 2, false); preview(size.width / 2, size.width / 2, true) }
        else preview(0f, size.width, mode == 2)
    }
}
