package dev.whekin.whfin

import android.Manifest
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.security.AppLockPinStore
import dev.whekin.whfin.data.security.AppLockViewModel
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.data.security.WHFIN_BIOMETRIC_AUTHENTICATORS
import dev.whekin.whfin.data.security.WhfinAuthenticator
import dev.whekin.whfin.data.security.biometricAvailability as checkBiometricAvailability
import dev.whekin.whfin.ui.MainScreen
import dev.whekin.whfin.ui.settings.AppLockGate
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.launch

internal enum class AppStartupContent { Loading, LockGate, Main }

internal fun appStartupContent(
    savedTimeout: AppLockTimeout?,
    hasPin: Boolean,
    sessionLocked: Boolean,
): AppStartupContent = when {
    savedTimeout == null -> AppStartupContent.Loading
    !savedTimeout.enabled || !hasPin -> AppStartupContent.Main
    sessionLocked -> AppStartupContent.LockGate
    else -> AppStartupContent.Main
}

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
    private var resumed = false
    private val uiPreferences by lazy { UiPreferences(applicationContext) }
    private val pinStore by lazy { AppLockPinStore(applicationContext) }
    private lateinit var appLock: AppLockViewModel
    private lateinit var authenticator: WhfinAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WhfinApp
        demoMode = app.isDemoMode
        developerMode = app.runtimeModes.developerMode
        appLock = ViewModelProvider(this)[AppLockViewModel::class.java]
        authenticator = WhfinAuthenticator(this)
        hasAppLockPin = pinStore.hasPin()
        refreshSmsPermission()
        refreshBiometricAvailability()
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            val appThemeMode by uiPreferences.appThemeMode.collectAsState(initial = AppThemeMode.System)
            val dynamicColorsEnabled by uiPreferences.dynamicColorsEnabled.collectAsState(initial = true)
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
            WhfinTheme(themeMode = appThemeMode, dynamicColor = dynamicColorsEnabled) {
                val smsPermissionPromptDismissed: Boolean? by uiPreferences.smsPermissionPromptDismissed
                    .collectAsState(initial = null)
                val smsImportEnabled: Boolean? by uiPreferences.smsImportEnabled.collectAsState(initial = null)
                val configuredSmsCards: Int? by (application as WhfinApp).userDb.paymentInstrumentDao()
                    .observeConfiguredCount().collectAsState(initial = null)
                val savedTimeout: AppLockTimeout? by uiPreferences.appLockTimeout.collectAsState(initial = null)
                val biometricEnabled: Boolean? by uiPreferences.biometricUnlockEnabled.collectAsState(initial = null)
                val effectiveTimeout = savedTimeout
                    ?.takeIf { !it.enabled || hasAppLockPin }
                    ?: AppLockTimeout.Disabled
                val scope = rememberCoroutineScope()
                val mainState = rememberSaveableStateHolder()
                LaunchedEffect(smsImportEnabled, configuredSmsCards) {
                    if (smsImportEnabled == true && configuredSmsCards == 0) {
                        uiPreferences.setSmsImportEnabled(false)
                    }
                }
                LaunchedEffect(savedTimeout, biometricEnabled) {
                    biometricEnabled?.let { this@MainActivity.biometricUnlockEnabled = it }
                    savedTimeout?.let { timeout ->
                        if (timeout.enabled && !hasAppLockPin) {
                            uiPreferences.setAppLockTimeout(AppLockTimeout.Disabled)
                        }
                        appLock.configure(timeout, hasAppLockPin)
                        updateWindowPrivacy()
                        if (resumed && appLock.locked && biometricEnabled == true) requestBiometricUnlock()
                    }
                }

                when (appStartupContent(savedTimeout, hasAppLockPin, appLock.locked)) {
                    AppStartupContent.Loading -> Surface(
                        Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {}
                    AppStartupContent.LockGate -> AppLockGate(
                        biometricAvailable = biometricEnabled == true &&
                            biometricAvailability == BiometricAvailability.Available,
                        problem = appLock.problem,
                        onVerifyPin = ::verifyPin,
                        onBiometric = ::requestBiometricUnlock,
                    )
                    AppStartupContent.Main -> mainState.SaveableStateProvider("main") {
                        MainScreen(
                            appThemeMode = appThemeMode,
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            onAppThemeModeChange = { mode ->
                                scope.launch { uiPreferences.setAppThemeMode(mode) }
                            },
                            onDynamicColorsEnabledChange = { enabled ->
                                scope.launch { uiPreferences.setDynamicColorsEnabled(enabled) }
                            },
                            smsImportEnabled = smsImportEnabled == true && (configuredSmsCards ?: 0) > 0,
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
                            onOpenSystemSettings = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null),
                                    ),
                                )
                            },
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
                            onDemoModeChange = ::changeDemoMode,
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

    private fun changeDemoMode(enabled: Boolean) {
        if (runtimeModeBusy || enabled == demoMode) return
        runtimeModeBusy = true
        runtimeModeProblem = null
        lifecycleScope.launch {
            runCatching { (application as WhfinApp).setDemoMode(enabled) }
                .onSuccess {
                    demoMode = enabled
                    recreate()
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
                .onSuccess { recreate() }
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
        if (::appLock.isInitialized) {
            appLock.foreground()
            updateWindowPrivacy()
            if (appLock.locked && appLock.timeout.enabled) {
                window.decorView.post(::requestBiometricUnlock)
            }
        }
    }

    override fun onPause() {
        if (::appLock.isInitialized && appLock.timeout.enabled) {
            // Set before Android captures the task thumbnail; clear only after a valid foreground session.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        if (::appLock.isInitialized && !isChangingConfigurations) appLock.background()
        super.onStop()
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
            arrayOf(Manifest.permission.RECEIVE_SMS),
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
            !biometricUnlockEnabled || authenticator.isPromptVisible
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
