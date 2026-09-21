package dev.whekin.whfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    /**
     * A second way out, named.
     *
     * Some notices can be set aside rather than answered, and an unlabelled cross in the corner is
     * not how a reader learns that: it reads as "close this card", which is a guess about whether
     * the thing comes back. A word says what happens.
     */
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
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
            if (actionLabel != null && onAction != null) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhfinButton(
                            secondaryActionLabel,
                            onSecondaryAction,
                            Modifier.weight(1f),
                            style = WhfinActionStyle.Secondary,
                        )
                        WhfinButton(
                            actionLabel,
                            onAction,
                            Modifier.weight(1f),
                            style = if (kind == WhfinNoticeKind.Error) WhfinActionStyle.Destructive
                            else WhfinActionStyle.Primary,
                        )
                    }
                } else {
                    WhfinButton(
                        actionLabel,
                        onAction,
                        Modifier.fillMaxWidth(),
                        style = if (kind == WhfinNoticeKind.Error) WhfinActionStyle.Destructive
                        else WhfinActionStyle.Primary,
                    )
                }
            }
        }
    }
}

@Composable
fun WhfinWorkspaceStrip(
    title: String,
    supportingText: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    problem: String? = null,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = WhfinThemeTokens.sizes.minTouchTarget)
                    .padding(start = WhfinThemeTokens.spacing.rail, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                // Usually one line. At larger font scales the workspace identity must wrap
                // completely; silently losing "data" makes the remaining label meaningless.
                Column(Modifier.weight(1f)) {
                    Text(
                        if (problem == null && !compact) "$title · $supportingText" else title,
                        modifier = if (compact) Modifier.semantics { contentDescription = "$title · $supportingText" } else Modifier,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = Int.MAX_VALUE,
                    )
                    if (problem != null) Text(
                        problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                }
                WhfinButton(
                    label = actionLabel,
                    onClick = onAction,
                    enabled = enabled,
                    style = WhfinActionStyle.Quiet,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Native switch interaction with WHFIN colours. A check and thumb position identify "on" without
 * relying on colour. Pass a null callback when the containing row owns the toggle semantics.
 */
@Composable
fun WhfinSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { value ->
                haptics.performHapticFeedback(WhfinHaptics.toggle(value))
                change(value)
            }
        },
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (onCheckedChange != null) Modifier.semantics {
                this.contentDescription = contentDescription
            } else Modifier),
        thumbContent = if (checked) {
            {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}


/**
 * The one shape WHFIN uses to say "working".
 *
 * Material's expressive loading indicator morphs between rounded polygons instead of sweeping an
 * arc; it is the platform's current answer, and using it keeps a waiting WHFIN screen looking like
 * a waiting Android screen. It needs a little more room than a hairline spinner, so callers size it
 * rather than shrinking it below the shapes' legibility.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhfinLoadingIndicator(
    modifier: Modifier = Modifier,
    // Waiting is not an accent moment: the indicator is a filled shape, and in the primary colour
    // it outweighed the sentence next to it. It states its presence in the quiet ink the rest of
    // the supporting text uses.
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    LoadingIndicator(modifier = modifier, color = color)
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
        if (state == WhfinPaneState.Loading) WhfinLoadingIndicator(Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) WhfinButton(
            actionLabel,
            onAction,
            style = if (state == WhfinPaneState.Error) WhfinActionStyle.Secondary else WhfinActionStyle.Primary,
        )
    }
}
