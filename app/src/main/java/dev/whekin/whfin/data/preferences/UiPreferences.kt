package dev.whekin.whfin.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.whfinUiPreferences by preferencesDataStore(name = "whfin_ui")

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

enum class WidgetColorMode(val storedValue: Int) {
    System(0),
    Whfin(1),
    ;

    companion object {
        fun fromStoredValue(value: Int): WidgetColorMode = entries.firstOrNull {
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

    /** Defaults off: card routing must be configured before automatic SMS import is enabled. */
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

    /** Uses Android's wallpaper-derived widget colors by default on supported devices. */
    val widgetColorMode: Flow<WidgetColorMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> WidgetColorMode.fromStoredValue(preferences[WidgetColorModeKey] ?: 0) }

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

    suspend fun setWidgetColorMode(mode: WidgetColorMode) {
        dataStore.edit { preferences -> preferences[WidgetColorModeKey] = mode.storedValue }
    }

    private companion object {
        val SmsPermissionPromptDismissed = booleanPreferencesKey("sms_permission_prompt_dismissed")
        val SmsImportEnabled = booleanPreferencesKey("sms_import_enabled")
        val AppLockTimeoutKey = intPreferencesKey("app_lock_timeout")
        val BiometricUnlockEnabled = booleanPreferencesKey("biometric_unlock_enabled")
        val WidgetColorModeKey = intPreferencesKey("widget_color_mode")
    }
}
