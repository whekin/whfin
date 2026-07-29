package dev.whekin.whfin.ui.demo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.core.ui.WhfinWorkspaceStrip
import dev.whekin.whfin.ui.theme.WhfinTheme

private data class DemoWorkspaceUiState(
    val active: Boolean = false,
    val busy: Boolean = false,
    val problem: String? = null,
    val onUsePersonal: () -> Unit = {},
)

private val LocalDemoWorkspace = staticCompositionLocalOf { DemoWorkspaceUiState() }

@Composable
fun DemoWorkspaceProvider(
    active: Boolean,
    busy: Boolean,
    problem: String?,
    onUsePersonal: () -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDemoWorkspace provides DemoWorkspaceUiState(
            active = active,
            busy = busy,
            problem = problem,
            onUsePersonal = onUsePersonal,
        ),
        content = content,
    )
}

@Composable
fun DemoWorkspaceFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val workspace = LocalDemoWorkspace.current
    if (!workspace.active) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        WhfinWorkspaceStrip(
            title = stringResource(R.string.demo_workspace_title),
            supportingText = stringResource(R.string.demo_workspace_supporting),
            actionLabel = stringResource(R.string.demo_workspace_use_personal),
            onAction = workspace.onUsePersonal,
            icon = Icons.Default.Science,
            enabled = !workspace.busy,
            problem = workspace.problem,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoEntrySheet(
    busy: Boolean,
    problem: String?,
    onOpenDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DemoEntrySheetContent(
            busy = busy,
            problem = problem,
            onOpenDemo = onOpenDemo,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun DemoEntrySheetContent(
    busy: Boolean,
    problem: String?,
    onOpenDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WhfinThemeTokens.spacing.rail),
            verticalArrangement = Arrangement.spacedBy(WhfinThemeTokens.spacing.md),
        ) {
            Text(
                stringResource(R.string.demo_entry_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.demo_entry_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow(
                    title = stringResource(R.string.demo_entry_isolated_title),
                    supportingText = stringResource(R.string.demo_entry_isolated_body),
                    supportingMaxLines = 5,
                    icon = Icons.Default.PrivacyTip,
                    divider = true,
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.demo_entry_fresh_title),
                    supportingText = stringResource(R.string.demo_entry_fresh_body),
                    supportingMaxLines = 5,
                    icon = Icons.Default.Restore,
                )
            }
            if (problem != null) {
                WhfinNotice(
                    title = stringResource(R.string.demo_mode_problem_title),
                    body = problem,
                    kind = WhfinNoticeKind.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WhfinThemeTokens.spacing.rail,
                    vertical = WhfinThemeTokens.spacing.md,
                ),
        ) {
            WhfinButton(
                label = stringResource(
                    if (busy) R.string.demo_entry_opening else R.string.demo_entry_open,
                ),
                onClick = onOpenDemo,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Demo entry light", widthDp = 400, showBackground = true)
@Preview(
    name = "Demo entry dark",
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Demo entry font 1.5",
    widthDp = 400,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(name = "Demo entry compact", widthDp = 360, heightDp = 520, showBackground = true)
@Composable
private fun DemoEntryPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DemoEntrySheetContent(
                busy = false,
                problem = null,
                onOpenDemo = {},
                modifier = Modifier.padding(top = WhfinThemeTokens.spacing.md),
            )
        }
    }
}

@Preview(name = "Demo strip light", widthDp = 400, showBackground = true)
@Preview(
    name = "Demo strip dark",
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Demo strip font 1.5",
    widthDp = 400,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(name = "Demo strip compact", widthDp = 320, showBackground = true)
@Composable
private fun DemoWorkspaceStripPreview() {
    WhfinTheme {
        WhfinWorkspaceStrip(
            title = stringResource(R.string.demo_workspace_title),
            supportingText = stringResource(R.string.demo_workspace_supporting),
            actionLabel = stringResource(R.string.demo_workspace_use_personal),
            onAction = {},
            icon = Icons.Default.Science,
        )
    }
}
