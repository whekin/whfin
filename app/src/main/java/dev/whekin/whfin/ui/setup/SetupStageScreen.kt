package dev.whekin.whfin.ui.setup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    primaryAction: SetupAction? = null,
    additionalActions: List<SetupAction> = emptyList(),
    onSelectStage: ((SetupStage) -> Unit)? = null,
) {
    var showMore by rememberSaveable(stage) { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberSaveable(stage, saver = ScrollState.Saver) { ScrollState(0) }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(scrollState).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhfinBackButton(stringResource(R.string.action_back), onBack)
                    Column(Modifier.weight(1f)) {
                        WhfinSectionLabel(stringResource(R.string.setup_stage_progress, stage.ordinal + 1, SetupStage.entries.size))
                    }
                    if (onSelectStage != null) WhfinButton(stringResource(R.string.setup_guide),
                        { showGuide = !showGuide }, style = WhfinActionStyle.Quiet)
                }
                if (showGuide && onSelectStage != null) {
                    Text(stringResource(R.string.setup_guide_body), style = MaterialTheme.typography.bodyMedium)
                    WhfinLedgerGroup {
                        SetupStage.entries.forEachIndexed { index, target ->
                            WhfinLedgerRow(title = "${index + 1}. ${stringResource(target.title)}",
                                supportingText = if (target == stage) stringResource(R.string.setup_current_step) else null,
                                onClick = { showGuide = false; onSelectStage(target) }, divider = index < SetupStage.entries.lastIndex)
                        }
                    }
                }
                when (stage) {
                    SetupStage.Categories -> SetupIllustration(WhfinIllustrationScene.Sort)
                    SetupStage.Ready -> SetupIllustration(WhfinIllustrationScene.Balance)
                    else -> Unit
                }
                Text(stringResource(stage.title), style = MaterialTheme.typography.headlineMedium)
                if (stage != SetupStage.Ready) Text(stringResource(stage.body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium)
                content?.invoke()
                val visibleActions = actions + if (showMore) additionalActions else emptyList()
                if (visibleActions.isNotEmpty()) WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    visibleActions.forEachIndexed { index, action ->
                        WhfinLedgerRow(title = action.label, supportingText = action.supportingText, onClick = action.onClick,
                            divider = index < visibleActions.lastIndex,
                            trailing = { Icon(Icons.Default.ChevronRight, null) })
                    }
                }
                if (additionalActions.isNotEmpty()) WhfinButton(
                    stringResource(if (showMore) R.string.setup_less_options else R.string.setup_more_options),
                    { showMore = !showMore }, style = WhfinActionStyle.Quiet,
                )
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val primary = primaryAction ?: footerAction
                if (primary != null) WhfinButton(primary.label, primary.onClick, Modifier.fillMaxWidth())
                WhfinButton(
                    continueLabel ?: stringResource(setupNextLabel(stage, false, false, false, false, false)),
                    onContinue, Modifier.fillMaxWidth(),
                    enabled = continueEnabled,
                    style = if (primary != null || stage == SetupStage.Banks || stage == SetupStage.Accounts && actions.isEmpty())
                        WhfinActionStyle.Quiet else WhfinActionStyle.Primary,
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
