package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stable search/context above one lazy catalogue. The feature owns selection and writes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhfinPickerSheet(
    title: String, context: String?, query: String, searchHint: String, clearDescription: String,
    onQuery: (String) -> Unit, onDismiss: () -> Unit,
    busy: Boolean = false, problem: String? = null,
    createDescription: String? = null, onCreate: (() -> Unit)? = null,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val currentBusy by rememberUpdatedState(busy)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        onDismissRequest = { if (!currentBusy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !currentBusy }),
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy, shouldDismissOnClickOutside = !busy),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { WhfinSheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    context?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (onCreate != null && createDescription != null) WhfinIconButton(Icons.Default.Add, createDescription,
                    { focus.clearFocus(); keyboard?.hide(); onCreate() }, outlined = false, enabled = !busy)
                WhfinIconButton(Icons.Default.Close, stringResource(R.string.whfin_close), onDismiss, outlined = false, enabled = !busy)
            }
            WhfinField(query, onQuery, null, placeholder = searchHint, leadingIcon = Icons.Default.Search,
                enabled = !busy, imeAction = ImeAction.Search,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                trailingIcon = { if (query.isNotEmpty()) WhfinIconButton(Icons.Default.Close, clearDescription,
                    { onQuery("") }, outlined = false, enabled = !busy) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
            LazyColumn(listModifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
internal fun WhfinSheetDragHandle() {
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(32.dp, 4.dp), shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.onSurfaceVariant) {}
    }
}
