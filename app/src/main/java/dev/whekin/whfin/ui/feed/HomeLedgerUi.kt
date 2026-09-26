package dev.whekin.whfin.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinTotalRule
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSkeleton
import dev.whekin.whfin.core.ui.WhfinSkeletonBlock
import dev.whekin.whfin.core.ui.WhfinSkeletonLedgerRow
import dev.whekin.whfin.core.ui.WhfinRunwayTimeline
import dev.whekin.whfin.core.ui.WhfinTimelineMark
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.data.recurring.RecurringCharge

@Composable
internal fun HomeSectionHeader(
    title: String,
    action: String?,
    onAction: () -> Unit,
    metricMinor: Long? = null,
    icon: ImageVector? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhfinSectionLabel(title, Modifier.weight(1f), icon = icon)
        // The day's own total belongs to the day's label: it is the same fact, said once.
        if (metricMinor != null) WhfinAmount(
            text = formatMinor(-metricMinor, "GEL", withSign = true),
            symbol = currencySymbol("GEL"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (action != null) TextButton(onClick = onAction) { Text(action, maxLines = 1) }
    }
}

/**
 * How long today's money lasts, said once and only while it is news.
 *
 * The row carries the consequence in words as well as colour: a runway that does not reach the
 * declared payday must still read as short to someone who cannot see the accent.
 */
@Composable
internal fun HomeRunwayRow(
    runway: HomeRunway,
    today: LocalDate = dev.whekin.whfin.data.LedgerCalendar.today(),
    onOpenAccounts: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val accent = if (runway.shortOfIncome) {
        WhfinThemeTokens.colors.warning
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val burn = stringResource(
        R.string.home_runway_burn,
        formatMinor(runway.dailyBurnMinor, "GEL"),
    )
    val payday = runway.nextIncome?.takeUnless { it.passed }?.let { incomeTimingLabel(it, dateFormat) }
    val primaryOccurrences = runway.recurringOccurrences.filter { occurrence ->
        runway.nextIncome?.let { occurrence.dueDate <= it.expected } ?: true
    }
    val firstOccurrence = primaryOccurrences.firstOrNull()?.let { occurrence ->
        val date = occurrence.dueDate.format(dateFormat)
        stringResource(
            R.string.home_runway_recurring,
            occurrence.charge.label,
            formatMinor(occurrence.amountMinor, "GEL"),
            date,
        )
    }
    val moreOccurrences = (primaryOccurrences.size - 1).takeIf { it > 0 }?.let { count ->
        pluralStringResource(R.plurals.home_recurring_more, count, count)
    }
    val obligations = listOfNotNull(firstOccurrence, moreOccurrences).joinToString(" · ")
        .takeIf(String::isNotEmpty)
    val shape = remember(runway, today) { runwayShape(runway, today) }
    val expandable = shape != null || primaryOccurrences.isNotEmpty() || runway.expectedExpenseMinor != null
    val title = runway.shortfallMinor?.let { shortfall ->
        stringResource(
            R.string.home_runway_shortfall,
            formatMinor(shortfall, "GEL"),
            runway.nextIncome?.expected?.format(dateFormat).orEmpty(),
        )
    } ?: runway.nextIncome?.takeUnless { it.passed }?.let { income ->
        stringResource(R.string.home_runway_enough, income.expected.format(dateFormat))
    } ?: pluralStringResource(
        R.plurals.home_runway_days,
        runway.daysLeft ?: 0,
        runway.daysLeft ?: 0,
    )
    // Everything the shape already draws is struck from the sentence beneath it: the payday and the
    // day the money ends are marks on the rule now, and repeating them below is the old paragraph
    // growing back.
    val detail = listOfNotNull(burn, obligations, payday.takeIf { shape == null })
        .joinToString(" · ")
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-runway"),
        tonal = true,
    ) {
        if (shape == null) WhfinLedgerRow(
            title = title,
            titleMaxLines = Int.MAX_VALUE,
            supportingText = detail,
            supportingMaxLines = Int.MAX_VALUE,
            icon = Icons.Outlined.Schedule,
            iconTint = accent,
            markerColor = if (runway.shortOfIncome) accent else null,
            trailing = {
                Icon(
                    if (!expandable) Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expandable) stringResource(R.string.home_runway_calculation) else null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = if (expandable) ({ expanded = !expanded }) else onOpenAccounts,
        ) else Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.home_runway_calculation)) {
                    expanded = !expanded
                }
                .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (runway.shortOfIncome) Spacer(
                    Modifier
                        .width(WhfinThemeTokens.sizes.ledgerMarker)
                        .height(36.dp)
                        .background(accent, CircleShape),
                )
                Box(
                    Modifier
                        .size(WhfinThemeTokens.sizes.iconContainer)
                        .background(accent.copy(alpha = .11f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.home_runway_calculation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val runsOutLabel = shape.runsOut?.format(dateFormat)
            val paydayLabel = shape.payday.format(dateFormat)
            WhfinRunwayTimeline(
                fundedFraction = shape.fundedFraction,
                // The two dates that decide the answer, in the order they must survive a collision:
                // where the money ends first, then when it is refilled, then the day it is read on.
                marks = listOfNotNull(
                    runsOutLabel?.let {
                        WhfinTimelineMark(shape.fundedFraction, it, emphasis = true)
                    },
                    WhfinTimelineMark(shape.paydayFraction, paydayLabel),
                    WhfinTimelineMark(0f, stringResource(R.string.home_runway_today)),
                ),
                contentDescription = listOfNotNull(
                    title,
                    runsOutLabel?.let { stringResource(R.string.home_runway_runs_out, it) },
                    stringResource(R.string.home_runway_payday_mark, paydayLabel),
                ).joinToString(". "),
                shortfall = shape.shortOfIncome,
            )
            if (detail.isNotEmpty()) Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded && expandable) {
            // The rule above marks the dates; when it is drawn, the sentence naming the whole
            // payday window — the ordinary day, the weekend it moved off, the outer bound — waits
            // here rather than crowding the card everybody reads at a glance.
            if (shape != null && payday != null) Text(
                payday,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            primaryOccurrences.forEach { occurrence ->
                WhfinLedgerRow(
                    title = occurrence.charge.label,
                    titleMaxLines = Int.MAX_VALUE,
                    supportingText = stringResource(R.string.home_runway_expected_payment,
                        formatMinor(occurrence.amountMinor, "GEL"),
                        occurrence.dueDate.format(dateFormat)),
                    supportingMaxLines = Int.MAX_VALUE,
                )
            }
            runway.expectedExpenseMinor?.let { expected ->
                WhfinLedgerRow(
                    title = stringResource(R.string.home_runway_expected_total, formatMinor(expected, "GEL")),
                    titleMaxLines = Int.MAX_VALUE,
                    supportingText = runway.remainingMinor?.takeIf { it >= 0L }?.let {
                        stringResource(R.string.home_runway_remaining, formatMinor(it, "GEL"))
                    },
                    supportingMaxLines = Int.MAX_VALUE,
                )
            }
            TextButton(onClick = onOpenAccounts, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.tab_accounts))
            }
        }
    }
}

/**
 * What this month still owes, named rather than folded into a forecast.
 *
 * The number stays out of the pace insight on purpose: a projection the person can also read in
 * Statistics must mean the same thing on both screens, and an obligation is a fact about the future
 * rather than a rate. Naming the payees is what makes the sum checkable.
 */
@Composable
internal fun HomeRecurringRow(charges: List<RecurringCharge>) {
    val total = charges.sumOf(RecurringCharge::typicalMinor)
    val named = charges.take(MAX_NAMED_CHARGES).joinToString(" · ") { it.label }
    val rest = charges.size - MAX_NAMED_CHARGES
    val supporting = if (rest > 0) {
        "$named · ${pluralStringResource(R.plurals.home_recurring_more, rest, rest)}"
    } else {
        named
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("home-recurring"),
    ) {
        WhfinLedgerRow(
            title = stringResource(R.string.home_recurring_title),
            supportingText = supporting,
            supportingMaxLines = 2,
            icon = Icons.Outlined.EventRepeat,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            trailing = {
                WhfinAmount(
                    text = formatMinor(total, "GEL"),
                    symbol = currencySymbol("GEL"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}

private const val MAX_NAMED_CHARGES = 3

/**
 * Home while the ledger is still answering: the month block and the first rows, without numbers.
 *
 * The layout is the message. The blocks sit where the result, the two flow figures and the first two
 * rows will land, so nothing jumps when the real values arrive and nothing claims an amount before
 * they do.
 */
@Composable
internal fun HomeSkeleton() {
    WhfinSkeleton(
        contentDescription = stringResource(R.string.home_loading),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // The wait has the shape of the answer: the forecast block, then the first rows. The month
        // now closes Home instead of opening it, so its rule waits at the bottom too.
        WhfinSkeletonBlock(Modifier.fillMaxWidth(), height = 64.dp)
        WhfinSkeletonBlock(Modifier.fillMaxWidth(.35f), height = 11.dp)
        WhfinSkeletonLedgerRow()
        WhfinSkeletonLedgerRow()
        WhfinSkeletonBlock(Modifier.fillMaxWidth(.3f), height = 11.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            WhfinSkeletonBlock(Modifier.weight(1f), height = 15.dp)
            WhfinSkeletonBlock(Modifier.weight(1f), height = 15.dp)
        }
        WhfinTotalRule()
    }
}

/** The same wait on the full ledger, where rows are all there is. */
@Composable
internal fun FeedSkeleton() {
    WhfinSkeleton(
        contentDescription = stringResource(R.string.home_loading),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        repeat(4) { WhfinSkeletonLedgerRow() }
    }
}

/**
 * Borrowed money the balances above still count as the person's own.
 *
 * One row per currency, because a debt in dollars and a debt in lari are two different promises and
 * adding them would need a rate to say something that needs none.
 */
@Composable
internal fun HomeDebtsOwedRow(
    debts: List<HomeDebt>,
    onOpenAccounts: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("home-debts-owed"),
    ) {
        val shown = debts.take(2)
        shown.forEachIndexed { index, debt ->
            WhfinLedgerRow(
                title = stringResource(R.string.home_debts_owed_title),
                supportingText = debt.people.take(2).joinToString(" · ").takeIf(String::isNotEmpty),
                icon = Icons.Outlined.Handshake,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                trailing = {
                    WhfinAmount(
                        text = formatMinor(debt.outstandingMinor, debt.currency),
                        symbol = currencySymbol(debt.currency),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                divider = index != shown.lastIndex,
                onClick = onOpenAccounts,
            )
        }
    }
}

@Composable
private fun incomeTimingLabel(
    payday: NextPayday,
    format: DateTimeFormatter,
): String = when {
    // Once the estimate is behind us the date says nothing useful; what is true is that the money
    // has not come yet, and no next date has been earned.
    payday.passed -> stringResource(R.string.income_awaiting_short)
    payday.weekendAdjusted -> stringResource(
        R.string.income_weekend_estimate,
        payday.usual.format(format),
        payday.expected.format(format),
    )
    else -> stringResource(R.string.home_runway_income, payday.expected.format(format))
}

/**
 * The one row that stands for everything Home decided not to raise yet.
 *
 * Kept quiet on purpose: the conditions behind it are real but ranked below what is already shown,
 * and a second alarming block would defeat the cap it exists to enforce.
 */
@Composable
internal fun HomeNoticesFold(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-notices-fold"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = if (expanded) {
                stringResource(R.string.home_notices_collapse)
            } else {
                pluralStringResource(R.plurals.home_notices_folded, count, count)
            },
            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onToggle,
        )
    }
}

@Composable
internal fun CredoSyncReminderCard(
    reminder: CredoSyncReminder,
    onClick: () -> Unit,
) {
    val age = reminder.daysSinceSync?.let { days ->
        pluralStringResource(R.plurals.home_credo_sync_days, days, days)
    } ?: stringResource(R.string.home_credo_sync_never)
    val waiting = reminder.awaitingStatementCount.takeIf { it > 0 }?.let { count ->
        pluralStringResource(R.plurals.home_credo_sync_waiting, count, count)
    }
    WhfinLedgerGroup(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("home-credo-sync-reminder"),
        tonal = true,
    ) {
        WhfinLedgerRow(
            title = stringResource(R.string.home_credo_sync_title),
            supportingText = listOfNotNull(age, waiting).joinToString(" · "),
            supportingMaxLines = 2,
            icon = Icons.Default.Sync,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onClick,
        )
    }
}

/**
 * The way back into a setup that was walked past.
 *
 * Skipping is allowed, so this offers rather than nags: it is the quietest notice kind, it withdraws
 * on its own as soon as a bank exists, and refusing it here is permanent — an offer that cannot be
 * turned off for good is a demand.
 */
@Composable
internal fun SetupInvitationCard(onResume: () -> Unit, onDismiss: () -> Unit) {
    WhfinNotice(
        title = stringResource(R.string.home_setup_invitation_title),
        body = stringResource(R.string.home_setup_invitation_body),
        icon = Icons.Default.AccountBalance,
        kind = WhfinNoticeKind.Info,
        actionLabel = stringResource(R.string.home_setup_invitation_action),
        onAction = onResume,
        dismissIcon = Icons.Default.Close,
        dismissContentDescription = stringResource(R.string.home_setup_invitation_dismiss),
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SmsOnboardingCard(onEnable: () -> Unit, onDismiss: () -> Unit) {
    WhfinNotice(
        title = stringResource(R.string.sms_onboarding_title),
        body = stringResource(R.string.sms_onboarding_body),
        icon = Icons.Default.Sms,
        kind = WhfinNoticeKind.Attention,
        actionLabel = stringResource(R.string.sms_onboarding_action),
        onAction = onEnable,
        dismissIcon = Icons.Default.Close,
        dismissContentDescription = stringResource(R.string.sms_onboarding_dismiss),
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
