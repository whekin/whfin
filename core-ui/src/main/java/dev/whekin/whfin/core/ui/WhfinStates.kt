package dev.whekin.whfin.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
fun WhfinWorkspaceStrip(
    title: String,
    supportingText: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    problem: String? = null,
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
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                    )
                    Text(
                        problem ?: supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (problem == null) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .72f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
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
 * The one switch WHFIN uses, in the same two states its pills already speak in.
 *
 * Material's own switch was the last stock control on the screen: a wide grey capsule with a large
 * floating thumb, drawn in a weight nothing else here uses. Beside a row of [WhfinFilterPill]s —
 * outline when off, filled when on — it read as a control borrowed from another app. This one says
 * the same two states in that same language: an empty outlined track, or a filled one.
 *
 * The thumb also changes size, not only place. Position alone is a weak signal at a glance and no
 * signal at all to someone who cannot separate the two track colours, and the extra weight arriving
 * with "on" is the cheapest second way to say it.
 */
@Composable
fun WhfinSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        animationSpec = WhfinMotion.quick(),
        label = "whfin-switch-track",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = WhfinMotion.quick(),
        label = "whfin-switch-border",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
        animationSpec = WhfinMotion.quick(),
        label = "whfin-switch-thumb",
    )
    // Pressed, the thumb stretches towards the side it is about to travel to, the way the pills
    // answer a press by changing shape. It is the only affordance a deliberately quiet palette has.
    val thumbWidth by animateDpAsState(
        targetValue = when {
            pressed -> SWITCH_THUMB_ON + 4.dp
            checked -> SWITCH_THUMB_ON
            else -> SWITCH_THUMB_OFF
        },
        animationSpec = WhfinMotion.standard(),
        label = "whfin-switch-thumb-width",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (checked) SWITCH_THUMB_ON else SWITCH_THUMB_OFF,
        animationSpec = WhfinMotion.standard(),
        label = "whfin-switch-thumb-height",
    )
    val travel = SWITCH_TRACK_WIDTH - thumbWidth - SWITCH_TRACK_INSET * 2
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = WhfinMotion.standard(),
        label = "whfin-switch-thumb-offset",
    )

    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { value ->
                    haptics.performHapticFeedback(WhfinHaptics.toggle(value))
                    onCheckedChange(value)
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .size(SWITCH_TRACK_WIDTH, SWITCH_TRACK_HEIGHT)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.5.dp, borderColor, CircleShape)
                .padding(SWITCH_TRACK_INSET),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(thumbWidth, thumbHeight)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

private val SWITCH_TRACK_WIDTH = 50.dp
private val SWITCH_TRACK_HEIGHT = 30.dp
private val SWITCH_TRACK_INSET = 4.dp
private val SWITCH_THUMB_OFF = 14.dp
private val SWITCH_THUMB_ON = 22.dp
private const val DISABLED_ALPHA = .38f

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
