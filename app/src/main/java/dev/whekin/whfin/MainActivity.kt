package dev.whekin.whfin

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.security.AppLockPinStore
import dev.whekin.whfin.data.security.AppLockViewModel
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.data.security.WHFIN_BIOMETRIC_AUTHENTICATORS
import dev.whekin.whfin.data.security.WhfinAuthenticator
import dev.whekin.whfin.data.security.biometricAvailability as checkBiometricAvailability
import dev.whekin.whfin.data.sms.SmsForegroundCatchUp
import dev.whekin.whfin.ui.MainScreen
import dev.whekin.whfin.ui.settings.AppLockGate
import dev.whekin.whfin.ui.setup.PersonalSetupFlow
import dev.whekin.whfin.ui.setup.PersonalSetupState
import dev.whekin.whfin.ui.setup.WelcomeChoiceScreen
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.widget.WhfinWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

internal enum class AppStartupContent { Loading, LockGate, Main }
internal enum class AppEntry { Welcome, PersonalSetup, Main }

internal fun initialAppEntry(
    welcomeCompleted: Boolean,
    personalSetupPending: Boolean,
    demoMode: Boolean,
): AppEntry = when {
    demoMode -> AppEntry.Main
    !welcomeCompleted -> AppEntry.Welcome
    personalSetupPending -> AppEntry.PersonalSetup
    else -> AppEntry.Main
}

internal fun appStartupContent(
    savedTimeout: AppLockTimeout?,
    hasPin: Boolean,
    sessionLocked: Boolean,
    runtimeModeRestart: Boolean = false,
): AppStartupContent = when {
    savedTimeout == null -> AppStartupContent.Loading
    !savedTimeout.enabled || !hasPin -> AppStartupContent.Main
    runtimeModeRestart -> AppStartupContent.Main
    sessionLocked -> AppStartupContent.LockGate
    else -> AppStartupContent.Main
}

internal const val EXTRA_RUNTIME_MODE_RESTART = "dev.whekin.whfin.RUNTIME_MODE_RESTART"

internal fun runtimeModeRestartIntent(componentName: ComponentName): Intent =
    Intent.makeRestartActivityTask(componentName).putExtra(EXTRA_RUNTIME_MODE_RESTART, true)

class MainActivity : FragmentActivity() {
    private var hasSmsPermission by mutableStateOf(false)
    private var hasSmsHistoryPermission by mutableStateOf(false)
    private var canRequestSmsPermission by mutableStateOf(true)
    private var canRequestSmsHistoryPermission by mutableStateOf(true)
    private var biometricAvailability by mutableStateOf(BiometricAvailability.Unsupported)
    private var biometricUnlockEnabled = true
    private var hasAppLockPin by mutableStateOf(false)
    private var demoMode by mutableStateOf(false)
    private var developerMode by mutableStateOf(false)
    private var runtimeModeBusy by mutableStateOf(false)
    private var runtimeModeProblem by mutableStateOf<String?>(null)
    private var runtimeModeRestart by mutableStateOf(false)
    private var appEntry by mutableStateOf(AppEntry.Main)
    private var mainInitialTab by mutableIntStateOf(0)
    private var mainOpenAccountAdd by mutableStateOf(false)
    private var runtimeModeRestarting = false
    private var resumed = false
    // Set once the locked user asks for the code instead of biometrics, so returning to the foreground
    // does not push the system prompt back over the keypad they chose. Cleared on every unlock.
    private var codeUnlockChosen = false
    private val uiPreferences by lazy { UiPreferences(applicationContext) }
    private val pinStore by lazy { AppLockPinStore(applicationContext) }
    private val smsCatchUp by lazy {
        SmsForegroundCatchUp(applicationContext, (application as WhfinApp).userDb)
    }
    private lateinit var appLock: AppLockViewModel
    private lateinit var authenticator: WhfinAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WhfinApp
        val installedPackageInfo = packageManager.getPackageInfo(packageName, 0)
        val portableAppVersion = "${installedPackageInfo.versionName ?: "—"} " +
            "(${PackageInfoCompat.getLongVersionCode(installedPackageInfo)})"
        demoMode = app.isDemoMode
        developerMode = app.runtimeModes.developerMode
        app.runtimeModes.adoptExistingInstallation(app.hadExistingUserData)
        appEntry = initialAppEntry(
            welcomeCompleted = app.runtimeModes.welcomeCompleted,
            personalSetupPending = app.runtimeModes.personalSetupPending,
            demoMode = demoMode,
        )
        runtimeModeRestart = intent.getBooleanExtra(EXTRA_RUNTIME_MODE_RESTART, false)
        appLock = ViewModelProvider(this)[AppLockViewModel::class.java]
        authenticator = WhfinAuthenticator(this)
        hasAppLockPin = pinStore.hasPin()
        refreshSmsPermission()
        refreshBiometricAvailability()
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            val appThemeMode by uiPreferences.appThemeMode.collectAsState(initial = AppThemeMode.System)
            val dynamicColorsEnabled by uiPreferences.dynamicColorsEnabled.collectAsState(initial = false)
            val useSystemFont by uiPreferences.useSystemFont.collectAsState(initial = false)
            val quickExpenseKeypadEnabled by uiPreferences.quickExpenseKeypadEnabled.collectAsState(initial = true)
            val widgetOpenAppButtonEnabled by uiPreferences.widgetOpenAppButtonEnabled.collectAsState(initial = true)
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = when (appThemeMode) {
                AppThemeMode.System -> systemDark
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            LaunchedEffect(effectiveDark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(transparent, transparent) { effectiveDark },
                    navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { effectiveDark },
                )
                window.isNavigationBarContrastEnforced = false
            }
            WhfinTheme(
                themeMode = appThemeMode,
                dynamicColor = dynamicColorsEnabled,
                useSystemFont = useSystemFont,
            ) {
                val smsPermissionPromptDismissed: Boolean? by uiPreferences.smsPermissionPromptDismissed
                    .collectAsState(initial = null)
                val smsImportEnabled: Boolean? by uiPreferences.smsImportEnabled.collectAsState(initial = null)
                val configuredSmsCards: Int? by (application as WhfinApp).userDb.paymentInstrumentDao()
                    .observeConfiguredCount().collectAsState(initial = null)
                val personalAccountsFlow = remember {
                    (application as WhfinApp).userDb.accountDao().observeActive()
                        .map<List<AccountEntity>, List<AccountEntity>?> { it }
                }
                val personalAccounts: List<AccountEntity>? by personalAccountsFlow
                    .collectAsState(initial = null)
                val statementImportsFlow = remember {
                    (application as WhfinApp).userDb.statementImportDao().observeAll()
                        .map<List<StatementImportEntity>, List<StatementImportEntity>?> { it }
                }
                val statementImports: List<StatementImportEntity>? by statementImportsFlow
                    .collectAsState(initial = null)
                val savedTimeout: AppLockTimeout? by uiPreferences.appLockTimeout.collectAsState(initial = null)
                val biometricEnabled: Boolean? by uiPreferences.biometricUnlockEnabled.collectAsState(initial = null)
                val effectiveTimeout = savedTimeout
                    ?.takeIf { !it.enabled || hasAppLockPin }
                    ?: AppLockTimeout.Disabled
                val scope = rememberCoroutineScope()
                val mainState = rememberSaveableStateHolder()
                LaunchedEffect(appLock.locked) {
                    if (!appLock.locked) codeUnlockChosen = false
                }
                LaunchedEffect(savedTimeout, biometricEnabled) {
                    biometricEnabled?.let { this@MainActivity.biometricUnlockEnabled = it }
                    savedTimeout?.let { timeout ->
                        if (timeout.enabled && !hasAppLockPin) {
                            uiPreferences.setAppLockTimeout(AppLockTimeout.Disabled)
                        }
                        appLock.configure(timeout, hasAppLockPin)
                        if (runtimeModeRestart) {
                            appLock.unlock()
                            runtimeModeRestart = false
                        }
                        updateWindowPrivacy()
                        if (resumed && appLock.locked && biometricEnabled == true) requestBiometricUnlock()
                    }
                }

                when (
                    appStartupContent(
                        savedTimeout = savedTimeout,
                        hasPin = hasAppLockPin,
                        sessionLocked = appLock.locked,
                        runtimeModeRestart = runtimeModeRestart,
                    )
                ) {
                    AppStartupContent.Loading -> Surface(
                        Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {}
                    AppStartupContent.LockGate -> AppLockGate(
                        biometricAvailable = biometricEnabled == true &&
                            biometricAvailability == BiometricAvailability.Available,
                        problem = appLock.problem,
                        onVerifyPin = ::verifyPin,
                        onBiometric = {
                            // An explicit tap outranks an earlier code choice.
                            codeUnlockChosen = false
                            requestBiometricUnlock()
                        },
                        onUseCode = { codeUnlockChosen = true },
                    )
                    AppStartupContent.Main -> when (appEntry) {
                        AppEntry.Welcome -> WelcomeChoiceScreen(
                            busy = runtimeModeBusy,
                            problem = runtimeModeProblem,
                            onSetUpPersonal = {
                                app.runtimeModes.completeWelcomeChoice(personalSetupPending = true)
                                appEntry = AppEntry.PersonalSetup
                            },
                            onExploreDemo = ::enterDemoFromWelcome,
                            onExit = ::finish,
                        )
                        AppEntry.PersonalSetup -> mainState.SaveableStateProvider("personal-setup") {
                            PersonalSetupFlow(
                                state = PersonalSetupState(
                                    bankLedgerCount = personalAccounts?.count {
                                        it.type == AccountType.BANK || it.type == AccountType.SAVINGS
                                    },
                                    hasCredoImport = statementImports?.any {
                                        it.origin == StatementImportOrigin.CREDO_SYNC
                                    },
                                    smsMonitoringEnabled = smsImportEnabled == true,
                                    hasSmsPermission = hasSmsPermission,
                                    canRequestSmsPermission = canRequestSmsPermission,
                                    cardRouteCount = configuredSmsCards,
                                ),
                                appVersion = portableAppVersion,
                                appLockTimeout = effectiveTimeout,
                                appLockHasPin = hasAppLockPin,
                                biometricAvailability = biometricAvailability,
                                biometricUnlockEnabled = biometricEnabled != false,
                                hasSmsHistoryPermission = hasSmsHistoryPermission,
                                canRequestSmsHistoryPermission = canRequestSmsHistoryPermission,
                                onEnableSmsMonitoring = {
                                    scope.launch { uiPreferences.setSmsImportEnabled(true) }
                                    if (!hasSmsPermission) {
                                        if (canRequestSmsPermission) requestSmsPermission()
                                        else openAppSettings()
                                    }
                                },
                                onRequestSmsPermission = ::requestSmsPermission,
                                onRequestSmsHistoryPermission = ::requestSmsHistoryPermission,
                                onOpenSystemSettings = ::openAppSettings,
                                onAppLockTimeoutChange = ::requestTimeoutChange,
                                onAppLockPinCreated = ::savePin,
                                onBiometricUnlockEnabledChange = { enabled ->
                                    biometricUnlockEnabled = enabled
                                    scope.launch { uiPreferences.setBiometricUnlockEnabled(enabled) }
                                },
                                onOpenBiometricSettings = ::openBiometricSettings,
                                onContinue = { initialTab, openAccountAdd ->
                                    app.runtimeModes.personalSetupPending = false
                                    mainInitialTab = initialTab
                                    mainOpenAccountAdd = openAccountAdd
                                    appEntry = AppEntry.Main
                                },
                                onExit = ::finish,
                            )
                        }
                        AppEntry.Main -> mainState.SaveableStateProvider("main") {
                            MainScreen(
                                initialTab = mainInitialTab,
                                initialAccountAddRequest = mainOpenAccountAdd,
                                appThemeMode = appThemeMode,
                                dynamicColorsEnabled = dynamicColorsEnabled,
                                useSystemFont = useSystemFont,
                                quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
                                widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
                                onAppThemeModeChange = { mode ->
                                    scope.launch { uiPreferences.setAppThemeMode(mode) }
                                },
                                onDynamicColorsEnabledChange = { enabled ->
                                    scope.launch { uiPreferences.setDynamicColorsEnabled(enabled) }
                                },
                                onUseSystemFontChange = { enabled ->
                                    scope.launch { uiPreferences.setUseSystemFont(enabled) }
                                },
                                onQuickExpenseKeypadEnabledChange = { enabled ->
                                    scope.launch { uiPreferences.setQuickExpenseKeypadEnabled(enabled) }
                                },
                                onWidgetOpenAppButtonEnabledChange = { enabled ->
                                    scope.launch {
                                        uiPreferences.setWidgetOpenAppButtonEnabled(enabled)
                                        WhfinWidget().updateAll(applicationContext)
                                    }
                                },
                                smsImportEnabled = smsImportEnabled == true,
                                hasSmsCardMapping = (configuredSmsCards ?: 0) > 0,
                                hasSmsPermission = hasSmsPermission,
                                canRequestSmsPermission = canRequestSmsPermission,
                                hasSmsHistoryPermission = hasSmsHistoryPermission,
                                canRequestSmsHistoryPermission = canRequestSmsHistoryPermission,
                                // Do not flash an already dismissed prompt while DataStore is loading.
                                smsPermissionPromptDismissed = smsPermissionPromptDismissed != false,
                                onRequestSmsPermission = ::requestSmsPermission,
                                onRequestSmsHistoryPermission = ::requestSmsHistoryPermission,
                                onDismissSmsPermissionPrompt = {
                                    scope.launch { uiPreferences.dismissSmsPermissionPrompt() }
                                },
                                onSmsImportEnabledChange = { enabled ->
                                    scope.launch { uiPreferences.setSmsImportEnabled(enabled) }
                                },
                                onOpenSystemSettings = ::openAppSettings,
                                appLockTimeout = effectiveTimeout,
                                appLockHasPin = hasAppLockPin,
                                biometricAvailability = biometricAvailability,
                                biometricUnlockEnabled = biometricEnabled != false,
                                onAppLockTimeoutChange = ::requestTimeoutChange,
                                onAppLockPinCreated = ::savePin,
                                onBiometricUnlockEnabledChange = { enabled ->
                                    biometricUnlockEnabled = enabled
                                    scope.launch { uiPreferences.setBiometricUnlockEnabled(enabled) }
                                },
                                onOpenBiometricSettings = ::openBiometricSettings,
                                demoMode = demoMode,
                                developerMode = developerMode,
                                runtimeModeBusy = runtimeModeBusy,
                                runtimeModeProblem = runtimeModeProblem,
                                onEnterDemo = { changeDemoMode(true) },
                                onExitDemo = { changeDemoMode(false) },
                                onResetDemoData = ::resetDemoData,
                                onDeveloperModeChange = { enabled ->
                                    developerMode = enabled
                                    app.setDeveloperMode(enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun enterDemoFromWelcome() {
        if (runtimeModeBusy) return
        runtimeModeBusy = true
        runtimeModeProblem = null
        lifecycleScope.launch {
            runCatching { (application as WhfinApp).setDemoMode(true) }
                .onSuccess {
                    (application as WhfinApp).runtimeModes.completeWelcomeChoice(
                        personalSetupPending = true,
                    )
                    demoMode = true
                    restartForRuntimeMode()
                }
                .onFailure { error ->
                    runtimeModeProblem = getString(
                        R.string.demo_mode_error,
                        error.message ?: error::class.java.simpleName,
                    )
                }
            runtimeModeBusy = false
        }
    }

    private fun changeDemoMode(enabled: Boolean) {
        if (runtimeModeBusy || enabled == demoMode) return
        runtimeModeBusy = true
        runtimeModeProblem = null
        lifecycleScope.launch {
            runCatching { (application as WhfinApp).setDemoMode(enabled) }
                .onSuccess {
                    demoMode = enabled
                    restartForRuntimeMode()
                }
                .onFailure { error ->
                    runtimeModeProblem = getString(R.string.demo_mode_error, error.message ?: error::class.java.simpleName)
                }
            runtimeModeBusy = false
        }
    }

    private fun resetDemoData() {
        if (runtimeModeBusy || !demoMode) return
        runtimeModeBusy = true
        runtimeModeProblem = null
        lifecycleScope.launch {
            runCatching { (application as WhfinApp).resetDemoData() }
                .onSuccess { restartForRuntimeMode() }
                .onFailure { error ->
                    runtimeModeProblem = getString(R.string.demo_mode_error, error.message ?: error::class.java.simpleName)
                }
            runtimeModeBusy = false
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refreshSmsPermission()
        refreshBiometricAvailability()
        lifecycleScope.launch { smsCatchUp.runIfNeeded() }
        if (::appLock.isInitialized) {
            appLock.foreground()
            updateWindowPrivacy()
            if (appLock.locked && appLock.timeout.enabled) {
                window.decorView.post(::requestBiometricUnlock)
            }
        }
    }

    override fun onPause() {
        if (::appLock.isInitialized && appLock.timeout.enabled && !runtimeModeRestarting) {
            // Set before Android captures the task thumbnail; clear only after a valid foreground session.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        if (::appLock.isInitialized && !isChangingConfigurations && !runtimeModeRestarting) {
            appLock.background()
        }
        super.onStop()
    }

    private fun restartForRuntimeMode() {
        runtimeModeRestarting = true
        startActivity(runtimeModeRestartIntent(componentName))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECEIVE_SMS -> {
                refreshSmsPermission()
                canRequestSmsPermission = hasSmsPermission ||
                    shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)
            }
            REQUEST_READ_SMS -> {
                refreshSmsPermission()
                canRequestSmsHistoryPermission = hasSmsHistoryPermission ||
                    shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)
            }
        }
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            REQUEST_RECEIVE_SMS,
        )
    }

    private fun requestSmsHistoryPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS),
            REQUEST_READ_SMS,
        )
    }

    private fun requestBiometricUnlock() {
        if (
            !resumed || !appLock.locked || !appLock.timeout.enabled ||
            !biometricUnlockEnabled || authenticator.isPromptVisible || codeUnlockChosen
        ) return
        if (biometricAvailability != BiometricAvailability.Available) return
        authenticator.authenticate(
            title = getString(R.string.app_lock_prompt_title),
            subtitle = getString(R.string.app_lock_prompt_subtitle),
            useCodeLabel = getString(R.string.app_lock_use_code),
            onSuccess = {
                appLock.unlock()
                updateWindowPrivacy()
            },
            onProblem = appLock::report,
        )
    }

    private fun requestTimeoutChange(timeout: AppLockTimeout) {
        if (timeout == appLock.timeout) return
        if (timeout.enabled && !hasAppLockPin) return
        appLock.configure(timeout, hasAppLockPin)
        appLock.unlock()
        updateWindowPrivacy()
        lifecycleScope.launch { uiPreferences.setAppLockTimeout(timeout) }
    }

    private fun savePin(pin: String, timeout: AppLockTimeout) {
        pinStore.setPin(pin.toCharArray())
        hasAppLockPin = true
        appLock.configure(timeout, hasPin = true)
        appLock.unlock()
        updateWindowPrivacy()
        lifecycleScope.launch { uiPreferences.setAppLockTimeout(timeout) }
    }

    private fun verifyPin(pin: String): PinVerificationResult = pinStore.verify(pin.toCharArray()).also {
        if (it == PinVerificationResult.Success) {
            appLock.unlock()
            updateWindowPrivacy()
        }
    }

    private fun openBiometricSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                WHFIN_BIOMETRIC_AUTHENTICATORS,
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        startActivity(intent)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun updateWindowPrivacy() {
        if (appLock.timeout.enabled && appLock.locked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (resumed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun refreshBiometricAvailability() {
        biometricAvailability = checkBiometricAvailability(this)
    }

    private fun refreshSmsPermission() {
        hasSmsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasSmsHistoryPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REQUEST_RECEIVE_SMS = 1101
        const val REQUEST_READ_SMS = 1102
    }
}
