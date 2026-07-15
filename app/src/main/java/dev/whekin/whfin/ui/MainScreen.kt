package dev.whekin.whfin.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.accounts.AccountsScreen
import dev.whekin.whfin.ui.accounts.AccountOverviewScreen
import dev.whekin.whfin.ui.accounts.AccountTransactionsScreen
import dev.whekin.whfin.ui.analytics.AnalyticsScreen
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsRequest
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsScreen
import dev.whekin.whfin.ui.components.LedgerIconButton
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.core.ui.WhfinHaptics
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.core.ui.WhfinBackButton
import androidx.activity.compose.BackHandler
import dev.whekin.whfin.ui.feed.FeedScreen
import dev.whekin.whfin.ui.feed.FeedViewModel
import dev.whekin.whfin.ui.settings.BankStatementsScreen
import dev.whekin.whfin.ui.settings.SettingsScreen
import dev.whekin.whfin.ui.settings.SmsDiagnosticsRoute
import dev.whekin.whfin.ui.settings.AboutScreen
import dev.whekin.whfin.ui.settings.BackupRoute
import dev.whekin.whfin.ui.settings.AppLockScreen
import dev.whekin.whfin.ui.settings.PrivacyScreen
import dev.whekin.whfin.ui.settings.CredoSyncRoute
import dev.whekin.whfin.ui.settings.CategoriesRoute
import dev.whekin.whfin.ui.settings.PeopleRoute
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import androidx.core.content.pm.PackageInfoCompat

private val AnalyticsTransactionsRequestSaver = listSaver<AnalyticsTransactionsRequest?, Any>(
    save = { request ->
        if (request == null) listOf(false) else listOf(
            true,
            request.month.toString(),
            request.categoryFilterEnabled,
            request.categoryId ?: Long.MIN_VALUE,
            request.filterName,
            request.expectedExpenseMinor,
        )
    },
    restore = { values ->
        if (values.first() == false) null else AnalyticsTransactionsRequest(
            month = YearMonth.parse(values[1] as String),
            categoryFilterEnabled = values[2] as Boolean,
            categoryId = (values[3] as Long).takeUnless { it == Long.MIN_VALUE },
            filterName = values[4] as String,
            expectedExpenseMinor = values[5] as Long,
        )
    },
)

private enum class SecondaryDestination { Settings, CredoSync, Statements, SmsDiagnostics, AccountOverview, AccountTransactions, Analytics, AppLock, Backup, Privacy, About, Categories, People }

private enum class ShellScene(val depth: Int) {
    Primary(0),
    Settings(1),
    CredoSync(2),
    Statements(2),
    SmsDiagnostics(2),
    AccountOverview(1),
    AccountTransactions(1),
    Analytics(1),
    AppLock(2),
    Backup(2),
    Privacy(2),
    About(2),
    Categories(2),
    People(2),
}

@Composable
fun MainScreen(
    appThemeMode: AppThemeMode,
    dynamicColorsEnabled: Boolean,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    smsImportEnabled: Boolean,
    hasSmsCardMapping: Boolean,
    hasSmsPermission: Boolean,
    canRequestSmsPermission: Boolean,
    hasSmsHistoryPermission: Boolean,
    canRequestSmsHistoryPermission: Boolean,
    smsPermissionPromptDismissed: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestSmsHistoryPermission: () -> Unit,
    onDismissSmsPermissionPrompt: () -> Unit,
    onSmsImportEnabledChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean,
    biometricAvailability: BiometricAvailability,
    biometricUnlockEnabled: Boolean,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onAppLockPinCreated: (String, AppLockTimeout) -> Unit,
    onBiometricUnlockEnabledChange: (Boolean) -> Unit,
    onOpenBiometricSettings: () -> Unit,
    feedViewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var secondaryDestination by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var analyticsTransactions by rememberSaveable(stateSaver = AnalyticsTransactionsRequestSaver) {
        mutableStateOf<AnalyticsTransactionsRequest?>(null)
    }
    var accountTransactionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scene = when {
        secondaryDestination == SecondaryDestination.Settings -> ShellScene.Settings
        secondaryDestination == SecondaryDestination.CredoSync -> ShellScene.CredoSync
        secondaryDestination == SecondaryDestination.Statements -> ShellScene.Statements
        secondaryDestination == SecondaryDestination.SmsDiagnostics -> ShellScene.SmsDiagnostics
        secondaryDestination == SecondaryDestination.AccountOverview -> ShellScene.AccountOverview
        secondaryDestination == SecondaryDestination.AccountTransactions -> ShellScene.AccountTransactions
        secondaryDestination == SecondaryDestination.Analytics -> ShellScene.Analytics
        secondaryDestination == SecondaryDestination.AppLock -> ShellScene.AppLock
        secondaryDestination == SecondaryDestination.Backup -> ShellScene.Backup
        secondaryDestination == SecondaryDestination.Privacy -> ShellScene.Privacy
        secondaryDestination == SecondaryDestination.About -> ShellScene.About
        secondaryDestination == SecondaryDestination.Categories -> ShellScene.Categories
        secondaryDestination == SecondaryDestination.People -> ShellScene.People
        else -> ShellScene.Primary
    }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val packageInfo = remember(context.packageName) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "—"
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val appVersion = stringResource(R.string.about_version_value, versionName, versionCode)
    val portableAppVersion = "$versionName ($versionCode)"

    fun open(destination: SecondaryDestination) {
        if (secondaryDestination == destination && analyticsTransactions == null) return
        haptics.performHapticFeedback(WhfinHaptics.navigation)
        analyticsTransactions = null
        accountTransactionsId = null
        secondaryDestination = destination
    }

    fun openAccountTransactions(accountId: Long) {
        haptics.performHapticFeedback(WhfinHaptics.navigation)
        analyticsTransactions = null
        accountTransactionsId = accountId
        secondaryDestination = SecondaryDestination.AccountTransactions
    }

    fun goBack(withHaptic: Boolean) {
        if (withHaptic) haptics.performHapticFeedback(WhfinHaptics.navigation)
        when {
            analyticsTransactions != null -> analyticsTransactions = null
            secondaryDestination == SecondaryDestination.AccountTransactions -> {
                accountTransactionsId = null
                secondaryDestination = null
            }
            secondaryDestination == SecondaryDestination.CredoSync ||
                secondaryDestination == SecondaryDestination.Statements ||
                secondaryDestination == SecondaryDestination.SmsDiagnostics ||
                secondaryDestination == SecondaryDestination.AppLock ||
                secondaryDestination == SecondaryDestination.Backup ||
                secondaryDestination == SecondaryDestination.Privacy ||
                secondaryDestination == SecondaryDestination.About ||
                secondaryDestination == SecondaryDestination.Categories ||
                secondaryDestination == SecondaryDestination.People -> {
                secondaryDestination = SecondaryDestination.Settings
            }
            else -> secondaryDestination = null
        }
    }
    BackHandler(enabled = scene != ShellScene.Primary) { goBack(withHaptic = false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = scene,
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                val enter = slideInHorizontally(WhfinMotion.standard()) { width ->
                    if (forward) width else -width / 5
                }
                val exit = slideOutHorizontally(WhfinMotion.standard()) { width ->
                    if (forward) -width / 5 else width
                }
                (enter togetherWith exit).apply {
                    targetContentZIndex = if (forward) 1f else -1f
                }.using(SizeTransform(clip = true))
            },
            label = "app-destination",
        ) { targetScene ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (targetScene) {
                    ShellScene.Primary -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (tab == 0) FeedScreen(
                                showSmsOnboarding = smsImportEnabled && !hasSmsPermission && !smsPermissionPromptDismissed,
                                onEnableSms = if (canRequestSmsPermission) onRequestSmsPermission else onOpenSystemSettings,
                                onDismissSmsOnboarding = onDismissSmsPermissionPrompt,
                                onOpenAnalytics = { open(SecondaryDestination.Analytics) },
                                addRequestKey = addRequestKey,
                                onAddRequestConsumed = { addRequestKey = 0 },
                                viewModel = feedViewModel,
                            ) else AccountsScreen(
                                onOpenStatements = { open(SecondaryDestination.Statements) },
                                onOpenOverview = { open(SecondaryDestination.AccountOverview) },
                                onOpenSettings = { open(SecondaryDestination.Settings) },
                                onOpenAccountTransactions = ::openAccountTransactions,
                            )
                        }
                        LedgerDock(
                            selected = tab,
                            onAdd = {
                                tab = 0
                                addRequestKey += 1
                            },
                            onSelect = { tab = it },
                        )
                    }
                    ShellScene.Settings -> SecondaryPage(
                        title = stringResource(R.string.settings_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        SettingsScreen(
                            appThemeMode = appThemeMode,
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            onAppThemeModeChange = onAppThemeModeChange,
                            onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
                            smsImportEnabled = smsImportEnabled,
                            hasSmsCardMapping = hasSmsCardMapping,
                            hasSmsPermission = hasSmsPermission,
                            canRequestSmsPermission = canRequestSmsPermission,
                            onSmsImportEnabledChange = onSmsImportEnabledChange,
                            onRequestSmsPermission = onRequestSmsPermission,
                            onOpenSystemSettings = onOpenSystemSettings,
                            onOpenStatements = { open(SecondaryDestination.Statements) },
                            onOpenSmsDiagnostics = { open(SecondaryDestination.SmsDiagnostics) },
                            onOpenCredoSync = { open(SecondaryDestination.CredoSync) },
                            appLockTimeout = appLockTimeout,
                            onOpenAppLock = { open(SecondaryDestination.AppLock) },
                            onOpenBackup = { open(SecondaryDestination.Backup) },
                            onOpenPrivacy = { open(SecondaryDestination.Privacy) },
                            onOpenAbout = { open(SecondaryDestination.About) },
                            onOpenCategories = { open(SecondaryDestination.Categories) },
                            onOpenPeople = { open(SecondaryDestination.People) },
                            appVersion = appVersion,
                        )
                    }
                    ShellScene.CredoSync -> SecondaryPage(
                        title = stringResource(R.string.credo_sync_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        CredoSyncRoute(
                            appLockHasPin = appLockHasPin,
                            onOpenAppLock = { open(SecondaryDestination.AppLock) },
                        )
                    }
                    ShellScene.Statements -> SecondaryPage(
                        title = stringResource(R.string.statements_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { BankStatementsScreen() }
                    ShellScene.SmsDiagnostics -> SecondaryPage(
                        title = stringResource(R.string.sms_diagnostics_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        SmsDiagnosticsRoute(
                            smsImportEnabled = smsImportEnabled,
                            hasReceivePermission = hasSmsPermission,
                            hasHistoryPermission = hasSmsHistoryPermission,
                            canRequestHistoryPermission = canRequestSmsHistoryPermission,
                            onRequestHistoryPermission = onRequestSmsHistoryPermission,
                            onOpenSystemSettings = onOpenSystemSettings,
                        )
                    }
                    ShellScene.AccountOverview -> SecondaryPage(
                        title = stringResource(R.string.account_overview_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { AccountOverviewScreen() }
                    ShellScene.AccountTransactions -> {
                        val accountId = accountTransactionsId
                        if (accountId != null) {
                            AccountTransactionsScreen(
                                accountId = accountId,
                                onBack = { goBack(withHaptic = true) },
                                feedViewModel = feedViewModel,
                            )
                        }
                    }
                    ShellScene.AppLock -> SecondaryPage(
                        title = stringResource(R.string.app_lock_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        AppLockScreen(
                            timeout = appLockTimeout,
                            hasPin = appLockHasPin,
                            biometricAvailability = biometricAvailability,
                            biometricEnabled = biometricUnlockEnabled,
                            onTimeoutChange = onAppLockTimeoutChange,
                            onPinCreated = onAppLockPinCreated,
                            onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                            onOpenBiometricSettings = onOpenBiometricSettings,
                        )
                    }
                    ShellScene.Backup -> SecondaryPage(
                        title = stringResource(R.string.backup_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { BackupRoute(appVersion = portableAppVersion) }
                    ShellScene.Privacy -> SecondaryPage(
                        title = stringResource(R.string.privacy_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { PrivacyScreen(onOpenSystemSettings = onOpenSystemSettings) }
                    ShellScene.About -> SecondaryPage(
                        title = stringResource(R.string.about_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { AboutScreen(appVersion = appVersion) }
                    ShellScene.Categories -> SecondaryPage(
                        title = stringResource(R.string.categories_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { CategoriesRoute() }
                    ShellScene.People -> SecondaryPage(
                        title = stringResource(R.string.people_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { PeopleRoute() }
                    ShellScene.Analytics -> Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxSize().then(
                                if (analyticsTransactions != null) Modifier.clearAndSetSemantics { }
                                else Modifier,
                            ),
                        ) {
                            AnalyticsScreen(
                                onBack = { goBack(withHaptic = true) },
                                onOpenTransactions = { request ->
                                    haptics.performHapticFeedback(WhfinHaptics.navigation)
                                    analyticsTransactions = request
                                },
                            )
                        }
                        AnimatedContent(
                            targetState = analyticsTransactions,
                            modifier = Modifier.matchParentSize(),
                            transitionSpec = {
                                val entering = targetState != null
                                val enter = if (entering) {
                                    slideInHorizontally(WhfinMotion.standard()) { it }
                                } else EnterTransition.None
                                val exit = if (!entering && initialState != null) {
                                    slideOutHorizontally(WhfinMotion.standard()) { it }
                                } else ExitTransition.None
                                (enter togetherWith exit).using(SizeTransform(clip = true))
                            },
                            label = "analytics-transactions",
                        ) { request ->
                            if (request != null) {
                                Surface(
                                    Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    AnalyticsTransactionsScreen(
                                        request = request,
                                        onBack = { goBack(withHaptic = true) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LedgerTopBar(title = title, compact = true, onBack = onBack, onSettings = null)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable private fun LedgerTopBar(title: String?, compact: Boolean, onBack: (() -> Unit)?, onSettings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            WhfinBackButton(stringResource(R.string.action_back), onBack)
            Spacer(Modifier.width(12.dp))
        }
        if (title != null) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
                maxLines = if (compact) 2 else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onSettings != null) LedgerIconButton(Icons.Default.Settings, stringResource(R.string.settings_title), onSettings)
    }
}

@Composable internal fun LedgerDock(selected: Int, onAdd: () -> Unit, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockItem(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    stringResource(R.string.tab_feed),
                    selected == 0,
                    Modifier.weight(1f).testTag("dock-feed"),
                ) { onSelect(0) }
                Surface(
                    onClick = onAdd,
                    modifier = Modifier.size(52.dp).testTag("dock-add"),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, stringResource(R.string.add_transaction), Modifier.size(24.dp))
                    }
                }
                DockItem(
                    Icons.Default.AccountBalanceWallet,
                    stringResource(R.string.tab_accounts),
                    selected == 1,
                    Modifier.weight(1f).testTag("dock-accounts"),
                ) { onSelect(1) }
            }
        }
    }
}

@Composable private fun DockItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = {
            if (!selected) haptics.performHapticFeedback(WhfinHaptics.navigation)
            onClick()
        },
        modifier = modifier.heightIn(min = WhfinThemeTokens.sizes.minTouchTarget).semantics {
            role = Role.Tab
            this.selected = selected
        },
        shape = MaterialTheme.shapes.medium,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = contentColor)
            Text(
                label,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "Dock light", widthDp = 400, heightDp = 96, showBackground = true)
@Preview(name = "Dock dark", widthDp = 400, heightDp = 96, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dock font 1.5", widthDp = 400, heightDp = 116, fontScale = 1.5f, showBackground = true)
@Composable
private fun LedgerDockPreview() {
    WhfinTheme { LedgerDock(selected = 0, onAdd = {}, onSelect = {}) }
}
