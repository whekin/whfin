package dev.whekin.whfin.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import dev.whekin.whfin.core.ui.WhfinFormSheet

@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = WhfinFormSheet(title, onDismiss, primaryLabel, primaryEnabled, onPrimary, content)
