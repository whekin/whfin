package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.whekin.whfin.data.preferences.UiPreferences
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.whekin.whfin.R
import dev.whekin.whfin.data.crypto.CryptoEndpoints
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinSwitch
import dev.whekin.whfin.ui.theme.WhfinTheme

/** Keeps the endpoint preference next to the screen that explains it, without widening the shell. */
@Composable
fun PrivacyRoute(onOpenSystemSettings: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UiPreferences(context) }
    val endpoints by preferences.cryptoEndpoints.collectAsState(initial = CryptoEndpoints())
    val scope = rememberCoroutineScope()
    PrivacyScreen(
        onOpenSystemSettings = onOpenSystemSettings,
        endpoints = endpoints,
        onEndpointsChange = { scope.launch { preferences.setCryptoEndpoints(it) } },
    )
}

@Composable
fun PrivacyScreen(
    onOpenSystemSettings: () -> Unit,
    endpoints: CryptoEndpoints = CryptoEndpoints(),
    onEndpointsChange: (CryptoEndpoints) -> Unit = {},
) {
    var editEndpoints by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.privacy_intro_title),
            body = stringResource(R.string.privacy_intro_body),
            icon = Icons.Default.PrivacyTip,
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
        )

        WhfinSectionLabel(stringResource(R.string.privacy_data_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_financial_title),
                supportingText = stringResource(R.string.privacy_financial_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Storage,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_backup_title),
                supportingText = stringResource(R.string.privacy_backup_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Backup,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_export_title),
                supportingText = stringResource(R.string.privacy_export_body),
                supportingMaxLines = 6,
                icon = Icons.Default.SaveAlt,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_app_lock_title),
                supportingText = stringResource(R.string.privacy_app_lock_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Lock,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_sms_title),
                supportingText = stringResource(R.string.privacy_sms_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Sms,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_files_title),
                supportingText = stringResource(R.string.privacy_files_body),
                supportingMaxLines = 6,
                icon = Icons.Default.FolderOpen,
            )
        }

        WhfinSectionLabel(stringResource(R.string.privacy_control_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_permissions_title),
                supportingText = stringResource(R.string.privacy_permissions_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Settings,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenSystemSettings,
            )
        }

        WhfinSectionLabel(stringResource(R.string.privacy_network_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.crypto_endpoints_title),
                supportingText = stringResource(R.string.crypto_endpoints_summary),
                supportingMaxLines = 4,
                icon = Icons.Default.Language,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = { editEndpoints = true },
            )
        }

        WhfinNotice(
            title = stringResource(R.string.privacy_network_title),
            body = stringResource(R.string.privacy_network_body),
            icon = Icons.Default.AccountBalanceWallet,
            kind = WhfinNoticeKind.Unavailable,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (editEndpoints) CryptoEndpointsSheet(
        endpoints = endpoints,
        onDismiss = { editEndpoints = false },
        onConfirm = {
            onEndpointsChange(it)
            editEndpoints = false
        },
    )
}

/**
 * Endpoints are plain text on purpose: the person can see exactly which host will learn that this
 * device asked about their address, and can point WHFIN at their own node instead.
 */
@Composable
private fun CryptoEndpointsSheet(
    endpoints: CryptoEndpoints,
    onDismiss: () -> Unit,
    onConfirm: (CryptoEndpoints) -> Unit,
) {
    var ethereum by remember { mutableStateOf(endpoints.ethereumRpcUrl) }
    var tron by remember { mutableStateOf(endpoints.tronApiUrl) }
    fun valid(url: String) = url.isBlank() || CryptoEndpoints.isUsable(url)

    FormSheet(
        title = stringResource(R.string.crypto_endpoints_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = valid(ethereum) && valid(tron),
        onPrimary = {
            onConfirm(
                CryptoEndpoints(
                    ethereumRpcUrl = ethereum.trim().ifBlank { CryptoEndpoints.DEFAULT_ETHEREUM_RPC },
                    tronApiUrl = tron.trim().ifBlank { CryptoEndpoints.DEFAULT_TRON_API },
                ),
            )
        },
    ) {
        Text(
            stringResource(R.string.crypto_endpoints_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinField(
            value = ethereum,
            onValueChange = { ethereum = it.trim() },
            label = stringResource(R.string.crypto_endpoint_ethereum),
            supportingText = if (valid(ethereum)) CryptoEndpoints.DEFAULT_ETHEREUM_RPC
            else stringResource(R.string.crypto_endpoint_invalid),
            isError = !valid(ethereum),
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinField(
            value = tron,
            onValueChange = { tron = it.trim() },
            label = stringResource(R.string.crypto_endpoint_tron),
            supportingText = if (valid(tron)) CryptoEndpoints.DEFAULT_TRON_API
            else stringResource(R.string.crypto_endpoint_invalid),
            isError = !valid(tron),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AboutScreen(
    appVersion: String,
    developerMode: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    easterEggInitiallyUnlocked: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    var versionTaps by rememberSaveable { mutableIntStateOf(if (easterEggInitiallyUnlocked) 5 else 0) }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.about_hero_title),
            body = stringResource(R.string.about_hero_body),
            icon = Icons.Default.AccountBalanceWallet,
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
        )

        WhfinSectionLabel(stringResource(R.string.about_details_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.about_version_title),
                supportingText = appVersion,
                icon = Icons.Default.Info,
                divider = true,
                onClick = { versionTaps = (versionTaps + 1).coerceAtMost(5) },
            )
            WhfinLedgerRow(
                title = stringResource(R.string.about_author_title),
                supportingText = stringResource(R.string.about_author_value),
                icon = Icons.Default.Person,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = { uriHandler.openUri("https://github.com/whekin") },
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.about_love_title),
                supportingText = stringResource(R.string.about_love_body),
                icon = Icons.Default.Favorite,
            )
        }

        if (versionTaps >= 5) WhfinNotice(
            title = stringResource(R.string.about_easter_egg_title),
            body = stringResource(R.string.about_easter_egg_body),
            icon = Icons.Default.Code,
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                WhfinSwitch(
                    checked = developerMode,
                    onCheckedChange = onDeveloperModeChange,
                    contentDescription = stringResource(R.string.developer_mode_toggle),
                )
            },
        )

        WhfinSectionLabel(stringResource(R.string.about_open_source_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.about_open_source_title),
                supportingText = stringResource(R.string.about_open_source_body),
                supportingMaxLines = 6,
                icon = Icons.Default.Code,
            )
        }
    }
}

@Preview(name = "Privacy light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Privacy dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Privacy font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "Privacy compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun PrivacyScreenPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PrivacyScreen(onOpenSystemSettings = {})
        }
    }
}

@Preview(name = "About light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "About dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "About font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "About compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AboutScreenPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AboutScreen(
                appVersion = "Version 0.1.0 (1)",
                developerMode = true,
                easterEggInitiallyUnlocked = true,
            )
        }
    }
}
