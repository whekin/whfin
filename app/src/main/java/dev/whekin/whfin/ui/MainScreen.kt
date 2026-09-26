package dev.whekin.whfin.ui

import kotlinx.coroutines.launch

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
import androidx.compose.material.icons.automirrored.filled.TrendingUp as FilledTrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ReceiptLong as FilledReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
import dev.whekin.whfin.core.ui.rememberWhfinBackGesture
import dev.whekin.whfin.core.ui.whfinPredictiveBack
import dev.whekin.whfin.ui.feed.AddTransactionSheet
import dev.whekin.whfin.ui.feed.CategoryRanker
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
import dev.whekin.whfin.ui.settings.CategoryIntelligenceRoute
import dev.whekin.whfin.ui.settings.CategoryQueue
import dev.whekin.whfin.ui.settings.categoryQueueTitle
import dev.whekin.whfin.ui.settings.IncomeSourcesRoute
import dev.whekin.whfin.ui.settings.PeopleRoute
import dev.whekin.whfin.ui.settings.CorrectionsScreen
import dev.whekin.whfin.ui.settings.DataHealthRoute
import dev.whekin.whfin.ui.savings.SavingsRoute
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame
import dev.whekin.whfin.ui.demo.DemoWorkspaceProvider
import java.time.YearMonth
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
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

/**
 * The four places the app is always one tap from, in the order the dock shows them.
 *
 * They are peers, not a hierarchy: the ledger, its full record, where the money sits and what it
 * did. Two of them used to be doors hidden behind icons beside a balance — a route you had to
 * remember rather than see. The create action lives between them in the dock and is not one of
 * them: it makes a row, it is not a place.
 */
internal enum class RootDestination(val scene: ShellScene) {
    Home(ShellScene.Home), Transactions(ShellScene.Transactions),
    Accounts(ShellScene.Accounts), Analytics(ShellScene.Analytics),
}

internal enum class SecondaryDestination(val scene: ShellScene) {
    Settings(ShellScene.Settings), CredoSync(ShellScene.CredoSync),
    TbcLogin(ShellScene.TbcLogin), PushJournal(ShellScene.PushJournal),
    Statements(ShellScene.Statements), SmsDiagnostics(ShellScene.SmsDiagnostics),
    AccountOverview(ShellScene.AccountOverview), Savings(ShellScene.Savings),
    AccountTransactions(ShellScene.AccountTransactions), AnalyticsExpenses(ShellScene.AnalyticsExpenses),
    AppLock(ShellScene.AppLock), Backup(ShellScene.Backup), Corrections(ShellScene.Corrections),
    DataHealth(ShellScene.DataHealth), Privacy(ShellScene.Privacy), About(ShellScene.About),
    Categories(ShellScene.Categories), CategoryIntelligence(ShellScene.CategoryIntelligence),
    IncomeSources(ShellScene.IncomeSources), People(ShellScene.People),
}

internal enum class ShellScene(val depth: Int) {
    Home(0),
    Transactions(0),
    Accounts(0),
    Analytics(0),
    Settings(1),
    CredoSync(2),
    TbcLogin(2),
    PushJournal(2),
    Statements(2),
    SmsDiagnostics(2),
    AccountOverview(1),
    Savings(1),
    AccountTransactions(1),
    AnalyticsExpenses(1),
    AnalyticsTransactions(2),
    AppLock(2),
    Backup(2),
    Corrections(2),
    DataHealth(2),
    Privacy(2),
    About(2),
    Categories(2),
    CategoryIntelligence(2),
    IncomeSources(2),
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
    root: RootDestination = RootDestination.Home,
): ShellTarget = when {
    analyticsTransactions != null -> ShellTarget(
        ShellScene.AnalyticsTransactions,
        analytics = analyticsTransactions,
    )
    secondaryDestination == null -> ShellTarget(root.scene)
    secondaryDestination == SecondaryDestination.AccountTransactions -> ShellTarget(
        ShellScene.AccountTransactions,
        accountId = accountTransactionsId,
    )
    else -> ShellTarget(secondaryDestination.scene)
}

/**
 * Only a shallower destination reads as a return. Equal depths mean one peer replaced another,
 * which still pushes forward — a backward slide there would claim the user went up a level.
 */
internal fun shellTransitionIsForward(from: ShellTarget, to: ShellTarget): Boolean =
    to.scene.depth >= from.scene.depth

/** Where a root sits in the dock, or null for anything that is not one. */
internal fun rootOrder(scene: ShellScene): Int? = when (scene) {
    ShellScene.Home -> 0
    ShellScene.Transactions -> 1
    ShellScene.Accounts -> 2
    ShellScene.Analytics -> 3
    else -> null
}

/**
 * Two roots are a change of subject, not a step in or out.
 *
 * They fade through each other with a small shift towards the one being opened; a full push would
 * say the reader had gone a level deeper into something, and they have not.
 */
internal fun shellTransitionIsBetweenRoots(from: ShellTarget, to: ShellTarget): Boolean =
    rootOrder(from.scene) != null && rootOrder(to.scene) != null && from.scene != to.scene

internal fun appLockReturnDestination(
    caller: SecondaryDestination?,
): SecondaryDestination = caller ?: SecondaryDestination.Settings

/** Preserves the actual caller instead of treating every Credo visit as Settings-owned. */
internal fun credoBackDestination(caller: SecondaryDestination?): SecondaryDestination? =
    caller

/**
 * Dock destinations are peers, but Android Back still returns to Home from any of them.
 *
 * One step, never a trail: tapping through the dock is browsing, not descending, so Back must not
 * replay the order the destinations happened to be visited in.
 */
internal fun rootAfterBack(current: RootDestination): RootDestination? =
    RootDestination.Home.takeIf { current != RootDestination.Home }

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


@Composable
fun MainScreen(
    initialTab: Int = 0,
    entryRequestKey: Int = 0,
    initialAccountAddRequest: Boolean = false,
    appThemeMode: AppThemeMode,
    dynamicColorsEnabled: Boolean,
    useSystemFont: Boolean,
    latinCounterparties: Boolean,
    quickExpenseKeypadEnabled: Boolean,
    widgetOpenAppButtonEnabled: Boolean,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    onUseSystemFontChange: (Boolean) -> Unit,
    onLatinCounterpartiesChange: (Boolean) -> Unit,
    onQuickExpenseKeypadEnabledChange: (Boolean) -> Unit,
    onWidgetOpenAppButtonEnabledChange: (Boolean) -> Unit,
    showSetupInvitation: Boolean = false,
    onResumeSetup: () -> Unit = {},
    onDismissSetupInvitation: () -> Unit = {},
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
    // The widget and the launcher shortcut still speak in the old two-page vocabulary: 0 is the
    // ledger, anything else is Accounts.
    fun rootFor(entryTab: Int) =
        if (entryTab == 0) RootDestination.Home else RootDestination.Accounts
    var root by rememberSaveable { mutableStateOf(rootFor(initialTab)) }
    // Restoring a saved root must not replay the original launch intent. Only a new request from
    // setup or onNewIntent may replace the destination the reader left open.
    LaunchedEffect(entryRequestKey) {
        if (entryRequestKey > 0) root = rootFor(initialTab)
    }
    // The rule under the dock travels rather than blinking, so the dock is told a position and this
    // is the only thing that still animates between destinations. The pager is gone: two of the
    // four roots answer horizontal drags of their own — analytics moves through time that way —
    // and a pager underneath would have been a second reader of the same gesture.
    val dockSelection by animateFloatAsState(
        targetValue = root.ordinal.toFloat(),
        animationSpec = WhfinMotion.standard(),
        label = "dock-selection",
    )
    // Each root keeps what the reader left there — a search, a filter, a period, a scroll position —
    // because switching destinations is not leaving them.
    var accountAddRequestKey by rememberSaveable {
        mutableIntStateOf(if (initialAccountAddRequest) 1 else 0)
    }
    var addRequestKey by rememberSaveable { mutableIntStateOf(0) }
    // The create action belongs to the shell, not to one destination. It used to send the reader to
    // Home first and leave them there afterwards, so writing something down from Accounts or from
    // analytics cost the place they were reading.
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    // "Review all" is a request, not a state: applied once when the record opens, so a later visit
    // keeps whatever filter the reader last chose.
    var historyReviewKey by rememberSaveable { mutableIntStateOf(0) }
    var historyWaitingKey by rememberSaveable { mutableIntStateOf(0) }
    /** A row the reader asked to see by id — from a Data health finding, not from the ledger. */
    var openTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var secondaryDestination by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var secondaryBackStack by rememberSaveable {
        mutableStateOf<List<SecondaryDestination>>(emptyList())
    }
    var appLockReturnTo by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var credoReturnTo by rememberSaveable { mutableStateOf<SecondaryDestination?>(null) }
    var diagnosticsBank by rememberSaveable { mutableStateOf<dev.whekin.whfin.data.sms.BankSmsBank?>(null) }
    val settingsSearchState = dev.whekin.whfin.ui.settings.rememberSettingsSearchState()
    var tbcRoutineSyncRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var credoRoutineSyncRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var analyticsTransactions by rememberSaveable(stateSaver = AnalyticsTransactionsRequestSaver) {
        mutableStateOf<AnalyticsTransactionsRequest?>(null)
    }
    var accountTransactionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Held by the shell rather than inside the screen so its Back and its title are the shell's,
    // and a queue can never be left open behind a screen the user has already exited.
    var categoryQueue by rememberSaveable { mutableStateOf<CategoryQueue?>(null) }
    val target = shellTargetFor(
        secondaryDestination,
        accountTransactionsId,
        analyticsTransactions,
        root,
    )
    val scene = target.scene
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val bankSync = (context.applicationContext as dev.whekin.whfin.WhfinApp).bankSync
    val bankStatuses by bankSync.statuses.collectAsState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(bankStatuses, demoMode) {
        if (!demoMode) for (status in bankStatuses) {
            if (status.canReturnHome && bankSync.consumeHomeHandoff(status.bank)) {
                val bankPage = if (status.bank == "Credo") SecondaryDestination.CredoSync else SecondaryDestination.TbcLogin
                if (secondaryDestination == bankPage) {
                    keyboard?.hide()
                    root = RootDestination.Home
                    secondaryDestination = null
                    secondaryBackStack = emptyList()
                    credoRoutineSyncRequestKey = 0
                    tbcRoutineSyncRequestKey = 0
                }
            }
        }
    }
    val bankPreferencesScope = androidx.compose.runtime.rememberCoroutineScope()
    val packageInfo = remember(context.packageName) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "—"
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val appVersion = stringResource(R.string.about_version_value, versionName, versionCode)
    val portableAppVersion = "$versionName ($versionCode)"

    fun open(destination: SecondaryDestination) {
        if (secondaryDestination == destination && analyticsTransactions == null) return
        if (destination == SecondaryDestination.Settings) settingsSearchState.reset()
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

    fun openSmsDiagnostics(bank: dev.whekin.whfin.data.sms.BankSmsBank? = null) {
        diagnosticsBank = bank
        open(SecondaryDestination.SmsDiagnostics)
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
        // Sync and sign-in must not silently re-enable a bank's disabled SMS channel.
        open(SecondaryDestination.CredoSync)
    }

    fun goBack(withHaptic: Boolean) {
        if (withHaptic) haptics.performHapticFeedback(WhfinHaptics.navigation)
        when {
            // A queue is a step inside its screen, so Back leaves it before leaving the screen.
            categoryQueue != null -> categoryQueue = null
            analyticsTransactions != null -> analyticsTransactions = null
            secondaryDestination == SecondaryDestination.Settings && settingsSearchState.back() -> Unit
            secondaryDestination != null -> {
                val leaving = secondaryDestination
                val back = popSecondaryDestination(secondaryBackStack)
                secondaryDestination = back.destination
                secondaryBackStack = back.remaining
                if (leaving == SecondaryDestination.SmsDiagnostics) diagnosticsBank = null
                if (leaving == SecondaryDestination.AccountTransactions) accountTransactionsId = null
                if (leaving == SecondaryDestination.AppLock) appLockReturnTo = null
                if (leaving == SecondaryDestination.CategoryIntelligence) categoryQueue = null
                if (leaving == SecondaryDestination.CredoSync) {
                    bankSync.credo.cancelSignIn()
                    credoReturnTo = null
                    credoRoutineSyncRequestKey = 0
                }
                if (leaving == SecondaryDestination.TbcLogin && !bankSync.tbc.state.value.sessionVerified) {
                    bankSync.tbc.leave()
                    bankSync.dismissIdle("TBC")
                }
            }
        }
    }
    // Leaving a screen is a pull, and so is returning to Home from another root: both are "out of
    // here", and answering them with one gesture is what makes Back feel like one thing.
    val onRoot = rootOrder(scene) != null
    val backGesture = rememberWhfinBackGesture(
        // While the composer is up it owns Back: it has unsaved work to ask about.
        enabled = !composerOpen && (!onRoot || rootAfterBack(root) != null),
    ) {
        if (onRoot) root = RootDestination.Home else goBack(withHaptic = false)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The Back pull moves everything the app is showing, workspace strip and dock included:
        // a page that insets while the furniture around it stays put reads as two applications.
        Box(Modifier.fillMaxSize().whfinPredictiveBack(backGesture)) {
        DemoWorkspaceProvider(
            active = demoMode,
            busy = runtimeModeBusy,
            problem = runtimeModeProblem,
            onUsePersonal = onExitDemo,
        ) {
            DemoWorkspaceFrame {
                ShellFrame(
                    target = target,
                    dockSelection = dockSelection,
                    onSelectRoot = { root = RootDestination.entries[it] },
                    onAdd = { composerOpen = true },
                ) { targetShell ->
                        when (targetShell.scene) {
                            ShellScene.Home, ShellScene.Transactions,
                            ShellScene.Accounts, ShellScene.Analytics,
                            ->
                        Box(Modifier.fillMaxSize()) {
                                when (targetShell.scene) {
                                    ShellScene.Home -> FeedScreen(
                                        mode = FeedMode.HOME,
                                        showSmsOnboarding = smsImportEnabled && !hasSmsPermission && !smsPermissionPromptDismissed,
                                        onEnableSms = if (canRequestSmsPermission) onRequestSmsPermission else onOpenSystemSettings,
                                        onDismissSmsOnboarding = onDismissSmsPermissionPrompt,
                                        showCredoSyncReminder = !demoMode,
                                        showSetupInvitation = showSetupInvitation,
                                        onResumeSetup = onResumeSetup,
                                        onDismissSetupInvitation = onDismissSetupInvitation,
                                        onOpenAnalytics = { root = RootDestination.Analytics },
                                        onOpenHistory = { root = RootDestination.Transactions },
                                        onWaitingBank = { historyWaitingKey += 1; root = RootDestination.Transactions },
                                        onReviewAll = {
                                            historyReviewKey += 1
                                            root = RootDestination.Transactions
                                        },
                                        onOpenDataHealth = { open(SecondaryDestination.DataHealth) },
                                        bankSyncStatuses = if (demoMode) emptyList() else bankStatuses,
                                        onOpenCredoSync = { openCredo(caller = null, syncLatest = bankStatuses.none { it.bank == "Credo" }) },
                                        onOpenTbcSync = { if (bankStatuses.none { it.bank == "TBC" }) tbcRoutineSyncRequestKey++; open(SecondaryDestination.TbcLogin) },
                                        onOpenAccounts = { root = RootDestination.Accounts },
                                        onOpenSettings = { open(SecondaryDestination.Settings) },
                                        hasLowBalanceNotificationPermission = demoMode || hasLowBalanceNotificationPermission,
                                        onRequestLowBalanceNotificationPermission = onRequestLowBalanceNotificationPermission,
                                        addRequestKey = addRequestKey,
                                        onAddRequestConsumed = { addRequestKey = 0 },
                                        viewModel = feedViewModel,
                                    )
                                    ShellScene.Transactions -> FeedScreen(
                                        mode = FeedMode.HISTORY,
                                        showSmsOnboarding = false,
                                        onEnableSms = {},
                                        onDismissSmsOnboarding = {},
                                        reviewRequestKey = historyReviewKey,
                                        waitingRequestKey = historyWaitingKey,
                                        onWaitingRequestConsumed = { historyWaitingKey = 0 },
                                        onReviewRequestConsumed = { historyReviewKey = 0 },
                                        openTransactionId = openTransactionId,
                                        onOpenTransactionConsumed = { openTransactionId = null },
                                        viewModel = feedViewModel,
                                    )
                                    ShellScene.Accounts -> AccountsScreen(
                                        onConnectBank = { bank ->
                                            if (bank == "Credo") openCredo(caller = null, syncLatest = true)
                                            else open(SecondaryDestination.TbcLogin)
                                        },
                                        addRequestKey = accountAddRequestKey,
                                        onAddRequestConsumed = { accountAddRequestKey = 0 },
                                        onOpenStatements = { open(SecondaryDestination.Statements) },
                                        onOpenOverview = { open(SecondaryDestination.AccountOverview) },
                                        onOpenSavings = { open(SecondaryDestination.Savings) },
                                        onOpenSettings = { open(SecondaryDestination.Settings) },
                                        onOpenAccountTransactions = ::openAccountTransactions,
                                    )
                                    else -> AnalyticsScreen(
                                        onBack = null,
                                        onOpenExpenses = { open(SecondaryDestination.AnalyticsExpenses) },
                                        onOpenTransactions = { request ->
                                            haptics.performHapticFeedback(WhfinHaptics.navigation)
                                            analyticsTransactions = request
                                        },
                                    )
                                }
                        }
                    ShellScene.Settings -> dev.whekin.whfin.ui.settings.SettingsPage(
                        state = settingsSearchState, onBack = { goBack(withHaptic = true) },
                    ) {
                        SettingsScreen(
                            searchState = settingsSearchState,
                            appThemeMode = appThemeMode,
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            useSystemFont = useSystemFont,
                            latinCounterparties = latinCounterparties,
                            quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
                            widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
                            onAppThemeModeChange = onAppThemeModeChange,
                            onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
                            onUseSystemFontChange = onUseSystemFontChange,
                            onLatinCounterpartiesChange = onLatinCounterpartiesChange,
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
                            onOpenSmsDiagnostics = { openSmsDiagnostics() },
                            onOpenBankMessages = { bank -> openSmsDiagnostics(bank) },
                            onOpenTbc = { open(SecondaryDestination.TbcLogin) },
                            onOpenPush = { open(SecondaryDestination.PushJournal) },
                            onSyncTbc = { tbcRoutineSyncRequestKey++; open(SecondaryDestination.TbcLogin) },
                            onSyncCredo = { openCredo(caller = SecondaryDestination.Settings, syncLatest = true) },
                            onOpenBankAccount = ::openAccountTransactions,
                            onOpenCredoSync = {
                                openCredo(
                                    caller = SecondaryDestination.Settings,
                                    syncLatest = false,
                                )
                            },
                            appLockTimeout = appLockTimeout,
                            appLockHasPin = appLockHasPin,
                            onOpenAppLock = { openAppLock(returnTo = null) },
                            onOpenBackup = { open(SecondaryDestination.Backup) },
                            onOpenCorrections = { open(SecondaryDestination.Corrections) },
                            onOpenDataHealth = { open(SecondaryDestination.DataHealth) },
                            onOpenPrivacy = { open(SecondaryDestination.Privacy) },
                            onOpenAbout = { open(SecondaryDestination.About) },
                            onOpenCategories = { open(SecondaryDestination.Categories) },
                            onOpenCategoryIntelligence = { open(SecondaryDestination.CategoryIntelligence) },
                            onOpenIncomeSources = { open(SecondaryDestination.IncomeSources) },
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
                            // A stored bank password needs a code to sit behind, not a
                            // screen-lock policy: the action gate asks for it on use.
                            canStoreCredentials = appLockHasPin,
                            onOpenAppLock = { openAppLock(SecondaryDestination.CredoSync) },
                            routineSyncRequestKey = credoRoutineSyncRequestKey,
                            onRoutineSyncRequestConsumed = { credoRoutineSyncRequestKey = 0 },
                            showCredentialManagement = credoReturnTo == SecondaryDestination.Settings,
                            onDone = { goBack(withHaptic = true) },
                        )
                    }
                    ShellScene.PushJournal -> SecondaryPage("TBC · " + stringResource(R.string.settings_bank_diagnostics), { goBack(withHaptic = true) }) {
                        dev.whekin.whfin.ui.settings.PushJournalRoute(demoMode, onOpenMessages = { openSmsDiagnostics(dev.whekin.whfin.data.sms.BankSmsBank.TBC) }, diagnosticsOnly = true)
                    }
                    ShellScene.TbcLogin -> SecondaryPage(
                        title = stringResource(R.string.tbc_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { dev.whekin.whfin.ui.settings.TbcLoginRoute(appLockHasPin, demoMode, routineSyncRequestKey = tbcRoutineSyncRequestKey, onRoutineSyncConsumed = { tbcRoutineSyncRequestKey = 0 }, onOpenStatements = { open(SecondaryDestination.Statements) }) }
                    ShellScene.Statements -> SecondaryPage(
                        title = stringResource(R.string.statements_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { BankStatementsScreen() }
                    ShellScene.SmsDiagnostics -> SecondaryPage(
                        title = diagnosticsBank?.let { stringResource(R.string.settings_scoped_messages, it.provider) } ?: stringResource(R.string.sms_diagnostics_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        SmsDiagnosticsRoute(
                            bankFilter = diagnosticsBank,
                            appVersion = portableAppVersion,
                            smsImportEnabled = smsImportEnabled,
                            hasReceivePermission = hasSmsPermission,
                            canRequestReceivePermission = canRequestSmsPermission,
                            hasHistoryPermission = hasSmsHistoryPermission,
                            canRequestHistoryPermission = canRequestSmsHistoryPermission,
                            onEnableMonitoring = {
                                val bank = diagnosticsBank
                                if (bank == null) enableSmsMonitoring()
                                else {
                                    bankPreferencesScope.launch { dev.whekin.whfin.data.preferences.UiPreferences(context).setBankSmsEnabled(bank, true) }
                                    if (!hasSmsPermission) { if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings() }
                                }
                            },
                            onRequestReceivePermission = onRequestSmsPermission,
                            onOpenFeed = {
                                haptics.performHapticFeedback(WhfinHaptics.navigation)
                                root = RootDestination.Home
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
                    ShellScene.Savings -> SecondaryPage(
                        title = stringResource(R.string.savings_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { SavingsRoute() }
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
                                // A detour sent here to unblock something else goes straight back to
                                // it. A visit to this screen itself stays: the code just created is
                                // what unlocks biometrics and the delay below, and popping to
                                // Settings would hide both behind another trip through the gate.
                                if (appLockReturnTo != null) goBack(withHaptic = false)
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
                            // A finding names a row; opening it is what makes the finding usable.
                            // It lands in the record with that row's own details already open, so
                            // "Correct imported transaction" is one step away instead of a search.
                            onOpenTransaction = { id ->
                                haptics.performHapticFeedback(WhfinHaptics.navigation)
                                openTransactionId = id
                                root = RootDestination.Transactions
                                secondaryDestination = null
                                secondaryBackStack = emptyList()
                            },
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
                    ShellScene.CategoryIntelligence -> SecondaryPage(
                        title = categoryQueue?.let { categoryQueueTitle(it) }
                            ?: stringResource(R.string.category_intelligence_title),
                        onBack = { goBack(withHaptic = true) },
                    ) {
                        CategoryIntelligenceRoute(
                            queue = categoryQueue,
                            onOpenQueue = {
                                haptics.performHapticFeedback(WhfinHaptics.navigation)
                                categoryQueue = it
                            },
                        )
                    }
                    ShellScene.IncomeSources -> SecondaryPage(
                        title = stringResource(R.string.income_sources_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { IncomeSourcesRoute() }
                    ShellScene.People -> SecondaryPage(
                        title = stringResource(R.string.people_title),
                        onBack = { goBack(withHaptic = true) },
                    ) { PeopleRoute() }
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
    // Last in the body on purpose: the sheet's own Back — which asks before discarding — must be
    // the innermost handler, or the shell's would answer first and close the shell instead.
    if (composerOpen) ShellComposer(
        viewModel = feedViewModel,
        onDismiss = { composerOpen = false },
    )
}

@Composable
internal fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LedgerTopBar(title = title, compact = true, onBack = onBack, onSettings = null, actions = actions)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable private fun LedgerTopBar(title: String?, compact: Boolean, onBack: (() -> Unit)?, onSettings: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().testTag("secondary-topbar").statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            WhfinBackButton(stringResource(R.string.action_back), onBack)
            Spacer(Modifier.width(12.dp))
        }
        if (title != null) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
                maxLines = if (compact) 2 else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onSettings != null) LedgerIconButton(Icons.Default.Settings, stringResource(R.string.settings_title), onSettings)
        actions()
    }
}

/**
 * Writing something down, from wherever the reader happens to be.
 *
 * The composer used to live inside the ledger screen, so the create action first moved the reader to
 * that screen and left them there when the sheet closed. Held by the shell, it opens over whatever
 * destination asked for it and gives that destination back.
 */
@Composable
private fun ShellComposer(viewModel: FeedViewModel, onDismiss: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categoriesByUsage.collectAsState()
    val people by viewModel.people.collectAsState()
    val counterparties by viewModel.counterparties.collectAsState()
    val suggester by viewModel.categorySuggester.collectAsState()
    val rankCategories: CategoryRanker = remember(suggester) {
        { list, amountMinor, currency ->
            suggester?.rankCategories(list, amountMinor?.let { -kotlin.math.abs(it) }, currency) ?: list
        }
    }
    val formState by viewModel.formSaveState.collectAsState()
    AddTransactionSheet(
        formState = formState,
        accounts = accounts,
        categories = categories,
        people = people,
        onDismiss = onDismiss,
        onSave = { manual -> viewModel.addManual(manual) },
        onSaveDebt = { debt -> viewModel.addDebt(debt) },
        onCreateCategory = viewModel::createCategory,
        onCreateCashCurrency = viewModel::createCashCurrency,
        rankCategories = rankCategories,
        counterparties = counterparties,
    )
}

@Composable internal fun LedgerDock(selection: Float, onAdd: () -> Unit, onSelect: (Int) -> Unit) {
    WhfinDock(
        destinations = listOf(
            WhfinDockDestination(
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.FilledHome,
                label = stringResource(R.string.tab_feed),
                testTag = "dock-feed",
            ),
            WhfinDockDestination(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                selectedIcon = Icons.AutoMirrored.Filled.FilledReceiptLong,
                label = stringResource(R.string.tab_transactions),
                testTag = "dock-transactions",
            ),
            WhfinDockDestination(
                icon = Icons.Outlined.AccountBalanceWallet,
                selectedIcon = Icons.Filled.FilledAccountBalanceWallet,
                label = stringResource(R.string.tab_accounts),
                testTag = "dock-accounts",
            ),
            WhfinDockDestination(
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                selectedIcon = Icons.AutoMirrored.Filled.FilledTrendingUp,
                label = stringResource(R.string.tab_analytics),
                testTag = "dock-analytics",
            ),
        ),
        selection = selection,
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
    WhfinTheme { LedgerDock(selection = 0f, onAdd = {}, onSelect = {}) }
}
