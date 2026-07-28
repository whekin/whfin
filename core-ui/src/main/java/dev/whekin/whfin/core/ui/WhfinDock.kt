package dev.whekin.whfin.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Immutable
data class WhfinDockDestination(
    val icon: ImageVector,
    val label: String,
    val testTag: String? = null,
)

/**
 * The primary WHFIN shell: two stable destinations and one independent create action.
 *
 * The dock is deliberately grounded in the screen canvas. Selection is shown by a short ledger
 * rule, while the filled center action carries the only strong visual emphasis.
 */
@Composable
fun WhfinDock(
    leading: WhfinDockDestination,
    trailing: WhfinDockDestination,
    selectedIndex: Int,
    addIcon: ImageVector,
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
                Box(
                    modifier = Modifier
                        .width(sizes.dockCenterSlot)
                        .heightIn(min = sizes.dockHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = onAdd,
                        modifier = Modifier
                            .size(sizes.minTouchTarget)
                            .offset(y = -spacing.xxs)
                            .testTag("dock-add"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = addIcon,
                                contentDescription = addContentDescription,
                                modifier = Modifier.size(24.dp),
                            )
                        }
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
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = WhfinMotion.quick(),
        label = "dock indicator",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizes.ledgerMarker),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(
                    Modifier
                        .width(sizes.dockIndicatorWidth)
                        .height(2.dp)
                        .background(indicatorColor, CircleShape),
                )
            }
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(sizes.dockIcon),
                tint = contentColor,
            )
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
