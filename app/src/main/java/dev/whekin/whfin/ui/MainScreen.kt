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
internal enum class RootDestination { Home, Transactions, Accounts, Analytics }

internal enum class SecondaryDestination { Settings, CredoSync, Statements, SmsDiagnostics, AccountOverview, Savings, AccountTransactions, AnalyticsExpenses, AppLock, Backup, Corrections, DataHealth, Privacy, About, Categories, CategoryIntelligence, IncomeSources, People }

internal enum class ShellScene(val depth: Int) {
    Home(0),
    Transactions(0),
    Accounts(0),
    Analytics(0),
    Settings(1),
    CredoSync(2),
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
    secondaryDestination == null -> ShellTarget(
        when (root) {
            RootDestination.Home -> ShellScene.Home
            RootDestination.Transactions -> ShellScene.Transactions
            RootDestination.Accounts -> ShellScene.Accounts
            RootDestination.Analytics -> ShellScene.Analytics
        },
    )
    secondaryDestination == SecondaryDestination.AccountTransactions -> ShellTarget(
        ShellScene.AccountTransactions,
        accountId = accountTransactionsId,
    )
    else -> ShellTarget(
        when (secondaryDestination) {
            SecondaryDestination.Settings -> ShellScene.Settings
            SecondaryDestination.CredoSync -> ShellScene.CredoSync
            SecondaryDestination.Statements -> ShellScene.Statements
            SecondaryDestination.SmsDiagnostics -> ShellScene.SmsDiagnostics
            SecondaryDestination.AccountOverview -> ShellScene.AccountOverview
            SecondaryDestination.Savings -> ShellScene.Savings
            SecondaryDestination.AnalyticsExpenses -> ShellScene.AnalyticsExpenses
            SecondaryDestination.AppLock -> ShellScene.AppLock
            SecondaryDestination.Backup -> ShellScene.Backup
            SecondaryDestination.Corrections -> ShellScene.Corrections
            SecondaryDestination.DataHealth -> ShellScene.DataHealth
            SecondaryDestination.Privacy -> ShellScene.Privacy
            SecondaryDestination.About -> ShellScene.About
            SecondaryDestination.Categories -> ShellScene.Categories
            SecondaryDestination.CategoryIntelligence -> ShellScene.CategoryIntelligence
            SecondaryDestination.IncomeSources -> ShellScene.IncomeSources
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
    LaunchedEffect(initialTab) { root = rootFor(initialTab) }
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
    val rootStates = rememberSaveableStateHolder()
    var accountAddRequestKey by rememberSaveable {
        mutableIntStateOf(if (initialAccountAddRequest) 1 else 0)
    }
    var addRequestKey by rememberSaveable { mutableIntStateOf(0) }
    // The create action belongs to the shell, not to one destination. It used to send the reader to
    // Home first and leave them there afterwards, so writing something down from Accounts or from
    // analytics cost the place they were reading.
    var composerOpen by rememberSaveable { mutableStateOf(false) }
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
            // A queue is a step inside its screen, so Back leaves it before leaving the screen.
            categoryQueue != null -> categoryQueue = null
            analyticsTransactions != null -> analyticsTransactions = null
            secondaryDestination != null -> {
                val leaving = secondaryDestination
                val back = popSecondaryDestination(secondaryBackStack)
                secondaryDestination = back.destination
                secondaryBackStack = back.remaining
                if (leaving == SecondaryDestination.AccountTransactions) accountTransactionsId = null
                if (leaving == SecondaryDestination.AppLock) appLockReturnTo = null
                if (leaving == SecondaryDestination.CategoryIntelligence) categoryQueue = null
                if (leaving == SecondaryDestination.CredoSync) {
                    credoReturnTo = null
                    credoRoutineSyncRequestKey = 0
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
                val sceneTravel = WhfinMotion.travel()
                val sceneFadeIn = WhfinMotion.standard<Float>()
                val sceneFadeOut = WhfinMotion.quick<Float>()
                val paneTravel = WhfinMotion.travel()
                val paneFadeIn = WhfinMotion.paneEnter<Float>()
                val paneFadeOut = WhfinMotion.paneExit<Float>()
                AnimatedContent(
                    targetState = target,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // A destination's first frame is expensive, and a full-width push loses a
                        // visible chunk of its travel to that frame, which reads as a stutter.
                        // A short directional shift under a fade keeps the direction legible even
                        // when the first frames are dropped.
                        val forward = shellTransitionIsForward(initialState, targetState)
                        // Two roots are a change of subject: they fade through each other, the way
                        // the dock's own peers always did, with the shift following the direction
                        // the dock moved. A push there would claim a level was entered.
                        val betweenRoots = shellTransitionIsBetweenRoots(initialState, targetState)
                        val rightwards = if (betweenRoots) {
                            (rootOrder(targetState.scene) ?: 0) > (rootOrder(initialState.scene) ?: 0)
                        } else {
                            forward
                        }
                        val enter = fadeIn(if (betweenRoots) paneFadeIn else sceneFadeIn) +
                            slideInHorizontally(if (betweenRoots) paneTravel else sceneTravel) { width ->
                                if (rightwards) width / 8 else -width / 8
                            }
                        val exit = fadeOut(if (betweenRoots) paneFadeOut else sceneFadeOut) +
                            slideOutHorizontally(if (betweenRoots) paneTravel else sceneTravel) { width ->
                                if (rightwards) -width / 8 else width / 8
                            }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = if (forward) 1f else -1f
                        }.using(SizeTransform(clip = false))
                    },
                    label = "app-destination",
                ) { targetShell ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (targetShell.scene) {
                            ShellScene.Home, ShellScene.Transactions,
                            ShellScene.Accounts, ShellScene.Analytics,
                            -> Column(Modifier.fillMaxSize()) {
                        // A root keeps its own saved state across a change of destination: the
                        // search typed into the record, the period chosen in analytics, where each
                        // list was scrolled to. Switching is not leaving.
                        rootStates.SaveableStateProvider(targetShell.scene) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
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
                                        onOpenDataHealth = { open(SecondaryDestination.DataHealth) },
                                        onOpenCredoSync = { openCredo(caller = null, syncLatest = true) },
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
                                        viewModel = feedViewModel,
                                    )
                                    ShellScene.Accounts -> AccountsScreen(
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
                        }
                        LedgerDock(
                            selection = dockSelection,
                            onAdd = { composerOpen = true },
                            onSelect = { root = RootDestination.entries[it] },
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
    }
    // Last in the body on purpose: the sheet's own Back — which asks before discarding — must be
    // the innermost handler, or the shell's would answer first and close the shell instead.
    if (composerOpen) ShellComposer(
        viewModel = feedViewModel,
        onDismiss = { composerOpen = false },
    )
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
    AddTransactionSheet(
        accounts = accounts,
        categories = categories,
        people = people,
        onDismiss = onDismiss,
        onSave = { manual -> viewModel.addManual(manual); onDismiss() },
        onSaveDebt = { debt -> viewModel.addDebt(debt); onDismiss() },
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
