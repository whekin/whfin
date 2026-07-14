package dev.whekin.whfin.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesTest {
    @Test
    fun smsPromptDismissal_isPersisted() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertFalse(preferences.smsPermissionPromptDismissed.first())
            preferences.dismissSmsPermissionPrompt()
            assertTrue(preferences.smsPermissionPromptDismissed.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun smsImportToggle_defaultsOffAndPersistsBothStates() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertFalse(preferences.smsImportEnabled.first())
            preferences.setSmsImportEnabled(false)
            assertTrue(preferences.smsPermissionPromptDismissed.first())
            preferences.setSmsImportEnabled(true)
            assertTrue(preferences.smsImportEnabled.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun appLock_defaultsOffAndPersistsTimeout() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertEquals(AppLockTimeout.Disabled, preferences.appLockTimeout.first())
            preferences.setAppLockTimeout(AppLockTimeout.OneMinute)
            assertEquals(AppLockTimeout.OneMinute, preferences.appLockTimeout.first())
            preferences.setAppLockTimeout(AppLockTimeout.Disabled)
            assertEquals(AppLockTimeout.Disabled, preferences.appLockTimeout.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun biometricUnlock_defaultsOnAndCanBeDisabled() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertTrue(preferences.biometricUnlockEnabled.first())
            preferences.setBiometricUnlockEnabled(false)
            assertFalse(preferences.biometricUnlockEnabled.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun appearance_defaultsToSystemAndPersistsThemeAndDynamicColors() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertEquals(AppThemeMode.System, preferences.appThemeMode.first())
            assertTrue(preferences.dynamicColorsEnabled.first())
            preferences.setAppThemeMode(AppThemeMode.Dark)
            preferences.setDynamicColorsEnabled(false)
            assertEquals(AppThemeMode.Dark, preferences.appThemeMode.first())
            assertFalse(preferences.dynamicColorsEnabled.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
