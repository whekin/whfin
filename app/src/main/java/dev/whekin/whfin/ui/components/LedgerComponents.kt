package dev.whekin.whfin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinSectionLabel

@Composable
fun LedgerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    WhfinButton(label, onClick, modifier, enabled)
}

@Composable
fun LedgerIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WhfinIconButton(icon, contentDescription.orEmpty(), onClick, modifier)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    WhfinSectionLabel(text, modifier)
}
