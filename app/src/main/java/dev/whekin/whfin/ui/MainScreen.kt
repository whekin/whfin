package dev.whekin.whfin.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet as FilledAccountBalanceWallet
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.accounts.AccountsScreen
import dev.whekin.whfin.ui.accounts.AccountOverviewScreen
import dev.whekin.whfin.ui.accounts.AccountTransactionsScreen
import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
import dev.whekin.whfin.ui.analytics.AnalyticsScale
import dev.whekin.whfin.ui.analytics.AnalyticsScreen
import dev.whekin.whfin.ui.analytics.ExpenseAnalysisScreen
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsRequest
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsScreen
import dev.whekin.whfin.ui.components.LedgerIconButton
import dev.whekin.whfin.core.ui.WhfinDock
import dev.whekin.whfin.core.ui.WhfinDockDestination
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.core.ui.WhfinHaptics
import dev.whekin.whfin.core.ui.WhfinBackButton
import androidx.activity.compose.BackHandler
import dev.whekin.whfin.ui.feed.FeedScreen
import dev.whekin.whfin.ui.feed.FeedMode
import dev.whekin.whfin.ui.feed.FeedViewModel
import dev.whekin.whfin.ui.settings.BankStatementsScreen
import dev.whekin.whfin.ui.settings.SettingsScreen
import dev.whekin.whfin.ui.settings.SmsDiagnosticsRoute
import dev.whekin.whfin.ui.settings.AboutScreen
import dev.whekin.whfin.ui.settings.BackupRoute
import dev.whekin.whfin.ui.settings.AppLockScreen
import dev.whekin.whfin.ui.settings.PrivacyRoute
import dev.whekin.whfin.ui.settings.CredoSyncRoute
import dev.whekin.whfin.ui.settings.CategoriesRoute
import dev.whekin.whfin.ui.settings.PeopleRoute
import dev.whekin.whfin.ui.settings.CorrectionsScreen
import dev.whekin.whfin.ui.settings.DataHealthRoute
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame
import dev.whekin.whfin.ui.demo.DemoWorkspaceProvider
import java.time.YearMonth
import androidx.core.content.pm.PackageInfoCompat

private val AnalyticsTransactionsRequestSaver = listSaver<AnalyticsTransactionsRequest?, Any>(
    save = { request ->
        if (request == null) listOf(false) else listOf(
            true,
            request.period.month.toString(),
            request.categoryFilterEnabled,
            request.categoryId ?: Long.MIN_VALUE,
            request.filterName,
            request.expectedExpenseMinor,
            request.period.scale.name,
        )
    },
    restore = { values ->
        if (values.first() == false) null else AnalyticsTransactionsRequest(
            period = AnalyticsPeriod(
                scale = AnalyticsScale.valueOf(values[6] as String),
                month = YearMonth.parse(values[1] as String),
            ),
            categoryFilterEnabled = values[2] as Boolean,
            categoryId = (values[3] as Long).takeUnless { it == Long.MIN_VALUE },
            filterName = values[4] as String,
            expectedExpenseMinor = values[5] as Long,
        )
    },
)

internal enum class SecondaryDestination { TransactionHistory, Settings, CredoSync, Statements, SmsDiagnostics, AccountOverview, AccountTransactions, Analytics, AnalyticsExpenses, AppLock, Backup, Corrections, DataHealth, Privacy, About, Categories, People }

internal enum class ShellScene(val depth: Int) {
    Primary(0),
    TransactionHistory(1),
    Settings(1),
    CredoSync(2),
    Statements(2),
    SmsDiagnostics(2),
    AccountOverview(1),
    AccountTransactions(1),
    Analytics(1),
    AnalyticsExpenses(2),
    AnalyticsTransactions(3),
    AppLock(2),
    Backup(2),
    Corrections(2),
    DataHealth(2),
    Privacy(2),
    About(2),
    Categories(2),
    People(2),
}

/**
 * A scene plus the arguments it was opened with. The payload travels inside the animated target
 * state on purpose: a scene that is sliding away must keep drawing the account or the month it was
 * showing, and reading the arguments from surrounding state would blank it out the moment Back
 * clears them.
 */
internal data class ShellTarget(
    val scene: ShellScene,
    val accountId: Long? = null,
    val analytics: AnalyticsTransactionsRequest? = null,
)

internal fun shellTargetFor(
    secondaryDestination: SecondaryDestination?,
    accountTransactionsId: Long?,
    analyticsTransactions: AnalyticsTransactionsRequest?,
): ShellTarget = when {
    analyticsTransactions != null -> ShellTarget(
        ShellScene.AnalyticsTransactions,
        analytics = analyticsTransactions,
    )
    secondaryDestination == null -> ShellTarget(ShellScene.Primary)
    secondaryDestination == SecondaryDestination.AccountTransactions -> ShellTarget(
        ShellScene.AccountTransactions,
        accountId = accountTransactionsId,
    )
    else -> ShellTarget(
        when (secondaryDestination) {
            SecondaryDestination.TransactionHistory -> ShellScene.TransactionHistory
            SecondaryDestination.Settings -> ShellScene.Settings
            SecondaryDestination.CredoSync -> ShellScene.CredoSync
            SecondaryDestination.Statements -> ShellScene.Statements
            SecondaryDestination.SmsDiagnostics -> ShellScene.SmsDiagnostics
            SecondaryDestination.AccountOverview -> ShellScene.AccountOverview
            SecondaryDestination.Analytics -> ShellScene.Analytics
            SecondaryDestination.AnalyticsExpenses -> ShellScene.AnalyticsExpenses
            SecondaryDestination.AppLock -> ShellScene.AppLock
            SecondaryDestination.Backup -> ShellScene.Backup
            SecondaryDestination.Corrections -> ShellScene.Corrections
            SecondaryDestination.DataHealth -> ShellScene.DataHealth
            SecondaryDestination.Privacy -> ShellScene.Privacy
            SecondaryDestination.About -> ShellScene.About
            SecondaryDestination.Categories -> ShellScene.Categories
            SecondaryDestination.People -> ShellScene.People
            SecondaryDestination.AccountTransactions -> ShellScene.AccountTransactions
        },
    )
}

/**
 * Only a shallower destination reads as a return. Equal depths mean one peer replaced another,
 * which still pushes forward — a backward slide there would claim the user went up a level.
 */
internal fun shellTransitionIsForward(from: ShellTarget, to: ShellTarget): Boolean =
    to.scene.depth >= from.scene.depth

internal fun appLockReturnDestination(
    caller: SecondaryDestination?,
): SecondaryDestination = caller ?: SecondaryDestination.Settings

/** Preserves the actual caller instead of treating every Credo visit as Settings-owned. */
internal fun credoBackDestination(caller: SecondaryDestination?): SecondaryDestination? =
    caller

internal data class SecondaryBackResult(
    val destination: SecondaryDestination?,
    val remaining: List<SecondaryDestination>,
)

internal fun pushSecondaryDestination(
    current: SecondaryDestination?,
    backStack: List<SecondaryDestination>,
    destination: SecondaryDestination,
): List<SecondaryDestination> = if (current == null || current == destination) {
    backStack
} else {
    backStack + current
}

internal fun popSecondaryDestination(
    backStack: List<SecondaryDestination>,
): SecondaryBackResult = SecondaryBackResult(
    destination = backStack.lastOrNull(),
    remaining = if (backStack.isEmpty()) emptyList() else backStack.dropLast(1),
)

internal fun openCredoSetup(
    enableSmsMonitoring: () -> Unit,
    openCredo: () -> Unit,
) {
    enableSmsMonitoring()
    openCredo()
}

@Composable
fun MainScreen(
    initialTab: Int = 0,
    initialAccountAddRequest: Boolean = false,
    appThemeMode: AppThemeMode,
    dynamicColorsEnabled: Boolean,
    useSystemFont: Boolean,
    quickExpenseKeypadEnabled: Boolean,
    widgetOpenAppButtonEnabled: Boolean,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    onUseSystemFontChange: (Boolean) -> Unit,
    onQuickExpenseKeypadEnabledChange: (Boolean) -> Unit,
    onWidgetOpenAppButtonEnabledChange: (Boolean) -> Unit,
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
    hasLowBalanceNotificationPermission: Boolean,
    onRequestLowBalanceNotificationPermission: () -> Unit,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean,
    biometricAvailability: BiometricAvailability,
    biometricUnlockEnabled: Boolean,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onAppLockPinCreated: (String, AppLockTimeout) -> Unit,
    onBiometricUnlockEnabledChange: (Boolean) -> Unit,
    onOpenBiometricSettings: () -> Unit,
    demoMode: Boolean = false,
    developerMode: Boolean = false,
    runtimeModeBusy: Boolean = false,
    runtimeModeProblem: String? = null,
    onEnterDemo: () -> Unit = {},
    onExitDemo: () -> Unit = {},
    onResetDemoData: () -> Unit = {},
    onDeveloperModeChange: (Boolean) -> Unit = {},
    feedViewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    LaunchedEffect(initialTab) { tab = initialTab.coerceIn(0, 1) }
    var accountAddRequestKey by rememberSaveable {
        mutableIntStateOf(if (initialAccountAddRequest) 1 else 0)
    }
    var addRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var secondaryDestination by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var secondaryBackStack by rememberSaveable {
        mutableStateOf<List<SecondaryDestination>>(emptyList())
    }
    var appLockReturnTo by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var credoReturnTo by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var credoRoutineSyncRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var analyticsTransactions by rememberSaveable(stateSaver = AnalyticsTransactionsRequestSaver) {
        mutableStateOf<AnalyticsTransactionsRequest?>(null)
    }
    var accountTransactionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val target = shellTargetFor(secondaryDestination, accountTransactionsId, analyticsTransactions)
    val scene = target.scene
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
        secondaryBackStack = pushSecondaryDestination(
            current = secondaryDestination,
            backStack = secondaryBackStack,
            destination = destination,
        )
        analyticsTransactions = null
        accountTransactionsId = null
        secondaryDestination = destination
    }

    fun openAccountTransactions(accountId: Long) {
        haptics.performHapticFeedback(WhfinHaptics.navigation)
        secondaryBackStack = pushSecondaryDestination(
            current = secondaryDestination,
            backStack = secondaryBackStack,
            destination = SecondaryDestination.AccountTransactions,
        )
        analyticsTransactions = null
        accountTransactionsId = accountId
        secondaryDestination = SecondaryDestination.AccountTransactions
    }

    fun openAppLock(returnTo: SecondaryDestination?) {
        appLockReturnTo = returnTo
        open(SecondaryDestination.AppLock)
    }

    fun enableSmsMonitoring() {
        onSmsImportEnabledChange(true)
        if (!hasSmsPermission) {
            if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings()
        }
    }

    fun openCredo(caller: SecondaryDestination?, syncLatest: Boolean) {
        credoReturnTo = caller
        if (syncLatest) credoRoutineSyncRequestKey += 1
        openCredoSetup(
            enableSmsMonitoring = ::enableSmsMonitoring,
            openCredo = { open(SecondaryDestination.CredoSync) },
        )
    }

    fun goBack(withHaptic: Boolean) {
        if (withHaptic) haptics.performHapticFeedback(WhfinHaptics.navigation)
        when {
            analyticsTransactions != null -> analyticsTransactions = null
            secondaryDestination != null -> {
                val leaving = secondaryDestination
                val back = popSecondaryDestination(secondaryBackStack)
                secondaryDestination = back.destination
                secondaryBackStack = back.remaining
                if (leaving == SecondaryDestination.AccountTransactions) accountTransactionsId = null
                if (leaving == SecondaryDestination.AppLock) appLockReturnTo = null
                if (leaving == SecondaryDestination.CredoSync) {
                    credoReturnTo = null
                    credoRoutineSyncRequestKey = 0
                }
            }
        }
    }
    BackHandler(enabled = scene != ShellScene.Primary) { goBack(withHaptic = false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        DemoWorkspaceProvider(
            active = demoMode,
            busy = runtimeModeBusy,
            problem = runtimeModeProblem,
            onUsePersonal = onExitDemo,
        ) {
            DemoWorkspaceFrame {
                AnimatedContent(
                    targetState = target,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // A destination's first frame is expensive, and a full-width push loses a
                        // visible chunk of its travel to that frame, which reads as a stutter.
                        // A short directional shift under a fade keeps the direction legible even
                        // when the first frames are dropped.
                        val forward = shellTransitionIsForward(initialState, targetState)
                        val enter = fadeIn(WhfinMotion.standard()) +
                            slideInHorizontally(WhfinMotion.standard()) { width ->
                                if (forward) width / 8 else -width / 8
                            }
                        val exit = fadeOut(WhfinMotion.quick()) +
                            slideOutHorizontally(WhfinMotion.standard()) { width ->
                                if (forward) -width / 8 else width / 8
                            }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = if (forward) 1f else -1f
                        }.using(SizeTransform(clip = false))
                    },
                    label = "app-destination",
                ) { targetShell ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (targetShell.scene) {
                            ShellScene.Primary -> Column(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = tab,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            transitionSpec = {
                                // Dock destinations are siblings, not a hierarchy: they trade
                                // places with a short directional settle instead of pushing.
                                val forward = targetState > initialState
                                val enter = fadeIn(WhfinMotion.paneEnter()) +
                                    slideInHorizontally(WhfinMotion.paneEnter()) { width ->
                                        if (forward) width / 24 else -width / 24
                                    }
                                (enter togetherWith fadeOut(WhfinMotion.paneExit()))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "primary-pane",
                        ) { currentTab ->
                            if (currentTab == 0) FeedScreen(
                                mode = FeedMode.HOME,
                                showSmsOnboarding = smsImportEnabled && !hasSmsPermission && !smsPermissionPromptDismissed,
                                onEnableSms = if (canRequestSmsPermission) onRequestSmsPermission else onOpenSystemSettings,
                                onDismissSmsOnboarding = onDismissSmsPermissionPrompt,
                                showCredoSyncReminder = !demoMode,
                                onOpenAnalytics = { open(SecondaryDestination.Analytics) },
                                onOpenHistory = { open(SecondaryDestination.TransactionHistory) },
                                onOpenDataHealth = { open(SecondaryDestination.DataHealth) },
                                onOpenCredoSync = { openCredo(caller = null, syncLatest = true) },
                                onOpenAccounts = { tab = 1 },
                                hasLowBalanceNotificationPermission = demoMode || hasLowBalanceNotificationPermission,
                                onRequestLowBalanceNotificationPermission = onRequestLowBalanceNotificationPermission,
                                addRequestKey = addRequestKey,
                                onAddRequestConsumed = { addRequestKey = 0 },
                                viewModel = feedViewModel,
                            ) else AccountsScreen(
                                addRequestKey = accountAddRequestKey,
                                onAddRequestConsumed = { accountAddRequestKey = 0 },
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
                    ShellScene.TransactionHistory -> SecondaryPage(
                        title = stringResource(R.string.transactions_history_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        FeedScreen(
                            mode = FeedMode.HISTORY,
                            showSmsOnboarding = false,
                            onEnableSms = {},
                            onDismissSmsOnboarding = {},
                            viewModel = feedViewModel,
                        )
                    }
                    ShellScene.Settings -> SecondaryPage(
                        title = stringResource(R.string.settings_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        SettingsScreen(
                            appThemeMode = appThemeMode,
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            useSystemFont = useSystemFont,
                            quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
                            widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
                            onAppThemeModeChange = onAppThemeModeChange,
                            onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
                            onUseSystemFontChange = onUseSystemFontChange,
                            onQuickExpenseKeypadEnabledChange = onQuickExpenseKeypadEnabledChange,
                            onWidgetOpenAppButtonEnabledChange = onWidgetOpenAppButtonEnabledChange,
                            smsImportEnabled = smsImportEnabled,
                            hasSmsCardMapping = hasSmsCardMapping,
                            hasSmsPermission = hasSmsPermission,
                            canRequestSmsPermission = canRequestSmsPermission,
                            onSmsImportEnabledChange = onSmsImportEnabledChange,
                            onRequestSmsPermission = onRequestSmsPermission,
                            onOpenSystemSettings = onOpenSystemSettings,
                            onOpenStatements = { open(SecondaryDestination.Statements) },
                            onOpenSmsDiagnostics = { open(SecondaryDestination.SmsDiagnostics) },
                            onOpenCredoSync = {
                                openCredo(
                                    caller = SecondaryDestination.Settings,
                                    syncLatest = false,
                                )
                            },
                            appLockTimeout = appLockTimeout,
                            onOpenAppLock = { openAppLock(returnTo = null) },
                            onOpenBackup = { open(SecondaryDestination.Backup) },
                            onOpenCorrections = { open(SecondaryDestination.Corrections) },
                            onOpenDataHealth = { open(SecondaryDestination.DataHealth) },
                            onOpenPrivacy = { open(SecondaryDestination.Privacy) },
                            onOpenAbout = { open(SecondaryDestination.About) },
                            onOpenCategories = { open(SecondaryDestination.Categories) },
                            onOpenPeople = { open(SecondaryDestination.People) },
                            appVersion = appVersion,
                            demoMode = demoMode,
                            developerMode = developerMode,
                            runtimeModeBusy = runtimeModeBusy,
                            runtimeModeProblem = runtimeModeProblem,
                            onEnterDemo = onEnterDemo,
                            onResetDemoData = onResetDemoData,
                        )
                    }
                    ShellScene.CredoSync -> SecondaryPage(
                        title = stringResource(R.string.credo_sync_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        CredoSyncRoute(
                            appLockEnabled = appLockHasPin && appLockTimeout.enabled,
                            onOpenAppLock = { openAppLock(SecondaryDestination.CredoSync) },
                            routineSyncRequestKey = credoRoutineSyncRequestKey,
                            onRoutineSyncRequestConsumed = { credoRoutineSyncRequestKey = 0 },
                            showCredentialManagement = credoReturnTo == SecondaryDestination.Settings,
                            onDone = { goBack(withHaptic = true) },
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
                            appVersion = portableAppVersion,
                            smsImportEnabled = smsImportEnabled,
                            hasReceivePermission = hasSmsPermission,
                            canRequestReceivePermission = canRequestSmsPermission,
                            hasHistoryPermission = hasSmsHistoryPermission,
                            canRequestHistoryPermission = canRequestSmsHistoryPermission,
                            onEnableMonitoring = ::enableSmsMonitoring,
                            onRequestReceivePermission = onRequestSmsPermission,
                            onOpenFeed = {
                                haptics.performHapticFeedback(WhfinHaptics.navigation)
                                tab = 0
                                secondaryDestination = null
                                secondaryBackStack = emptyList()
                            },
                            onRequestHistoryPermission = onRequestSmsHistoryPermission,
                            onOpenSystemSettings = onOpenSystemSettings,
                        )
                    }
                    ShellScene.AccountOverview -> SecondaryPage(
                        title = stringResource(R.string.account_overview_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { AccountOverviewScreen() }
                    ShellScene.AccountTransactions -> targetShell.accountId?.let { accountId ->
                        AccountTransactionsScreen(
                            accountId = accountId,
                            onBack = { goBack(withHaptic = true) },
                            feedViewModel = feedViewModel,
                        )
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
                            onPinCreated = { pin, timeout ->
                                onAppLockPinCreated(pin, timeout)
                                goBack(withHaptic = false)
                            },
                            onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                            onOpenBiometricSettings = onOpenBiometricSettings,
                        )
                    }
                    ShellScene.Backup -> SecondaryPage(
                        title = stringResource(R.string.backup_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { BackupRoute(appVersion = portableAppVersion) }
                    ShellScene.Corrections -> SecondaryPage(
                        title = stringResource(R.string.corrections_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { CorrectionsScreen() }
                    ShellScene.DataHealth -> SecondaryPage(
                        title = stringResource(R.string.data_health_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        DataHealthRoute(
                            onOpenCorrections = { open(SecondaryDestination.Corrections) },
                            onOpenBackup = { open(SecondaryDestination.Backup) },
                        )
                    }
                    ShellScene.Privacy -> SecondaryPage(
                        title = stringResource(R.string.privacy_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { PrivacyRoute(onOpenSystemSettings = onOpenSystemSettings) }
                    ShellScene.About -> SecondaryPage(
                        title = stringResource(R.string.about_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        AboutScreen(
                            appVersion = appVersion,
                            developerMode = developerMode,
                            onDeveloperModeChange = onDeveloperModeChange,
                        )
                    }
                    ShellScene.Categories -> SecondaryPage(
                        title = stringResource(R.string.categories_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { CategoriesRoute() }
                    ShellScene.People -> SecondaryPage(
                        title = stringResource(R.string.people_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { PeopleRoute() }
                    ShellScene.Analytics -> AnalyticsScreen(
                        onBack = { goBack(withHaptic = true) },
                        onOpenExpenses = { open(SecondaryDestination.AnalyticsExpenses) },
                        onOpenTransactions = { request ->
                            haptics.performHapticFeedback(WhfinHaptics.navigation)
                            analyticsTransactions = request
                        },
                    )
                    ShellScene.AnalyticsExpenses -> ExpenseAnalysisScreen(
                        onBack = { goBack(withHaptic = true) },
                        onOpenTransactions = { request ->
                            haptics.performHapticFeedback(WhfinHaptics.navigation)
                            analyticsTransactions = request
                        },
                    )
                    ShellScene.AnalyticsTransactions -> targetShell.analytics?.let { request ->
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
    WhfinDock(
        leading = WhfinDockDestination(
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.FilledHome,
            label = stringResource(R.string.tab_feed),
            testTag = "dock-feed",
        ),
        trailing = WhfinDockDestination(
            icon = Icons.Outlined.AccountBalanceWallet,
            selectedIcon = Icons.Filled.FilledAccountBalanceWallet,
            label = stringResource(R.string.tab_accounts),
            testTag = "dock-accounts",
        ),
        selectedIndex = selected,
        addLabel = stringResource(R.string.dock_add),
        addContentDescription = stringResource(R.string.add_transaction),
        onAdd = onAdd,
        onSelect = onSelect,
    )
}

@Preview(name = "Dock light", widthDp = 400, heightDp = 96, showBackground = true)
@Preview(name = "Dock dark", widthDp = 400, heightDp = 96, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dock font 1.5", widthDp = 400, heightDp = 116, fontScale = 1.5f, showBackground = true)
@Composable
private fun LedgerDockPreview() {
    WhfinTheme { LedgerDock(selected = 0, onAdd = {}, onSelect = {}) }
}
