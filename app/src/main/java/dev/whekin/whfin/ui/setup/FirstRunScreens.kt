package dev.whekin.whfin.ui.setup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun WelcomeChoiceScreen(
    busy: Boolean,
    problem: String?,
    onSetUpPersonal: () -> Unit,
    onExploreDemo: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            WhfinSectionLabel(stringResource(R.string.app_name))
            // The greeting sits in the middle of the screen rather than at the top of an empty
            // one: two sentences pinned under the status bar left two thirds of a phone blank,
            // which reads as a screen still loading. It still scrolls, so a large font scale
            // pushes nothing off the bottom.
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = viewport),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(96.dp).offset(x = (-24).dp),
                        )
                        Text(
                            stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (problem != null) {
                            WhfinNotice(
                                title = stringResource(R.string.demo_mode_problem_title),
                                body = problem,
                                kind = WhfinNoticeKind.Error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WhfinButton(
                    label = stringResource(R.string.welcome_personal_action),
                    onClick = onSetUpPersonal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !busy,
                    leadingIcon = Icons.Default.Wallet,
                )
                WhfinButton(
                    label = stringResource(R.string.welcome_demo_action),
                    onClick = onExploreDemo,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    style = WhfinActionStyle.Secondary,
                    leadingIcon = Icons.Default.Visibility,
                )
            }
        }
    }
}

data class PersonalSetupState(
    val accountCount: Int? = null,
    val bankLedgerCount: Int? = null,
    val hasCredoImport: Boolean? = null,
    val smsMonitoringEnabled: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val canRequestSmsPermission: Boolean = true,
    val unresolvedSmsCount: Int? = null,
    val statementReviewCount: Int? = null,
)

internal val PersonalSetupState.smsReady: Boolean
    get() = smsMonitoringEnabled && hasSmsPermission

internal val PersonalSetupState.reviewCount: Int?
    get() = if (unresolvedSmsCount != null && statementReviewCount != null) {
        unresolvedSmsCount + statementReviewCount
    } else {
        null
    }

internal val PersonalSetupState.ready: Boolean
    get() = smsReady && hasCredoImport == true && reviewCount == 0

internal enum class PersonalSetupStep { Bank, Accounts, Ready, Alternative }

@Composable
internal fun PersonalSetupScreen(
    step: PersonalSetupStep,
    state: PersonalSetupState,
    onConnectBank: () -> Unit,
    onShowAlternatives: () -> Unit,
    onImportStatement: () -> Unit,
    onCreateAccount: () -> Unit,
    onRestoreBackup: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (step != PersonalSetupStep.Bank) {
                    WhfinBackButton(stringResource(R.string.action_back), onBack)
                }
                WizardHeader(step = step, bankConnected = state.ready)
                WizardBody(step = step, state = state)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when (step) {
                    PersonalSetupStep.Bank -> {
                        WhfinButton(
                            label = if (state.hasCredoImport == null) {
                                stringResource(R.string.personal_setup_checking)
                            } else if (state.hasCredoImport && state.smsReady) {
                                stringResource(R.string.personal_setup_continue_bank_action)
                            } else {
                                stringResource(R.string.personal_setup_connect_action)
                            },
                            onClick = onConnectBank,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            enabled = state.hasCredoImport != null,
                            leadingIcon = Icons.Default.CloudSync,
                        )
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_other_action),
                            onClick = onShowAlternatives,
                            modifier = Modifier.fillMaxWidth(),
                            style = WhfinActionStyle.Secondary,
                        )
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_skip_bank_action),
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            style = WhfinActionStyle.Quiet,
                        )
                    }
                    PersonalSetupStep.Accounts -> {
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_add_account_action),
                            onClick = onCreateAccount,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            leadingIcon = Icons.Default.Wallet,
                        )
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_skip_optional_action),
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            style = WhfinActionStyle.Quiet,
                        )
                    }
                    PersonalSetupStep.Ready -> WhfinButton(
                        label = stringResource(R.string.personal_setup_continue_action),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        leadingIcon = Icons.Default.CheckCircle,
                    )
                    PersonalSetupStep.Alternative -> {
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_statement_title),
                            onClick = onImportStatement,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            leadingIcon = Icons.AutoMirrored.Filled.FactCheck,
                        )
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_restore_title),
                            onClick = onRestoreBackup,
                            modifier = Modifier.fillMaxWidth(),
                            style = WhfinActionStyle.Secondary,
                            leadingIcon = Icons.Default.Restore,
                        )
                        WhfinButton(
                            label = stringResource(R.string.personal_setup_continue_without_bank_action),
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            style = WhfinActionStyle.Quiet,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: PersonalSetupStep, bankConnected: Boolean) {
    val label = when (step) {
        PersonalSetupStep.Bank -> R.string.personal_setup_progress_bank
        PersonalSetupStep.Accounts -> R.string.personal_setup_progress_accounts
        PersonalSetupStep.Ready -> R.string.personal_setup_progress_ready
        PersonalSetupStep.Alternative -> R.string.personal_setup_progress_alternative
    }
    val title = when (step) {
        PersonalSetupStep.Bank -> R.string.personal_setup_bank_title
        PersonalSetupStep.Accounts -> R.string.personal_setup_accounts_title
        PersonalSetupStep.Ready -> R.string.personal_setup_ready_title
        PersonalSetupStep.Alternative -> R.string.personal_setup_alternative_title
    }
    val body = when (step) {
        PersonalSetupStep.Bank -> R.string.personal_setup_bank_body
        PersonalSetupStep.Accounts -> R.string.personal_setup_accounts_body
        PersonalSetupStep.Ready -> if (bankConnected) {
            R.string.personal_setup_ready_body
        } else {
            R.string.personal_setup_ready_skipped_body
        }
        PersonalSetupStep.Alternative -> R.string.personal_setup_alternative_body
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinSectionLabel(stringResource(label))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardBody(step: PersonalSetupStep, state: PersonalSetupState) {
    when (step) {
        PersonalSetupStep.Bank -> {
            WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
                WhfinLedgerRow(
                    title = stringResource(R.string.personal_setup_bank_sms_title),
                    supportingText = stringResource(R.string.personal_setup_bank_sms_body),
                    supportingMaxLines = 3,
                    icon = Icons.Default.Sms,
                    divider = true,
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.personal_setup_bank_history_title),
                    supportingText = stringResource(R.string.personal_setup_bank_history_body),
                    supportingMaxLines = 3,
                    icon = Icons.Default.CloudSync,
                )
            }
            if (state.smsMonitoringEnabled && !state.hasSmsPermission) {
                WhfinNotice(
                    title = stringResource(R.string.personal_setup_permission_needed),
                    body = stringResource(R.string.personal_setup_permission_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PersonalSetupStep.Accounts -> WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            WhfinLedgerRow(
                title = stringResource(R.string.personal_setup_manual_title),
                supportingText = when (state.accountCount) {
                    null -> stringResource(R.string.personal_setup_checking)
                    0 -> stringResource(R.string.personal_setup_manual_body)
                    else -> pluralStringResource(
                        R.plurals.personal_setup_accounts_ready,
                        state.accountCount,
                        state.accountCount,
                    )
                },
                supportingMaxLines = 3,
                icon = Icons.Default.Wallet,
            )
        }
        PersonalSetupStep.Ready -> WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            WhfinLedgerRow(
                title = if (state.ready) {
                    stringResource(R.string.personal_setup_bank_ready_title)
                } else {
                    stringResource(R.string.personal_setup_later_title)
                },
                supportingText = if (state.ready) {
                    stringResource(
                        R.string.personal_setup_ledgers_ready,
                        state.bankLedgerCount ?: 0,
                    )
                } else {
                    stringResource(R.string.personal_setup_later_body)
                },
                supportingMaxLines = 3,
                icon = if (state.ready) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                iconTint = MaterialTheme.colorScheme.primary,
            )
        }
        PersonalSetupStep.Alternative -> WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.personal_setup_other_title),
                supportingText = stringResource(R.string.personal_setup_other_body),
                supportingMaxLines = 3,
                icon = Icons.AutoMirrored.Filled.FactCheck,
            )
        }
    }
}

@Composable
fun PersonalSetupSecondaryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
            content()
        }
    }
}

@Preview(name = "Welcome light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Welcome dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Welcome font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f)
@Preview(name = "Welcome compact", widthDp = 400, heightDp = 560)
@Composable
private fun WelcomeChoicePreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WelcomeChoiceScreen(false, null, {}, {}, {})
        }
    }
}

@Preview(name = "Personal setup light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Personal setup dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Personal setup font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f)
@Preview(name = "Personal setup compact", widthDp = 400, heightDp = 560)
@Composable
private fun PersonalSetupPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PersonalSetupScreen(
                step = PersonalSetupStep.Bank,
                state = PersonalSetupState(
                    accountCount = 2,
                    bankLedgerCount = 2,
                    hasCredoImport = false,
                    unresolvedSmsCount = 0,
                    statementReviewCount = 0,
                ),
                onConnectBank = {},
                onShowAlternatives = {},
                onImportStatement = {},
                onCreateAccount = {},
                onRestoreBackup = {},
                onSkip = {},
                onContinue = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Personal setup ready", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(
    name = "Personal setup ready RU-scale",
    widthDp = 400,
    heightDp = 950,
    fontScale = 1.5f,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PersonalSetupReadyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PersonalSetupScreen(
                step = PersonalSetupStep.Ready,
                state = PersonalSetupState(
                    accountCount = 4,
                    bankLedgerCount = 3,
                    hasCredoImport = true,
                    smsMonitoringEnabled = true,
                    hasSmsPermission = true,
                    unresolvedSmsCount = 0,
                    statementReviewCount = 0,
                ),
                onConnectBank = {},
                onShowAlternatives = {},
                onImportStatement = {},
                onCreateAccount = {},
                onRestoreBackup = {},
                onSkip = {},
                onContinue = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Personal setup accounts compact", widthDp = 400, heightDp = 560, fontScale = 1.5f)
@Composable
private fun PersonalSetupAccountsPreview() {
    WhfinTheme {
        PersonalSetupScreen(
            step = PersonalSetupStep.Accounts,
            state = PersonalSetupState(accountCount = 3, hasCredoImport = true),
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = {},
            onRestoreBackup = {},
            onSkip = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Personal setup alternative RU",
    widthDp = 400,
    heightDp = 850,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PersonalSetupAlternativePreview() {
    WhfinTheme {
        PersonalSetupScreen(
            step = PersonalSetupStep.Alternative,
            state = PersonalSetupState(hasCredoImport = false),
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = {},
            onRestoreBackup = {},
            onSkip = {},
            onContinue = {},
            onBack = {},
        )
    }
}
