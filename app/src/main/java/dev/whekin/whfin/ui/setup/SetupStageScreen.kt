package dev.whekin.whfin.ui.setup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.ui.theme.WhfinTheme

internal enum class SetupStage(val title: Int, val body: Int) {
    Banks(R.string.setup_banks_title, R.string.setup_banks_body),
    Sms(R.string.setup_sms_title, R.string.setup_sms_body),
    Accounts(R.string.setup_accounts_title, R.string.setup_accounts_body),
    Categories(R.string.setup_categories_title, R.string.setup_categories_body),
    Income(R.string.setup_income_title, R.string.setup_income_body),
    Plans(R.string.setup_plans_title, R.string.setup_plans_body),
    Preferences(R.string.setup_preferences_title, R.string.setup_preferences_body),
    Ready(R.string.setup_finish_title, R.string.setup_finish_body),
}

internal fun setupStageFromSaved(value: String?): SetupStage =
    SetupStage.entries.firstOrNull { it.name == value } ?: SetupStage.Banks

internal data class SetupAction(val label: String, val supportingText: String? = null, val onClick: () -> Unit)

/** Optional work stays inside setup; only the final action completes the first run. */
@Composable
internal fun SetupStageScreen(
    stage: SetupStage,
    actions: List<SetupAction>,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    summary: String? = null,
    continueLabel: String? = null,
    continueEnabled: Boolean = true,
    footerAction: SetupAction? = null,
    content: (@Composable () -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                WhfinSectionLabel(stringResource(R.string.setup_stage_progress, stage.ordinal + 1, SetupStage.entries.size))
                Text(stringResource(stage.title), style = MaterialTheme.typography.headlineLarge)
                if (stage != SetupStage.Ready) Text(stringResource(stage.body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium)
                content?.invoke()
                if (actions.isNotEmpty()) WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { index, action ->
                        WhfinLedgerRow(title = action.label, supportingText = action.supportingText, onClick = action.onClick,
                            divider = index < actions.lastIndex,
                            trailing = { Icon(Icons.Default.ChevronRight, null) })
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (footerAction != null) WhfinButton(footerAction.label, footerAction.onClick, Modifier.fillMaxWidth())
                WhfinButton(
                    continueLabel ?: stringResource(if (stage == SetupStage.Ready) R.string.personal_setup_continue_action else R.string.setup_next),
                    onContinue, Modifier.fillMaxWidth(),
                    enabled = continueEnabled,
                    style = if (footerAction == null) WhfinActionStyle.Primary else WhfinActionStyle.Quiet,
                )
            }
        }
    }
}

@Composable
internal fun SetupSmsConsent(allBanksEnabled: Boolean, hasSmsPermission: Boolean) {
    val ready = allBanksEnabled && hasSmsPermission
    WhfinNotice(
        title = stringResource(if (ready) R.string.setup_sms_all_on else R.string.setup_sms_all_title),
        body = stringResource(when {
            ready -> R.string.setup_sms_all_on_body
            allBanksEnabled -> R.string.setup_sms_all_permission
            else -> R.string.setup_sms_all_body
        }),
        kind = if (allBanksEnabled && !hasSmsPermission) WhfinNoticeKind.Attention else WhfinNoticeKind.Info,
    )
}

@Preview(name = "Setup light", widthDp = 400, heightDp = 850)
@Preview(name = "Setup dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Setup RU large compact", widthDp = 360, heightDp = 560, locale = "ru", fontScale = 1.5f)
@Composable
private fun SetupStagePreview() = WhfinTheme {
    SetupStageScreen(SetupStage.Banks, listOf(SetupAction("Credo") {}, SetupAction("TBC") {},
        SetupAction(stringResource(R.string.setup_channels)) {}), {}, {})
}

@Preview(name = "Bank SMS step light", widthDp = 400, heightDp = 850)
@Preview(name = "Bank SMS step dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Bank SMS step RU large compact", widthDp = 360, heightDp = 560, locale = "ru", fontScale = 1.5f)
@Composable
private fun SetupSmsPreview() = WhfinTheme {
    SetupStageScreen(SetupStage.Sms, listOf(
        SetupAction(stringResource(R.string.setup_sms_review), stringResource(R.string.setup_sms_cards_checked, 1)) {},
        SetupAction(stringResource(R.string.setup_sms_individual)) {},
    ), {}, {}, footerAction = SetupAction(stringResource(R.string.setup_sms_all_enable)) {},
        content = { SetupSmsConsent(false, false) })
}

@Preview(name = "Bank SMS enabled", widthDp = 400, heightDp = 850)
@Composable
private fun SetupSmsEnabledPreview() = WhfinTheme {
    SetupStageScreen(SetupStage.Sms, listOf(
        SetupAction(stringResource(R.string.setup_sms_review)) {},
    ), {}, {}, content = { SetupSmsConsent(true, true) })
}

@Preview(name = "Bank SMS permission RU", widthDp = 360, heightDp = 560,
    locale = "ru", fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SetupSmsPermissionPreview() = WhfinTheme {
    SetupStageScreen(SetupStage.Sms, listOf(
        SetupAction(stringResource(R.string.setup_sms_review)) {},
    ), {}, {}, footerAction = SetupAction(stringResource(R.string.setup_sms_allow)) {},
        content = { SetupSmsConsent(true, false) })
}
