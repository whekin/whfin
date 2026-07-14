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
    fun smsImportToggle_defaultsOnAndPersistsBothStates() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertTrue(preferences.smsImportEnabled.first())
            preferences.setSmsImportEnabled(false)
            assertFalse(preferences.smsImportEnabled.first())
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
    fun widgetColors_defaultToSystemAndPersistWhfinOverride() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("whfin-ui", ".preferences_pb").also(File::delete)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(dataStore)

        try {
            assertEquals(WidgetColorMode.System, preferences.widgetColorMode.first())
            preferences.setWidgetColorMode(WidgetColorMode.Whfin)
            assertEquals(WidgetColorMode.Whfin, preferences.widgetColorMode.first())
            preferences.setWidgetColorMode(WidgetColorMode.System)
            assertEquals(WidgetColorMode.System, preferences.widgetColorMode.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
