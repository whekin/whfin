package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shape of what is coming, shown while the database is still answering.
 *
 * A screen that fills in with real numbers a moment later should not first assert something else. The
 * old alternative — printing the placeholder a `StateFlow` starts on — said `0.00` and a total of
 * nothing, which reads as a fact rather than as a wait. Blocks in the layout's own rhythm say the same
 * thing without claiming an amount.
 *
 * The whole skeleton breathes as one, quietly: a moving highlight would be decoration, and this
 * palette says "working" with the same tonal ink it uses everywhere else.
 */
@Composable
fun WhfinSkeleton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = .55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-breath",
    )
    Column(
        modifier
            .alpha(breath)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(WhfinThemeTokens.spacing.sm),
        content = content,
    )
}

/** One placeholder bar, sized where a number or a line of text will land. */
@Composable
fun WhfinSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    corner: Dp = 6.dp,
) {
    Box(
        modifier
            .height(height)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .09f), RoundedCornerShape(corner)),
    )
}

/**
 * The silhouette of a ledger row: category mark, two lines of identity, an amount on the right.
 *
 * Kept here rather than in a feature so a loading feed, a loading account list and a loading queue
 * cannot each invent their own idea of what a row looks like.
 */
@Composable
fun WhfinSkeletonLedgerRow(
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = WhfinThemeTokens.sizes.minTouchTarget)
            .padding(horizontal = WhfinThemeTokens.spacing.md, vertical = WhfinThemeTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WhfinThemeTokens.spacing.sm),
    ) {
        if (showIcon) Box(
            Modifier
                .size(WhfinThemeTokens.sizes.iconContainer)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .09f), CircleShape),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(WhfinThemeTokens.spacing.xxs),
        ) {
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.55f), height = 15.dp)
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.35f), height = 11.dp)
        }
        WhfinSkeletonBlock(Modifier.width(64.dp), height = 15.dp)
    }
}
