package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

enum class WhfinNoticeKind { Info, Attention, Error, Unavailable }
enum class WhfinPaneState { Loading, Empty, Error, Unavailable }

@Composable
fun WhfinNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: WhfinNoticeKind = WhfinNoticeKind.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    dismissContentDescription: String? = null,
    onDismiss: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = when (kind) {
        WhfinNoticeKind.Info -> MaterialTheme.colorScheme.primary
        WhfinNoticeKind.Attention -> MaterialTheme.colorScheme.tertiary
        WhfinNoticeKind.Error -> MaterialTheme.colorScheme.error
        WhfinNoticeKind.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = .07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .35f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
                if (dismissIcon != null && dismissContentDescription != null && onDismiss != null) {
                    WhfinIconButton(
                        icon = dismissIcon,
                        contentDescription = dismissContentDescription,
                        onClick = onDismiss,
                        outlined = false,
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) WhfinButton(
                actionLabel,
                onAction,
                Modifier.fillMaxWidth(),
                style = if (kind == WhfinNoticeKind.Error) WhfinActionStyle.Destructive else WhfinActionStyle.Primary,
            )
        }
    }
}

@Composable
fun WhfinSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(WhfinHaptics.toggle(enabled))
                onCheckedChange(enabled)
            },
            enabled = enabled,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
fun WhfinStatePane(
    state: WhfinPaneState,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state == WhfinPaneState.Loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) WhfinButton(
            actionLabel,
            onAction,
            style = if (state == WhfinPaneState.Error) WhfinActionStyle.Secondary else WhfinActionStyle.Primary,
        )
    }
}
