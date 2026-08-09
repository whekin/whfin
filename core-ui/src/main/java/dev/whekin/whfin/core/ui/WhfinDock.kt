package dev.whekin.whfin.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
data class WhfinDockDestination(
    val icon: ImageVector,
    val label: String,
    val selectedIcon: ImageVector = icon,
    val testTag: String? = null,
)

/**
 * The primary WHFIN shell: two stable destinations and one independent create action.
 *
 * The dock is deliberately grounded in the screen canvas. Selection is shown by a filled glyph,
 * stronger label and color. The create action shares the same visual rhythm without pretending to
 * be a destination.
 */
@Composable
fun WhfinDock(
    leading: WhfinDockDestination,
    trailing: WhfinDockDestination,
    selectedIndex: Int,
    addLabel: String,
    addContentDescription: String,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WhfinThemeTokens.spacing
    val sizes = WhfinThemeTokens.sizes
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.rail),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .heightIn(min = sizes.dockHeight)
                    .padding(horizontal = spacing.rail),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WhfinDockItem(
                    destination = leading,
                    selected = selectedIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(0) },
                )
                Surface(
                    onClick = onAdd,
                    modifier = Modifier
                        .width(sizes.dockCenterSlot)
                        .heightIn(min = sizes.dockHeight)
                        .testTag("dock-add")
                        .semantics { role = Role.Button },
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(sizes.ledgerMarker),
                        )
                        WhfinDockAddMark(
                            contentDescription = addContentDescription,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(sizes.dockIcon),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = addLabel,
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                WhfinDockItem(
                    destination = trailing,
                    selected = selectedIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(1) },
                )
            }
        }
    }
}

@Composable
private fun WhfinDockAddMark(
    contentDescription: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = 2.5.dp
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val inset = strokeWidth.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawLine(
            color = color,
            start = Offset(inset, center.y),
            end = Offset(size.width - inset, center.y),
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, inset),
            end = Offset(center.x, size.height - inset),
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WhfinDockItem(
    destination: WhfinDockDestination,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val sizes = WhfinThemeTokens.sizes
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = WhfinMotion.quick(),
        label = "dock content",
    )
    val taggedModifier = if (destination.testTag != null) {
        modifier.testTag(destination.testTag)
    } else {
        modifier
    }

    Surface(
        onClick = {
            if (!selected) haptics.performHapticFeedback(WhfinHaptics.navigation)
            onClick()
        },
        modifier = taggedModifier
            .heightIn(min = sizes.dockHeight)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizes.ledgerMarker),
            )
            Crossfade(
                targetState = selected,
                animationSpec = WhfinMotion.quick(),
                label = "dock icon emphasis",
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(sizes.dockIcon),
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 2.dp),
                style = if (selected) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
