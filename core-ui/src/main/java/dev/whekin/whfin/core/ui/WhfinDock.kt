package dev.whekin.whfin.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Immutable
data class WhfinDockDestination(
    val icon: ImageVector,
    val label: String,
    val selectedIcon: ImageVector = icon,
    val testTag: String? = null,
)

/**
 * The primary WHFIN shell: the app's stable destinations and one independent create action.
 *
 * The dock is deliberately grounded in the screen canvas. Selection is shown by a filled glyph,
 * stronger label and color, plus one thin ledger rule that travels between the destinations.
 *
 * The create action does not borrow their shape. With two destinations, standing between them was
 * enough to say "not one of you"; with four it read as a fifth section — same slot, same glyph
 * weight, same label underneath. It is a control, so it is given the one thing no destination has:
 * a surface. A filled disc with no label cannot be mistaken for a place, and the destinations keep
 * the labels, the rule and the flat ground to themselves.
 *
 * [selection] is a position, not an index, so the rule can travel rather than blink: the caller
 * animates it, and it may sit between two items while the change is happening.
 */
@Composable
fun WhfinDock(
    destinations: List<WhfinDockDestination>,
    selection: Float,
    addContentDescription: String,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(destinations.isNotEmpty()) { "A dock without destinations is not a dock" }
    val position = selection.coerceIn(0f, (destinations.size - 1).toFloat())
    val addAfter = destinations.size / 2
    val spacing = WhfinThemeTokens.spacing
    val sizes = WhfinThemeTokens.sizes
    // Geometry answers the reader's text size before the text does. Four names and a control share
    // one phone width, so at a large scale the dock first spends its own margins and the air between
    // the slots — everything that is not a letter — and only then considers the letters.
    val fontScale = LocalDensity.current.fontScale
    val roomy = fontScale <= COMPACT_SCALE
    val railPadding = if (roomy) spacing.rail else spacing.xs
    val itemGap = if (roomy) spacing.xs else 0.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.rail),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slotWidth = (
                    maxWidth - railPadding * 2 - sizes.dockAction - itemGap * destinations.size
                    ) / destinations.size - DOCK_ITEM_PADDING * 2
                val labelSize = dockLabelSize(destinations, slotWidth)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .heightIn(min = sizes.dockHeight)
                        .padding(horizontal = railPadding),
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.take(addAfter).forEachIndexed { index, destination ->
                        WhfinDockItem(
                            destination = destination,
                            emphasis = dockEmphasis(position, index),
                            labelSize = labelSize,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(index) },
                        )
                    }
                    WhfinDockAction(
                        contentDescription = addContentDescription,
                        onClick = onAdd,
                    )
                    destinations.drop(addAfter).forEachIndexed { offset, destination ->
                        val index = addAfter + offset
                        WhfinDockItem(
                            destination = destination,
                            emphasis = dockEmphasis(position, index),
                            labelSize = labelSize,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One type size for every dock label, chosen from the widest name and the room it actually has.
 *
 * A blanket cap on the text scale shrank names that fit perfectly well; per-label auto-sizing would
 * set four different sizes in one row of furniture. The longest name decides for all of them, and
 * the floor is the size the dock renders at scale 1.0 — the labels never come out smaller than a
 * reader who asked for no enlargement already sees.
 */
@Composable
private fun dockLabelSize(destinations: List<WhfinDockDestination>, slotWidth: Dp): TextUnit {
    val base = MaterialTheme.typography.labelMedium.fontSize
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    if (base.isUnspecified || slotWidth <= 0.dp) return base
    return remember(destinations, slotWidth, density.density, density.fontScale, style) {
        // Measured in the style the dock actually draws — family, weight and letter spacing
        // included. A bare TextStyle(fontSize) measures a different typeface and reports room the
        // real one does not have, which is how a shorter name ended up as the ellipsised one.
        val widest = destinations.maxOf { destination ->
            measurer.measure(destination.label, style = style).size.width
        }
        val available = with(density) { slotWidth.toPx() }
        if (widest <= available) return@remember base
        // Advance width is very close to linear in point size, so one measurement is enough; the
        // margin absorbs the hinting that is not.
        val ratio = (available / widest) * DOCK_LABEL_SAFETY
        val floor = 1f / density.fontScale.coerceAtLeast(1f)
        base * ratio.coerceIn(floor.coerceAtMost(1f), 1f)
    }
}

/**
 * How much of the travelling rule belongs to one item.
 *
 * Full at its own index, gone one place away, and shared in between, so a change in destination
 * reads as one mark moving rather than two marks blinking.
 */
private fun dockEmphasis(position: Float, index: Int): Float =
    (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)

/**
 * The create action: the only thing in the dock that is pressed rather than gone to.
 *
 * It carries no label because the disc already says it is a control, and a word underneath was
 * exactly what made it read as a fifth destination. The name lives in the content description,
 * where a screen reader still announces it as a button.
 */
@Composable
private fun WhfinDockAction(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val sizes = WhfinThemeTokens.sizes
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(sizes.dockAction)
            .heightIn(min = sizes.dockHeight)
            .testTag("dock-add")
            .semantics { role = Role.Button },
        color = Color.Transparent,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(sizes.dockActionMark),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WhfinDockAddMark(
                        contentDescription = contentDescription,
                        modifier = Modifier.size(sizes.dockIcon),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
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

/**
 * One destination.
 *
 * [emphasis] runs from 0 (the other page is showing) to 1 (this one is), and every signal reads it
 * directly instead of animating towards a boolean: mid-swipe the dock has to be able to say "half
 * way", which a target-based animation cannot.
 */
@Composable
private fun WhfinDockItem(
    destination: WhfinDockDestination,
    emphasis: Float,
    labelSize: TextUnit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val sizes = WhfinThemeTokens.sizes
    val selected = emphasis > .5f
    val contentColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        emphasis,
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
            // Four names and the create action share the width, so the slot spends its room on the
            // words rather than on air beside them.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DOCK_ITEM_PADDING, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The rule the ledger draws over the column being read. It grows out of the item the
            // swipe is heading for and drains from the one being left, so the pair reads as one
            // mark travelling rather than two marks blinking.
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(sizes.dockRule * emphasis)
                        .height(sizes.ledgerMarker)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = emphasis),
                            CircleShape,
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(sizes.dockIcon),
                contentAlignment = Alignment.Center,
            ) {
                // Both glyphs are drawn and cross-faded by the same fraction: an outline that
                // becomes filled only after the page has settled would answer the gesture late.
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 1f - emphasis),
                )
                Icon(
                    imageVector = destination.selectedIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = emphasis),
                )
            }
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = labelSize,
                    fontWeight = lerp(FontWeight.Medium, FontWeight.SemiBold, emphasis),
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Above this text scale the dock gives up its margins and the gaps between slots to the words. */
private const val COMPACT_SCALE = 1.15f

/** Room kept beside a label so hinting cannot push the last glyph into an ellipsis. */
private const val DOCK_LABEL_SAFETY = .94f

private val DOCK_ITEM_PADDING = 2.dp
