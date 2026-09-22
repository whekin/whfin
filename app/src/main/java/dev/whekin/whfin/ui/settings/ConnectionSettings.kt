package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.sms.BankSmsBank

internal data class ConnectionSettingsState(
    val accounts: Map<BankSmsBank, List<AccountEntity>>? = null,
    val lastSync: Map<BankSmsBank, Long?> = emptyMap(),
    val remembered: Set<BankSmsBank> = emptySet(),
    val sms: Map<BankSmsBank, Boolean> = emptyMap(),
    val smsPermission: Boolean = false,
    val pushEnabled: Boolean = false,
    val pushPermission: Boolean = false,
    val pushConnected: Boolean = false,
    val pushRead: Boolean = false,
    val lastPush: Long? = null,
    val unknownPush: Int = 0,
    val pushError: Boolean = false,
) {
    fun configured(bank: BankSmsBank) = sms[bank] == true || accounts?.get(bank).orEmpty().isNotEmpty() || bank in remembered || lastSync[bank] != null || (bank == BankSmsBank.TBC && pushEnabled)
}

internal data class ConnectionSettingsActions(
    val sync: (BankSmsBank) -> Unit,
    val login: (BankSmsBank) -> Unit,
    val sms: (BankSmsBank, Boolean) -> Unit,
    val push: (Boolean) -> Unit,
    val smsPermission: () -> Unit,
    val pushPermission: () -> Unit,
    val messages: (BankSmsBank?) -> Unit,
    val statements: () -> Unit,
    val journal: () -> Unit,
    val account: (Long) -> Unit,
)

@Composable
internal fun ConnectionsSettings(state: ConnectionSettingsState, navigation: SettingsSearchState,
    actions: ConnectionSettingsActions, demoMode: Boolean) {
    if (demoMode) { Text(stringResource(R.string.demo_mode_live_import_unavailable), Modifier.padding(20.dp)); return }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val page = navigation.page
    val bank = BankSmsBank.entries.firstOrNull { page == "bank:${it.name}" }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (demoMode) Text(stringResource(R.string.demo_mode_live_import_unavailable))
        if (bank == null) {
            val available = BankSmsBank.entries.filterNot(state::configured)
            val displayed = if (page == "add-bank") available else BankSmsBank.entries.filter(state::configured)
            if (state.accounts == null) WhfinLoadingIndicator()
            else if (displayed.isEmpty() && page != "add-bank") Text(stringResource(R.string.settings_no_connections))
            displayed.forEach { item ->
                WhfinLedgerGroup(Modifier.fillMaxWidth().testTag("connection-${item.name}").clip(MaterialTheme.shapes.large).clickable { haptics.performHapticFeedback(WhfinHaptics.navigation); navigation.open("bank:${item.name}") }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            dev.whekin.whfin.ui.banks.BankBrand(item.provider)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(connectionSummary(item, state), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (page != "add-bank") {
                if (available.isNotEmpty()) WhfinButton(stringResource(R.string.settings_add_bank), { haptics.performHapticFeedback(WhfinHaptics.navigation); navigation.open("add-bank") }, Modifier.fillMaxWidth(), enabled = !demoMode)
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    WhfinLedgerRow(stringResource(R.string.statements_upload), icon = Icons.Outlined.Description,
                        onClick = actions.statements, divider = true, trailing = { Icon(Icons.Default.ChevronRight, null) })
                    WhfinLedgerRow(stringResource(R.string.sms_diagnostics_title), icon = Icons.Outlined.Sms,
                        onClick = { actions.messages(null) }, trailing = { Icon(Icons.Default.ChevronRight, null) })
                }
                WhfinSectionLabel(stringResource(R.string.settings_shared_permissions))
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    WhfinLedgerRow("SMS", icon = Icons.Outlined.Sms,
                        supportingText = stringResource(if (state.smsPermission) R.string.settings_access_granted else R.string.settings_access_needed),
                        onClick = actions.smsPermission, divider = true, trailing = { Icon(Icons.Outlined.OpenInNew, null) })
                    WhfinLedgerRow(stringResource(R.string.settings_notifications), icon = Icons.Outlined.Notifications,
                        supportingText = stringResource(if (state.pushPermission) R.string.settings_access_granted else R.string.settings_access_needed),
                        onClick = actions.pushPermission, trailing = { Icon(Icons.Outlined.OpenInNew, null) })
                }
            }
        } else {
            Text(connectionSummary(bank, state), style = MaterialTheme.typography.titleMedium)
            WhfinButton(stringResource(if (bank in state.remembered || state.lastSync[bank] != null) R.string.settings_update_operations else R.string.settings_connect_bank),
                { actions.sync(bank) }, Modifier.fillMaxWidth(), enabled = !demoMode, leadingIcon = Icons.Outlined.Sync)
            WhfinSectionLabel(stringResource(R.string.settings_operation_sources))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bank == BankSmsBank.TBC) {
                        val pushState = stringResource(when {
                            !state.pushEnabled -> R.string.push_off
                            !state.pushPermission -> R.string.push_permission_missing
                            !state.pushConnected -> R.string.push_connecting
                            else -> R.string.settings_push_receiving
                        })
                        BankChannelSwitch(stringResource(R.string.settings_bank_push), state.pushEnabled, true, actions.push, pushState, icon = Icons.Outlined.Notifications)
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.lastPush != null) Text(stringResource(R.string.settings_last_notification, connectionTime(state.lastPush)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.settings_push_brief), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            var explain by remember { mutableStateOf(false) }
                            WhfinButton(stringResource(R.string.settings_how_it_works), { explain = !explain }, style = WhfinActionStyle.Quiet)
                            if (explain) Text(stringResource(R.string.push_explanation) + "\n" + stringResource(R.string.push_retention), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.unknownPush > 0 || state.pushError) WhfinLedgerRow(
                            stringResource(if (state.pushError) R.string.settings_capture_attention else R.string.settings_push_attention, state.unknownPush),
                            onClick = actions.journal, divider = true, trailing = { Icon(Icons.Default.ChevronRight, null) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    BankChannelSwitch(stringResource(R.string.settings_bank_sms), state.sms[bank] == true, true, { actions.sms(bank, it) }, icon = Icons.Outlined.Sms)
                    if (state.sms[bank] == true && !state.smsPermission) WhfinButton(stringResource(R.string.permission_allow), actions.smsPermission, style = WhfinActionStyle.Quiet)
                    WhfinLedgerRow(stringResource(R.string.sms_diagnostics_title), icon = Icons.Outlined.Sms,
                        onClick = { actions.messages(bank) }, trailing = { Icon(Icons.Default.ChevronRight, null) })
                }
            }
            WhfinSectionLabel(stringResource(R.string.settings_linked_accounts))
            val accounts = state.accounts?.get(bank)
            if (state.accounts == null) WhfinLoadingIndicator()
            else if (accounts.isNullOrEmpty()) Text(stringResource(R.string.settings_bank_no_accounts))
            accounts.orEmpty().groupBy { it.iban ?: it.id.toString() }.values.forEach { ledgers ->
                val first = ledgers.first()
                Text(dev.whekin.whfin.ui.accountNumberLabel(first) ?: first.name, style = MaterialTheme.typography.titleSmall)
                WhfinChoiceRail {
                    ledgers.sortedBy { if (it.currency == "GEL") "" else it.currency }.forEach { ledger -> item(key = ledger.id) {
                        WhfinButton(ledger.currency, { actions.account(ledger.id) }, Modifier.testTag("connection-account-${ledger.id}"), style = WhfinActionStyle.Secondary)
                    } }
                }
            }
            HorizontalDivider()
            WhfinLedgerRow(stringResource(R.string.settings_bank_sign_in), icon = Icons.Outlined.Key,
                supportingText = stringResource(if (bank in state.remembered) R.string.settings_sign_in_saved else R.string.settings_sign_in_not_saved),
                onClick = { actions.login(bank) }, trailing = { Icon(Icons.Default.ChevronRight, null) })
            WhfinLedgerRow(stringResource(R.string.settings_bank_diagnostics), icon = Icons.Outlined.ManageSearch, onClick = {
                if (bank == BankSmsBank.TBC) actions.journal() else actions.messages(bank)
            }, trailing = { Icon(Icons.Default.ChevronRight, null) })
        }
    }
}

@Composable
private fun BankChannelSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit, supporting: String? = null, icon: ImageVector? = null) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    WhfinLedgerRow(label, icon = icon, supportingText = supporting, modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) {
        haptics.performHapticFeedback(WhfinHaptics.toggle(it)); change(it)
    }.semantics { contentDescription = label }, trailing = { WhfinSwitch(checked, null, contentDescription = label) })
}

@Composable
private fun connectionSummary(bank: BankSmsBank, state: ConnectionSettingsState): String = when {
    bank == BankSmsBank.TBC && state.pushError -> stringResource(R.string.settings_capture_attention)
    (state.sms[bank] == true && !state.smsPermission) || (bank == BankSmsBank.TBC && state.pushEnabled && !state.pushPermission) -> stringResource(R.string.settings_access_needed)
    bank == BankSmsBank.TBC && state.unknownPush > 0 -> stringResource(R.string.settings_push_attention, state.unknownPush)
    state.lastSync[bank] != null -> stringResource(R.string.settings_bank_updated, connectionTime(requireNotNull(state.lastSync[bank])))
    bank in state.remembered -> stringResource(R.string.settings_sign_in_saved)
    state.sms[bank] == true || (bank == BankSmsBank.TBC && state.pushEnabled) -> stringResource(R.string.settings_messages_only)
    else -> stringResource(R.string.settings_bank_not_connected)
}

private fun connectionTime(value: Long) = java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM · HH:mm"))

@androidx.compose.ui.tooling.preview.Preview(name = "Bank settings", showBackground = true, heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "Bank settings dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "Bank settings compact large", fontScale = 1.5f, heightDp = 500)
@Composable
private fun BankConnectionPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    Surface {
        val navigation = rememberSettingsSearchState().apply { page = "bank:TBC" }
        ConnectionsSettings(ConnectionSettingsState(accounts = mapOf(BankSmsBank.TBC to listOf(
            AccountEntity(id = 1, name = "Everyday", type = dev.whekin.whfin.data.db.AccountType.BANK, currency = "GEL", iban = "GE00TB0000000000000001"))),
            remembered = setOf(BankSmsBank.TBC), pushEnabled = true, pushPermission = true, pushConnected = true), navigation,
            ConnectionSettingsActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}), false)
    }
}
