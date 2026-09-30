package dev.whekin.whfin.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import dev.whekin.whfin.core.ui.WhfinFormSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.ui.demo.isDemoWorkspaceActive

@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    busy: Boolean = false,
    dirty: Boolean = false,
    scrollable: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var discard by rememberSaveable { mutableStateOf(false) }
    val protectDraft = dirty && !isDemoWorkspaceActive()
    val close = { if (!busy) { if (protectDraft) discard = true else onDismiss() } }
    WhfinFormSheet(title, close, primaryLabel, primaryEnabled, onPrimary,
        busy = busy, dismissAllowed = !protectDraft, scrollable = scrollable, footer = footer, content = content)
    if (discard) WhfinConfirmDialog(
        title = stringResource(R.string.form_discard_title),
        body = stringResource(R.string.discard_transaction_body),
        confirmLabel = stringResource(R.string.discard_action),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = { discard = false; onDismiss() },
        onDismiss = { discard = false },
    )
}
