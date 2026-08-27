package dev.whekin.whfin.ui.savings

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.data.savings.projectSavings
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun SavingsPlanEditor(
    plan: SavingsPlanEntity?, currency: String, balanceMinor: Long,
    onDismiss: () -> Unit, onSave: (Long, Long?, LocalDate?) -> Unit, onClear: (() -> Unit)?,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WhfinDialogSystemBars(darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DemoWorkspaceFrame {
                SavingsPlanEditorContent(plan, currency, balanceMinor, LocalDate.now(), onDismiss, onSave, onClear)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavingsPlanEditorContent(
    plan: SavingsPlanEntity?, currency: String, balanceMinor: Long, today: LocalDate,
    onDismiss: () -> Unit, onSave: (Long, Long?, LocalDate?) -> Unit, onClear: (() -> Unit)?,
) {
    var monthlyText by rememberSaveable(plan?.id) { mutableStateOf(plan?.monthlyTargetMinor?.moneyInput().orEmpty()) }
    var goalText by rememberSaveable(plan?.id) { mutableStateOf(plan?.goalMinor?.moneyInput().orEmpty()) }
    var goalDay by rememberSaveable(plan?.id) { mutableStateOf(plan?.goalBy) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val monthly = parseToMinor(monthlyText)?.takeIf { it > 0L }
    val goal = parseToMinor(goalText)?.takeIf { it > 0L }
    val targetDate = goalDay?.let(LocalDate::ofEpochDay)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val close = { if (calendar) calendar = false else onDismiss() }
    val gesture = rememberWhfinBackGesture(enabled = true, onBack = close)

    Box(Modifier.fillMaxSize().whfinPredictiveBack(gesture)) {
        if (calendar) {
            val forecast = projectSavings(balanceMinor, monthly ?: 0L, today, goal)
            val initial = (targetDate ?: forecast.goalReachedOn ?: today.plusYears(1)).coerceAtLeast(today)
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = today.year..maxOf(today.year + 30, initial.year),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= today
                },
            )
            val selected = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            val projection = projectSavings(balanceMinor, monthly ?: 0L, today, goal, selected)
            key("calendar") {
                WhfinFullScreenForm(
                    title = stringResource(R.string.savings_choose_date), closeDescription = stringResource(R.string.action_cancel),
                    onClose = { calendar = false }, back = true,
                    primaryLabel = stringResource(R.string.action_done), primaryEnabled = selected != null,
                    onPrimary = { goalDay = selected?.toEpochDay(); calendar = false },
                    footer = { selected?.let { SavingsDeadlineReading(projection, it, currency) } },
                ) {
                    WhfinCalendar(picker, Modifier.fillMaxWidth().testTag("savings-date-picker"))
                }
            }
        } else key("editor") {
            WhfinFullScreenForm(
                title = stringResource(R.string.savings_plan_edit), closeDescription = stringResource(R.string.action_cancel),
                onClose = onDismiss, primaryLabel = stringResource(R.string.action_save),
                primaryEnabled = monthly != null && (goalText.isBlank() || goal != null),
                onPrimary = { onSave(requireNotNull(monthly), goal, targetDate.takeIf { goal != null }) },
            ) {
                WhfinField(monthlyText, { monthlyText = it.take(14) }, stringResource(R.string.savings_plan_monthly_amount),
                    suffix = currencySymbol(currency), keyboardType = KeyboardType.Decimal, modifier = Modifier.testTag("savings-monthly-input"))
                WhfinField(goalText, { goalText = it.take(14) }, stringResource(R.string.savings_plan_goal_amount),
                    suffix = currencySymbol(currency), keyboardType = KeyboardType.Decimal, modifier = Modifier.testTag("savings-goal-input"))
                if (goal != null) {
                    WhfinLedgerRow(
                        title = targetDate?.let { savingsDate(it) } ?: stringResource(R.string.savings_choose_date),
                        supportingText = stringResource(R.string.savings_plan_goal_date),
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = { focus.clearFocus(); keyboard?.hide(); calendar = true },
                        modifier = Modifier.testTag("savings-open-calendar"),
                    )
                    if (goalDay != null) WhfinButton(stringResource(R.string.savings_clear_date), { goalDay = null }, style = WhfinActionStyle.Quiet)
                } else Text(stringResource(R.string.savings_date_needs_goal), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (monthly != null || goal != null) SavingsProjectionPanel(
                    balanceMinor, monthly ?: 0L, currency, goal, targetDate.takeIf { goal != null }, today,
                    onUseRequired = { monthlyText = it.moneyInput() },
                )
                Text(stringResource(R.string.savings_plan_effective), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                onClear?.let { WhfinButton(stringResource(R.string.savings_plan_pause), it, style = WhfinActionStyle.Secondary) }
            }
        }
    }
}

private fun Long.moneyInput(): String = BigDecimal.valueOf(this, 2).stripTrailingZeros().toPlainString()

@Preview(name = "Live savings editor", widthDp = 400, heightDp = 900)
@Preview(name = "Live savings editor dark large", widthDp = 400, heightDp = 640, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SavingsEditorPreview() {
    WhfinTheme { SavingsPlanEditorContent(
        SavingsPlanEntity(currency = "GEL", monthlyTargetMinor = 100_000, goalMinor = 3_000_000,
            startedOn = 0, createdAt = 0), "GEL", 1_800_000, LocalDate.of(2026, 8, 27), {}, { _, _, _ -> }, null,
    ) }
}
