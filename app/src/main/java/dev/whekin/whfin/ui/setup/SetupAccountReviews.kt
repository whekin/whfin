package dev.whekin.whfin.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.accountTitle
import dev.whekin.whfin.ui.formatMinor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun SetupAccountReviews(
    state: SetupOverviewState,
    onRetry: () -> Unit,
    onCheck: (Long, String, Boolean) -> Unit,
    onOpenAccount: (Long) -> Unit,
) {
    when (state) {
        SetupOverviewState.Loading -> WhfinLoadingIndicator()
        SetupOverviewState.Failed -> {
            Text(stringResource(R.string.setup_read_failed))
            WhfinButton(stringResource(R.string.action_retry), onRetry, style = WhfinActionStyle.Secondary)
        }
        is SetupOverviewState.Ready -> {
            val overview = state.value
            if (overview.accounts.isEmpty()) Text(stringResource(R.string.setup_no_accounts))
            else {
                WhfinSectionLabel(stringResource(R.string.setup_checked_count, overview.resolved, overview.accounts.size))
                WhfinLedgerGroup {
                    overview.accounts.forEachIndexed { index, review ->
                        SetupAccountReviewRow(review, onCheck, onOpenAccount)
                        if (index < overview.accounts.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupAccountReviewRow(
    review: SetupAccountReview,
    onCheck: (Long, String, Boolean) -> Unit,
    onOpenAccount: (Long) -> Unit,
) {
    var expanded by rememberSaveable(review.account.id) { mutableStateOf(false) }
    val account = review.account
    val title = listOfNotNull(review.provider, accountTitle(account, review.provider)).distinct().joinToString(" · ")
    val crypto = account.type == AccountType.CRYPTO
    val reviewLabel = if (review.difference != null) R.string.setup_difference_reviewed else R.string.setup_checked
    val amount = if (crypto) review.chainBalance?.let {
        BigDecimal(it.baseUnits).movePointLeft(it.decimals).stripTrailingZeros().toPlainString() + " " + account.currency
    } ?: stringResource(R.string.setup_balance_unknown) else formatMinor(review.balance, account.currency)
    val day = review.bankDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])) }
    val status = when {
        review.difference == 0L -> stringResource(R.string.setup_matches_bank, requireNotNull(day))
        review.difference != null -> stringResource(R.string.setup_bank_difference,
            formatMinor(review.difference, account.currency, withSign = true), requireNotNull(day))
        crypto -> review.chainBalance?.let {
            stringResource(R.string.setup_observed, android.text.format.DateUtils.formatDateTime(
                androidx.compose.ui.platform.LocalContext.current, it.observedAt,
                android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME))
        } ?: stringResource(R.string.setup_refresh_balance)
        else -> stringResource(R.string.setup_check_balance)
    }
    WhfinLedgerRow(title = title, supportingText = "$amount\n$status" +
        if (review.checked && review.difference != 0L && !expanded) "\n" + stringResource(reviewLabel) else "",
        supportingMaxLines = Int.MAX_VALUE,
        onClick = { expanded = !expanded }, trailing = {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        })
    if (expanded) Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (review.bankBalance != null) Text(stringResource(R.string.setup_bank_closing,
            formatMinor(review.bankBalance, account.currency), requireNotNull(day)), style = MaterialTheme.typography.bodyMedium)
        if (review.pending > 0) Text(stringResource(R.string.setup_waiting_bank, review.pending), style = MaterialTheme.typography.bodyMedium)
        if (review.difference != 0L) WhfinLedgerRow(title = stringResource(reviewLabel), onClick = {
            onCheck(account.id, review.fingerprint, !review.checked)
        }, trailing = { WhfinSwitch(review.checked, null, stringResource(reviewLabel)) })
        WhfinButton(stringResource(R.string.setup_open_account), { onOpenAccount(account.id) }, style = WhfinActionStyle.Quiet)
    }
}


@androidx.compose.ui.tooling.preview.Preview(name = "Review light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "Review dark", widthDp = 400, heightDp = 850, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Review RU large", widthDp = 360, heightDp = 560, locale = "ru", fontScale = 1.5f)
@Composable
internal fun SetupReviewPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    SetupReviewSample()
}

/** Synthetic multi-currency fixture shared by preview and device visual QA. */
@Composable
internal fun SetupReviewSample() {
    var checkedSecond by rememberSaveable { mutableStateOf(false) }
    val account = dev.whekin.whfin.data.db.AccountEntity(id = 1, name = "", type = AccountType.BANK,
        currency = "GEL", iban = "GE00EXAMPLE0001")
    val day = LocalDate.of(2026, 9, 20).toEpochDay()
    val rows = listOf(SetupAccountReview(account, "Credo", 250000, 250000, day, 0, null, 0, "1"),
        SetupAccountReview(account.copy(id = 2, currency = "USD", iban = "GE00EXAMPLE0002"),
            "Credo", 125050, 125000, day, 50, null, 0, "2", checkedSecond))
    val overview = SetupOverview(rows, emptyMap(), emptySet(), 8, 0, 1, 0, 0, 0)
    SetupStageScreen(SetupStage.Ready, listOf(SetupAction(stringResource(R.string.setup_banks_title)) {}), {}, {},
        continueLabel = stringResource(if (!overview.allResolved) R.string.setup_continue_unchecked
            else if (overview.hasBankDifference) R.string.setup_start_with_difference
            else R.string.personal_setup_continue_action),
        content = { SetupAccountReviews(SetupOverviewState.Ready(overview), {}, { _, _, value -> checkedSecond = value }, {}) })
}
