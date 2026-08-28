package dev.whekin.whfin.ui.savings

import dev.whekin.whfin.ui.bank.SupportedBankApp
import dev.whekin.whfin.ui.bank.launchBank

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinDistributionBar
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinSkeleton
import dev.whekin.whfin.core.ui.WhfinSkeletonBlock
import dev.whekin.whfin.core.ui.WhfinSkeletonLedgerRow
import dev.whekin.whfin.core.ui.WhfinSavingsBalanceChart
import dev.whekin.whfin.core.ui.WhfinSavingsBalancePoint
import dev.whekin.whfin.core.ui.WhfinSavingsPaceBar
import dev.whekin.whfin.core.ui.WhfinSavingsPaceChart
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.isDemoWorkspaceActive
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class SavingsChartMode { Pace, Balance, Projection }
private enum class SavingsChartRange { Year, All }

@Composable
fun SavingsRoute(viewModel: SavingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val demoWorkspace = isDemoWorkspaceActive()
    SavingsScreen(
        data = state?.let { if (demoWorkspace) it.copy(bankApps = emptyList()) else it },
        onSavePlan = viewModel::savePlan,
        onClearPlan = viewModel::clearPlan,
        onSelectCurrency = viewModel::selectCurrency,
        onOpenBank = context::launchBank,
    )
}

@Composable
internal fun SavingsScreen(
    data: SavingsScreenData?,
    onSavePlan: (Long, Long?, LocalDate?) -> Unit,
    onClearPlan: () -> Unit,
    onSelectCurrency: (String) -> Unit = {},
    onOpenBank: (SupportedBankApp) -> Boolean = { false },
) {
    var editingPlan by rememberSaveable { mutableStateOf(false) }
    if (data == null) {
        WhfinSkeleton(contentDescription = stringResource(R.string.savings_loading), modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.35f), height = 14.dp)
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.7f).padding(top = 12.dp), height = 48.dp)
            repeat(3) { WhfinSkeletonLedgerRow() }
        }
        return
    }
    if (!data.hasReserve) {
        WhfinStatePane(
            state = WhfinPaneState.Unavailable,
            title = stringResource(R.string.savings_no_reserve_title),
            body = stringResource(R.string.savings_no_reserve_body),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    SavingsContent(data = data, onEditPlan = { editingPlan = true }, onSelectCurrency = onSelectCurrency, onOpenBank = onOpenBank)

    if (editingPlan) SavingsPlanEditor(
        plan = data.currentPlan,
        currency = data.currency,
        balanceMinor = data.currentReserveMinor,
        onDismiss = { editingPlan = false },
        onSave = { monthly, goal, goalBy ->
            onSavePlan(monthly, goal, goalBy)
            editingPlan = false
        },
        onClear = data.currentPlan?.let {
            {
                onClearPlan()
                editingPlan = false
            }
        },
    )
}

@Composable
private fun SavingsContent(
    data: SavingsScreenData,
    onEditPlan: () -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenBank: (SupportedBankApp) -> Boolean,
) {
    var modeName by rememberSaveable { mutableStateOf(if (data.currentPlan != null) SavingsChartMode.Projection.name else SavingsChartMode.Pace.name) }
    var rangeName by rememberSaveable { mutableStateOf(SavingsChartRange.Year.name) }
    LaunchedEffect(data.currentPlan?.id) {
        modeName = if (data.currentPlan != null) SavingsChartMode.Projection.name else SavingsChartMode.Pace.name
    }
    val mode = SavingsChartMode.valueOf(modeName).let {
        if (it == SavingsChartMode.Projection && data.currentPlan == null) SavingsChartMode.Pace else it
    }
    val range = SavingsChartRange.valueOf(rangeName)
    val shownMonths = when (range) {
        SavingsChartRange.Year -> data.months.takeLast(12)
        SavingsChartRange.All -> data.months
    }
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize().testTag("savings-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = navigationBottom + 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "reserve") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.availableCurrencies.size > 1) WhfinChoiceRail {
                data.availableCurrencies.forEach { currency ->
                    item {
                        WhfinFilterPill(currency, currency == data.currency, { onSelectCurrency(currency) })
                    }
                }
            }
            ReserveSummary(data)
            }
        }
        item(key = "plan") {
            SavingsPlanSummary(data, onEditPlan)
        }
        if (data.bankApps.isNotEmpty()) item(key = "bank-apps") {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                data.bankApps.forEachIndexed { index, app ->
                    WhfinLedgerRow(
                        title = stringResource(R.string.savings_open_bank, app.displayName),
                        supportingText = stringResource(R.string.savings_open_bank_hint),
                        icon = Icons.Outlined.AccountBalance,
                        onClick = { onOpenBank(app) },
                        divider = index < data.bankApps.lastIndex,
                    )
                }
            }
        }
        item(key = "chart-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhfinFilterPill(
                        label = stringResource(R.string.savings_view_pace),
                        selected = mode == SavingsChartMode.Pace,
                        onClick = { modeName = SavingsChartMode.Pace.name },
                        centered = true,
                        modifier = Modifier.weight(1f),
                    )
                    WhfinFilterPill(
                        label = stringResource(R.string.savings_view_balance),
                        selected = mode == SavingsChartMode.Balance,
                        onClick = { modeName = SavingsChartMode.Balance.name },
                        centered = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (data.currentPlan != null) WhfinFilterPill(
                        label = stringResource(R.string.savings_view_projection),
                        selected = mode == SavingsChartMode.Projection,
                        onClick = { modeName = SavingsChartMode.Projection.name },
                        centered = true, modifier = Modifier.weight(1f),
                    )
                }
                if (mode != SavingsChartMode.Projection) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhfinFilterPill(
                        label = stringResource(R.string.savings_range_year),
                        selected = range == SavingsChartRange.Year,
                        onClick = { rangeName = SavingsChartRange.Year.name },
                        centered = true,
                        modifier = Modifier.weight(1f),
                    )
                    WhfinFilterPill(
                        label = stringResource(R.string.savings_range_all),
                        selected = range == SavingsChartRange.All,
                        onClick = { rangeName = SavingsChartRange.All.name },
                        centered = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "chart-$mode-$range") {
            if (mode == SavingsChartMode.Projection && data.currentPlan != null) {
                SavingsProjectionPanel(data.currentReserveMinor, data.currentPlan.monthlyTargetMinor, data.currency,
                    data.currentPlan.goalMinor, data.currentPlan.goalBy?.let(LocalDate::ofEpochDay), LocalDate.now(), data.months)
            } else SavingsChart(data, shownMonths, mode, range)
        }
        item(key = "pace-reading") {
            PaceReading(data)
        }
    }
}

@Composable
private fun ReserveSummary(data: SavingsScreenData) {
    val plan = data.currentPlan
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhfinFieldLabel(stringResource(R.string.savings_reserve_label))
        WhfinAmount(
            formatMinor(data.currentReserveMinor, data.currency),
            symbol = currencySymbol(data.currency),
            style = MaterialTheme.typography.displayMedium,
        )
        plan?.goalMinor?.let { goal ->
            val remaining = (goal - data.currentReserveMinor).coerceAtLeast(0L)
            Text(
                stringResource(R.string.savings_goal_progress, formatMinor(goal, data.currency),
                    (data.currentReserveMinor.toDouble() / goal * 100).toInt().coerceAtLeast(0)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (remaining == 0L) stringResource(R.string.savings_goal_reached)
                else stringResource(R.string.savings_goal_remaining, formatMinor(remaining, data.currency)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhfinDistributionBar(
                segments = listOf(
                    WhfinDistributionSegment(data.currentReserveMinor.coerceAtLeast(0L).toFloat(), MaterialTheme.colorScheme.primary),
                    WhfinDistributionSegment(remaining.toFloat(), MaterialTheme.colorScheme.surfaceContainerHighest),
                ),
            )
        }
    }
}

@Composable
private fun SavingsPlanSummary(data: SavingsScreenData, onEditPlan: () -> Unit) {
    val plan = data.currentPlan
    if (plan == null) {
        WhfinNotice(
            title = stringResource(R.string.savings_plan_no_plan_title),
            body = stringResource(R.string.savings_plan_no_plan_body),
            icon = Icons.Default.Savings,
            kind = WhfinNoticeKind.Info,
            actionLabel = stringResource(R.string.savings_plan_add),
            onAction = onEditPlan,
        )
        return
    }
    val achieved = data.currentPaceMinor.coerceAtLeast(0L)
    val remaining = (plan.monthlyTargetMinor - achieved).coerceAtLeast(0L)
    WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WhfinFieldLabel(stringResource(R.string.savings_plan_title), Modifier.weight(1f))
                WhfinButton(
                    label = stringResource(R.string.action_edit),
                    onClick = onEditPlan,
                    style = WhfinActionStyle.Quiet,
                )
            }
            WhfinAmount(
                formatMinor(data.currentPaceMinor, data.currency, withSign = true),
                symbol = currencySymbol(data.currency),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(
                    R.string.savings_plan_target,
                    formatMinor(plan.monthlyTargetMinor, data.currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhfinDistributionBar(
                listOf(
                    WhfinDistributionSegment(achieved.toFloat(), MaterialTheme.colorScheme.primary),
                    WhfinDistributionSegment(remaining.toFloat(), MaterialTheme.colorScheme.surfaceContainerHighest),
                ),
            )
        }
    }
}

@Composable
private fun PaceReading(data: SavingsScreenData) {
    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WhfinFieldLabel(if (data.rollingMonthsIncluded == 0) stringResource(R.string.savings_average_no_history)
                    else stringResource(R.string.savings_average_months, data.rollingMonthsIncluded))
                WhfinAmount(
                    if (data.rollingMonthsIncluded == 0) "—" else formatMinor(data.rollingThreeMonthMinor, data.currency, withSign = true),
                    symbol = currencySymbol(data.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (data.currentPlan == null) {
                    WhfinFieldLabel(stringResource(R.string.savings_this_month))
                    WhfinAmount(formatMinor(data.currentPaceMinor, data.currency, withSign = true),
                        symbol = currencySymbol(data.currency), style = MaterialTheme.typography.titleMedium)
                } else {
                    WhfinFieldLabel(stringResource(R.string.savings_consistency))
                    Text(
                        if (data.evaluatedMonths == 0) stringResource(R.string.savings_consistency_none)
                        else stringResource(R.string.savings_consistency_value, data.monthsOnPace, data.evaluatedMonths),
                        style = if (data.evaluatedMonths == 0) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingsChart(
    data: SavingsScreenData,
    months: List<SavingsMonthUi>,
    mode: SavingsChartMode,
    range: SavingsChartRange,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val title = stringResource(
        if (mode == SavingsChartMode.Pace) R.string.savings_pace_chart_title
        else R.string.savings_balance_chart_title,
    )
    val body = stringResource(
        if (mode == SavingsChartMode.Pace) R.string.savings_pace_chart_body
        else R.string.savings_balance_chart_body,
    )
    val periodFormatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    // A newly declared plan still gives the history a useful reference, but it is explicitly the
    // current plan. Historical success is judged against each month's own version below.
    val commonTarget = data.currentPlan?.monthlyTargetMinor
    var selectedMonth by rememberSaveable(data.currency) { mutableStateOf(months.lastOrNull()?.month?.toString()) }
    val selectedIndex = months.indexOfFirst { it.month.toString() == selectedMonth }
        .takeIf { it >= 0 } ?: months.lastIndex
    val selected = months.getOrNull(selectedIndex)
    Column(Modifier.testTag("savings-chart"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinSectionHeader(title = title, supportingText = body)
        WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                if (mode == SavingsChartMode.Pace && commonTarget != null) Text(
                    stringResource(R.string.savings_current_target, formatMinor(commonTarget, data.currency)),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (mode) {
                    SavingsChartMode.Pace -> WhfinSavingsPaceChart(
                        bars = months.map { point ->
                            val targetSuffix = point.targetMinor?.takeIf { it != commonTarget }?.let { target ->
                                ", " + stringResource(
                                    R.string.savings_target_description,
                                    formatMinor(target, data.currency),
                                )
                            }.orEmpty()
                            WhfinSavingsPaceBar(
                                periodLabel = savingsPeriodLabel(point.month, range, locale),
                                valueMinor = point.paceMinor,
                                valueDescription = formatMinor(
                                    point.paceMinor,
                                    data.currency,
                                    withSign = true,
                                ) + targetSuffix,
                                periodDescription = point.month.atDay(1).format(periodFormatter),
                                selected = point == selected,
                            )
                        },
                        targetMinor = commonTarget,
                        targetDescription = commonTarget?.let { target ->
                            stringResource(
                                R.string.savings_current_target,
                                formatMinor(target, data.currency),
                            )
                        },
                        fitToWidth = range == SavingsChartRange.Year,
                        onBarClick = { index -> selectedMonth = months[index].month.toString() },
                    )
                    SavingsChartMode.Balance -> WhfinSavingsBalanceChart(
                        points = months.map { point ->
                            WhfinSavingsBalancePoint(
                                periodLabel = savingsPeriodLabel(point.month, range, locale),
                                balanceMinor = point.reserveBalanceMinor,
                                balanceDescription = formatMinor(point.reserveBalanceMinor, data.currency),
                                periodDescription = point.month.atDay(1).format(periodFormatter),
                            )
                        },
                        goalMinor = data.currentPlan?.goalMinor,
                        goalDescription = data.currentPlan?.goalMinor?.let { goal ->
                            stringResource(
                                R.string.savings_goal_description,
                                formatMinor(goal, data.currency),
                            )
                        },
                    )
                    SavingsChartMode.Projection -> Unit
                }
                selected?.let { point ->
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WhfinIconButton(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.savings_previous_month),
                            { if (selectedIndex > 0) selectedMonth = months[selectedIndex - 1].month.toString() },
                            outlined = false,
                            enabled = selectedIndex > 0,
                        )
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(point.month.atDay(1).format(periodFormatter), style = MaterialTheme.typography.bodySmall)
                            WhfinAmount(
                                formatMinor(if (mode == SavingsChartMode.Pace) point.paceMinor else point.reserveBalanceMinor,
                                    data.currency, withSign = mode == SavingsChartMode.Pace),
                                symbol = currencySymbol(data.currency),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (mode == SavingsChartMode.Pace) point.targetMinor?.let { target ->
                                Text(stringResource(R.string.savings_target_description, formatMinor(target, data.currency)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        WhfinIconButton(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.savings_next_month),
                            { if (selectedIndex < months.lastIndex) selectedMonth = months[selectedIndex + 1].month.toString() },
                            outlined = false,
                            enabled = selectedIndex < months.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

private fun savingsPeriodLabel(
    month: YearMonth,
    range: SavingsChartRange,
    locale: Locale,
): String = when (range) {
    SavingsChartRange.Year -> month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
    SavingsChartRange.All -> buildString {
        append(month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale))
        append('\n')
        append(month.year.toString().takeLast(2))
    }
}

private val previewMonths = (1..12).map { month ->
    SavingsMonthUi(
        month = YearMonth.of(2026, month),
        reserveBalanceMinor = 1_200_000L + month * 120_000L,
        paceMinor = if (month == 4) -30_000L else 80_000L + month * 4_000L,
        targetMinor = 100_000L,
    )
}

private val previewData = SavingsScreenData(
    currency = "GEL",
    months = previewMonths,
    currentPlan = SavingsPlanEntity(
        id = 1,
        currency = "GEL",
        monthlyTargetMinor = 100_000L,
        goalMinor = 3_000_000L,
        goalBy = LocalDate.of(2027, 12, 31).toEpochDay(),
        startedOn = LocalDate.of(2026, 1, 1).toEpochDay(),
        createdAt = 0,
    ),
    currentReserveMinor = previewMonths.last().reserveBalanceMinor,
    currentPaceMinor = previewMonths.last().paceMinor,
    rollingThreeMonthMinor = 124_000L,
    monthsOnPace = 8,
    evaluatedMonths = 11,
    hasReserve = true,
)

@Preview(name = "Savings light", showBackground = true, heightDp = 900)
@Composable
private fun SavingsLightPreview() {
    WhfinTheme(darkTheme = false) { SavingsScreen(previewData, { _, _, _ -> }, {}) }
}

@Preview(name = "Savings dark large", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, heightDp = 900)
@Composable
private fun SavingsDarkLargePreview() {
    WhfinTheme(darkTheme = true) { SavingsScreen(previewData, { _, _, _ -> }, {}) }
}

@Preview(name = "Savings no plan compact", showBackground = true, heightDp = 640)
@Composable
private fun SavingsNoPlanPreview() {
    WhfinTheme(darkTheme = false) {
        SavingsScreen(previewData.copy(currentPlan = null), { _, _, _ -> }, {})
    }
}
