package dev.whekin.whfin.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class WhfinActionStyle { Primary, Secondary, Quiet, Destructive, DestructiveSecondary }

private val LocalProminentIconButtons = staticCompositionLocalOf { false }

@Composable
fun WhfinButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: WhfinActionStyle = WhfinActionStyle.Primary,
    leadingIcon: ImageVector? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, maxLines = 2)
    }
    val sized = modifier.heightIn(min = WhfinThemeTokens.sizes.buttonHeight)
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberWhfinPressShape(interactionSource, WhfinThemeTokens.sizes.buttonCorner)
    when (style) {
        WhfinActionStyle.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            content = content,
        )
        WhfinActionStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            content = content,
        )
        WhfinActionStyle.Quiet -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            content = content,
        )
        WhfinActionStyle.Destructive -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            content = content,
        )
        WhfinActionStyle.DestructiveSecondary -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            content = content,
        )
    }
}

@Composable
fun WhfinConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: WhfinActionStyle = WhfinActionStyle.Destructive,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            WhfinConfirmDialogContent(
                title = title,
                body = body,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                confirmStyle = confirmStyle,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun WhfinActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
internal fun WhfinConfirmDialogContent(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: WhfinActionStyle = WhfinActionStyle.Destructive,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val consequential = confirmStyle == WhfinActionStyle.Destructive ||
        confirmStyle == WhfinActionStyle.DestructiveSecondary
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 440.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(
                    Modifier
                        .width(4.dp)
                        .height(52.dp)
                        .background(
                            if (consequential) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                            CircleShape,
                        ),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
            HorizontalDivider(
                Modifier.padding(top = 20.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhfinButton(
                        label = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        style = WhfinActionStyle.Secondary,
                    )
                    WhfinButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        style = confirmStyle,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WhfinButton(
                        label = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        style = WhfinActionStyle.Secondary,
                    )
                    WhfinButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        style = confirmStyle,
                    )
                }
            }
        }
    }
}

@Composable
fun WhfinIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
    selected: Boolean = false,
    enabled: Boolean = true,
    prominent: Boolean? = null,
    style: WhfinActionStyle = WhfinActionStyle.Quiet,
) {
    val useProminentIcon = prominent ?: LocalProminentIconButtons.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(WhfinThemeTokens.sizes.minTouchTarget),
        shape = CircleShape,
        color = when {
            !selected || !enabled -> Color.Transparent
            style == WhfinActionStyle.Destructive || style == WhfinActionStyle.DestructiveSecondary ->
                MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            style == WhfinActionStyle.Destructive || style == WhfinActionStyle.DestructiveSecondary ->
                if (selected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (outlined) BorderStroke(
            1.dp,
            if (style == WhfinActionStyle.Destructive || style == WhfinActionStyle.DestructiveSecondary) {
                MaterialTheme.colorScheme.error.copy(alpha = .45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription,
                modifier = Modifier.size(
                    if (useProminentIcon) WhfinThemeTokens.sizes.prominentIcon else WhfinThemeTokens.sizes.icon,
                ),
            )
        }
    }
}

/**
 * The single visual treatment for hierarchical Back navigation across WHFIN.
 * Dismiss/close actions intentionally use their own icon and semantics.
 */
@Composable
fun WhfinBackButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    WhfinIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        outlined = false,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhfinContextHeader(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSymbol: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Optional tap on the metric itself, e.g. to read the same money in another currency. */
    onValueClick: (() -> Unit)? = null,
    valueClickLabel: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column(
                    Modifier
                        .padding(start = 4.dp, top = 4.dp)
                        .then(
                            if (onValueClick == null) Modifier
                            else Modifier.clickable(onClickLabel = valueClickLabel, onClick = onValueClick),
                        ),
                ) {
                    WhfinAmount(
                        text = value,
                        symbol = valueSymbol,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                CompositionLocalProvider(LocalProminentIconButtons provides true) {
                    Surface(
                        modifier = Modifier.padding(end = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions,
                        )
                    }
                }
            },
            expandedHeight = 84.dp,
            windowInsets = TopAppBarDefaults.windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            scrollBehavior = scrollBehavior,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = WhfinThemeTokens.spacing.rail),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Keeps system status icons legible while a primary screen scrolls edge-to-edge.
 * This deliberately covers only the status-bar inset and must not become a sticky toolbar.
 */
@Composable
fun WhfinStatusBarProtection(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background),
    )
}

@Immutable
data class WhfinDistributionSegment(
    val weight: Float,
    val color: Color,
)

/**
 * A restrained category distribution ring. Labels and interaction stay in the adjacent ledger rows;
 * the ring is one accessible summary, so small segments never create unusable touch targets.
 */
@Composable
fun WhfinDonutChart(
    segments: List<WhfinDistributionSegment>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    centerContent: @Composable () -> Unit = {},
) {
    val visible = segments.filter { it.weight > 0f }
    val total = visible.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .size(196.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 24.dp.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
            )
            if (visible.isNotEmpty()) {
                val gap = if (visible.size == 1) 0f else 2.5f
                val availableSweep = 360f - gap * visible.size
                var start = -90f
                visible.forEachIndexed { index, segment ->
                    val sweep = availableSweep * (segment.weight / total)
                    drawArc(
                        color = if (selectedIndex == null || selectedIndex == index) {
                            segment.color
                        } else {
                            segment.color.copy(alpha = .24f)
                        },
                        startAngle = start,
                        sweepAngle = sweep.coerceAtLeast(0f),
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                    )
                    start += sweep + gap
                }
            }
        }
        centerContent()
    }
}

@Immutable
data class WhfinMonthlyBar(
    val label: String,
    val value: Long,
    val amountDescription: String,
    val selected: Boolean = false,
    val periodDescription: String = label,
)

/**
 * A compact, selectable period comparison with accessible 48 dp month targets.
 *
 * A closed period — especially a calendar year — is one shape the reader is meant to take in at
 * once, so [fitToWidth] divides the available width instead of asking the reader to scroll a year
 * they can already name. Screens using that dense mode must also provide arrow navigation.
 */
@Composable
fun WhfinMonthlyBarChart(
    bars: List<WhfinMonthlyBar>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    onBarClick: ((Int) -> Unit)? = null,
    fitToWidth: Boolean = false,
) {
    val maximum = bars.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val chartModifier = modifier.fillMaxWidth().height(148.dp)
    if (fitToWidth) {
        Row(
            modifier = chartModifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, bar ->
                MonthlyBar(
                    bar = bar,
                    index = index,
                    maximum = maximum,
                    color = color,
                    onBarClick = onBarClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedIndex = bars.indexOfFirst { it.selected }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        modifier = chartModifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(bars) { index, bar ->
            MonthlyBar(
                bar = bar,
                index = index,
                maximum = maximum,
                color = color,
                onBarClick = onBarClick,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}

@Composable
private fun MonthlyBar(
    bar: WhfinMonthlyBar,
    index: Int,
    maximum: Long,
    color: Color,
    onBarClick: ((Int) -> Unit)?,
    modifier: Modifier,
) {
    // Столбец должен читаться как столбец: узкая 9dp полоса внутри 48dp слота выглядела
    // палочкой-засечкой. Пустой месяц рисуется базовой чертой, а не почти невидимым
    // огрызком столбца.
    val fraction = (bar.value.toFloat() / maximum).coerceIn(0f, 1f)
    // Height carries the comparison, so it settles with a spring: switching year or period reads
    // as the same bars moving, not as a different chart appearing.
    val grown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = WhfinMotion.standard(),
        label = "monthly bar",
    )
    val itemModifier = modifier
        .fillMaxHeight()
        .testTag("whfin-monthly-bar-$index")
        .semantics(mergeDescendants = true) {
            contentDescription = "${bar.periodDescription}, ${bar.amountDescription}"
            selected = bar.selected
        }
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                if (bar.value <= 0L) Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ) else Box(
                    Modifier
                        // A column has to read as a column: a hairline inside a wide slot reads as
                        // a tick mark, and twelve of them read as a ruler rather than a year.
                        .fillMaxWidth(if (bar.selected) 1f else .84f)
                        .fillMaxHeight(grown.coerceAtLeast(0.02f))
                        .background(
                            if (bar.selected) color else color.copy(alpha = .38f),
                            RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp),
                        ),
                )
            }
            Text(
                bar.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (bar.selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (onBarClick != null) Surface(
        onClick = { onBarClick(index) },
        modifier = itemModifier,
        shape = MaterialTheme.shapes.small,
        color = if (bar.selected) color.copy(alpha = .08f) else Color.Transparent,
        content = content,
    ) else Box(
        modifier = itemModifier,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun WhfinDistributionBar(
    segments: List<WhfinDistributionSegment>,
    modifier: Modifier = Modifier,
) {
    val visible = segments.filter { it.weight > 0f }
    Row(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        visible.forEach { segment ->
            Spacer(
                Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(segment.color),
            )
        }
    }
}

@Composable
fun WhfinPrimaryIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun WhfinFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    /** Set when the pill is one segment of a row that divides the width between equal choices. */
    centered: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = WhfinThemeTokens.sizes.minTouchTarget),
        interactionSource = interactionSource,
        shape = rememberWhfinPressShape(interactionSource, WhfinThemeTokens.sizes.pillCorner),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier
                .then(if (centered) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) {
                Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(7.dp)
            },
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(17.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One-dimensional choice rail for labels that must remain readable in RU/EN and at large font scale.
 * The trailing inset intentionally leaves the next item partially visible as a scrolling cue.
 */
@Composable
fun WhfinChoiceRail(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(end = 28.dp),
    itemSpacing: Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content,
    )
}

@Composable
fun WhfinSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.1.sp,
    )
}

/**
 * Подпись поля или подраздела. Отличается от [WhfinSectionLabel] намеренно: капс с трекингом —
 * ярлык книги (день, раздел экрана), и когда им подписан каждый второй блок, экран начинает
 * телеграфировать. Для служебных подписей внутри блока нужен тихий регистр.
 */
@Composable
fun WhfinFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Бухгалтерская итоговая черта: две тонкие линии под результатом. Типографский приём книги,
 * а не имитация бумаги — одинарная линейка остаётся обычным разделителем.
 */
@Composable
fun WhfinTotalRule(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Денежная сумма в редакционном (серифном) регистре с табличными цифрами: числовой столбец
 * получает собственный голос и не сливается с sans-текстом строки. Валютный символ набирается
 * тише и мельче, чтобы цифры выстраивались в колонку.
 *
 * Форматирование остаётся в app-слое: сюда приходит готовая строка и символ, который нужно
 * приглушить. Семантика цвета задаётся вызывающим.
 */
@Composable
fun WhfinAmount(
    text: String,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
    color: Color = Color.Unspecified,
    /**
     * Bundled Noto Serif имеет единственное начертание, поэтому по умолчанию вес нормальный:
     * запрос SemiBold дал бы синтетический faux-bold. Присутствие суммы задаётся кеглем и цветом.
     */
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolved = color.takeOrElse { LocalContentColor.current }
    // lastIndexOf: у валюты без своего знака символ — это код («1 000.00 AMD»), и такой же код
    // может стоять подписью перед суммой. Тише набираем именно тот, что стоит при числе.
    val symbolRange = symbol?.let { value ->
        text.lastIndexOf(value).takeIf { it >= 0 }?.let { it..(it + value.length) }
    }
    val annotated = buildAnnotatedString {
        append(text)
        if (symbolRange != null) addStyle(
            SpanStyle(
                // Тише, но не бледнее необходимого: символ валюты остаётся читаемым,
                // просто перестаёт спорить с цифрами за внимание.
                fontSize = style.fontSize * .82f,
                color = resolved.copy(alpha = .8f),
            ),
            symbolRange.first,
            symbolRange.last,
        )
    }
    Text(
        annotated,
        modifier,
        // Редакционная гарнитура берётся из темы, поэтому `Device font` переключает и суммы.
        style = style.copy(
            fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
            fontFeatureSettings = "tnum",
        ),
        color = resolved,
        fontWeight = fontWeight,
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = overflow,
    )
}

@Composable
fun WhfinSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (supportingText != null) Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun WhfinLedgerGroup(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (tonal) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
fun WhfinLedgerRow(
    title: String,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2,
    supportingText: String? = null,
    supportingMaxLines: Int = 2,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    markerColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth().then(
        if (onClick != null) Modifier.clickable(interactionSource, indication = null, onClick = onClick) else Modifier,
    )) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (markerColor != null) Spacer(
                Modifier.width(WhfinThemeTokens.sizes.ledgerMarker).height(36.dp)
                    .background(markerColor, CircleShape),
            )
            if (icon != null) Box(
                Modifier.size(WhfinThemeTokens.sizes.iconContainer)
                    .background(iconTint.copy(alpha = .11f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = supportingMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) trailing()
        }
        if (divider) HorizontalDivider(
            Modifier.padding(start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
fun WhfinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics {
                if (!label.isNullOrBlank()) contentDescription = label
            },
            placeholder = placeholder?.let { { Text(it) } },
            suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null) } },
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .55f),
                errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f),
                focusedIndicatorColor = MaterialTheme.colorScheme.tertiary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
            ),
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhfinFormSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                WhfinButton(
                    label = primaryLabel,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    style = WhfinActionStyle.Quiet,
                )
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}
