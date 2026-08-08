package dev.whekin.whfin.data.preferences

import android.content.Context
import dev.whekin.whfin.data.crypto.CryptoEndpoints
import dev.whekin.whfin.data.rates.PIVOT_CURRENCY
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.whfinUiPreferences by preferencesDataStore(name = "whfin_ui")

/** Rotation order of the headline total. Deliberately short: three readings, one tap apart. */
val DISPLAY_CURRENCIES = listOf(PIVOT_CURRENCY, "USD", "RUB")

fun nextDisplayCurrency(current: String): String {
    val index = DISPLAY_CURRENCIES.indexOf(current)
    return DISPLAY_CURRENCIES[(index + 1).mod(DISPLAY_CURRENCIES.size)]
}

enum class AppLockTimeout(val storedValue: Int, val timeoutMillis: Long?) {
    Disabled(0, null),
    Immediate(1, 0L),
    ThirtySeconds(2, 30_000L),
    OneMinute(3, 60_000L),
    FiveMinutes(4, 300_000L),
    ;

    val enabled: Boolean get() = timeoutMillis != null

    companion object {
        fun fromStoredValue(value: Int): AppLockTimeout = entries.firstOrNull {
            it.storedValue == value
        } ?: Disabled
    }
}

enum class AppThemeMode(val storedValue: Int) {
    System(0),
    Light(1),
    Dark(2),
    ;

    companion object {
        fun fromStoredValue(value: Int): AppThemeMode = entries.firstOrNull {
            it.storedValue == value
        } ?: System
    }
}

internal class UiPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.whfinUiPreferences)

    val smsPermissionPromptDismissed: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[SmsPermissionPromptDismissed] ?: false }

    /** Defaults off: monitoring is explicit, while incomplete card/account routing may be resolved later. */
    val smsImportEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[SmsImportEnabled] ?: false }

    /** Defaults off so an upgrade never locks a user out without an explicit choice. */
    val appLockTimeout: Flow<AppLockTimeout> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AppLockTimeout.fromStoredValue(preferences[AppLockTimeoutKey] ?: 0) }

    val biometricUnlockEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[BiometricUnlockEnabled] ?: true }

    val appThemeMode: Flow<AppThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AppThemeMode.fromStoredValue(preferences[AppThemeModeKey] ?: 0) }

    /**
     * Defaults off: the WHFIN Quiet Ledger palette is product identity and must be what a first run
     * shows. Wallpaper-derived colors stay an explicit Appearance opt-in; the widget always follows
     * the system palette because it lives on the launcher surface.
     */
    val dynamicColorsEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[DynamicColorsEnabledKey] ?: false }

    /**
     * Reading a public address reveals interest in it, so the endpoint stays visible and editable.
     * Blank means "use the documented public default".
     */
    val cryptoEndpoints: Flow<CryptoEndpoints> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            CryptoEndpoints(
                ethereumRpcUrl = preferences[EthereumRpcUrlKey]?.takeIf { it.isNotBlank() }
                    ?: CryptoEndpoints.DEFAULT_ETHEREUM_RPC,
                tronApiUrl = preferences[TronApiUrlKey]?.takeIf { it.isNotBlank() }
                    ?: CryptoEndpoints.DEFAULT_TRON_API,
            )
        }

    /**
     * Which currency the headline totals are shown in. Storage stays in each account's own currency;
     * this only changes the reading, so switching it can never rewrite a ledger.
     */
    val displayCurrency: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[DisplayCurrencyKey]?.takeIf { it in DISPLAY_CURRENCIES } ?: PIVOT_CURRENCY
        }

    val useSystemFont: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[UseSystemFontKey] ?: false }

    /** Defaults on to preserve the compact calculator-first Quick expense flow. */
    val quickExpenseKeypadEnabled: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[QuickExpenseKeypadEnabledKey] ?: true }

    suspend fun dismissSmsPermissionPrompt() {
        dataStore.edit { preferences -> preferences[SmsPermissionPromptDismissed] = true }
    }

    suspend fun setSmsImportEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SmsImportEnabled] = enabled
            if (!enabled) preferences[SmsPermissionPromptDismissed] = true
        }
    }

    suspend fun setAppLockTimeout(timeout: AppLockTimeout) {
        dataStore.edit { preferences -> preferences[AppLockTimeoutKey] = timeout.storedValue }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[BiometricUnlockEnabled] = enabled }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences -> preferences[AppThemeModeKey] = mode.storedValue }
    }

    suspend fun setDynamicColorsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DynamicColorsEnabledKey] = enabled }
    }

    suspend fun setDisplayCurrency(currency: String) {
        if (currency !in DISPLAY_CURRENCIES) return
        dataStore.edit { preferences -> preferences[DisplayCurrencyKey] = currency }
    }

    suspend fun setCryptoEndpoints(endpoints: CryptoEndpoints) {
        dataStore.edit { preferences ->
            preferences[EthereumRpcUrlKey] = endpoints.ethereumRpcUrl.trim()
            preferences[TronApiUrlKey] = endpoints.tronApiUrl.trim()
        }
    }

    suspend fun setUseSystemFont(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[UseSystemFontKey] = enabled }
    }

    suspend fun setQuickExpenseKeypadEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[QuickExpenseKeypadEnabledKey] = enabled }
    }

    private companion object {
        val SmsPermissionPromptDismissed = booleanPreferencesKey("sms_permission_prompt_dismissed")
        val SmsImportEnabled = booleanPreferencesKey("sms_import_enabled")
        val AppLockTimeoutKey = intPreferencesKey("app_lock_timeout")
        val BiometricUnlockEnabled = booleanPreferencesKey("biometric_unlock_enabled")
        val AppThemeModeKey = intPreferencesKey("app_theme_mode")
        val DynamicColorsEnabledKey = booleanPreferencesKey("dynamic_colors_enabled")
        val UseSystemFontKey = booleanPreferencesKey("use_system_font")
        val QuickExpenseKeypadEnabledKey = booleanPreferencesKey("quick_expense_keypad_enabled")
        val DisplayCurrencyKey = stringPreferencesKey("display_currency")
        val EthereumRpcUrlKey = stringPreferencesKey("crypto_ethereum_rpc_url")
        val TronApiUrlKey = stringPreferencesKey("crypto_tron_api_url")
    }
}
