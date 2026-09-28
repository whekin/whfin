package dev.whekin.whfin.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.whekin.whfin.data.sms.BankSmsBank
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class BankSmsPreferencesTest {
    @Test fun legacyChoiceIsPreservedAndBanksCanThenBeChangedIndependently() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("bank-sms", ".preferences_pb").also(File::delete)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = UiPreferences(store)
        try {
            // A pre-migration installation has only the global key.
            store.edit { it[booleanPreferencesKey("sms_import_enabled")] = true }
            assertTrue(preferences.bankSmsEnabled(BankSmsBank.CREDO).first())
            assertTrue(preferences.bankSmsEnabled(BankSmsBank.TBC).first())
            preferences.setBankSmsEnabled(BankSmsBank.TBC, false)
            assertTrue(preferences.bankSmsEnabled(BankSmsBank.CREDO).first())
            assertFalse(preferences.bankSmsEnabled(BankSmsBank.TBC).first())
            preferences.setBankSmsEnabled(BankSmsBank.CREDO, false)
            assertFalse(preferences.smsImportEnabled.first())
            preferences.setBankSmsEnabled(BankSmsBank.TBC, true)
            val restored = UiPreferences(store)
            assertFalse(restored.bankSmsEnabled(BankSmsBank.CREDO).first())
            assertTrue(restored.bankSmsEnabled(BankSmsBank.TBC).first())
            restored.setSmsImportEnabled(true)
            assertTrue(restored.bankSmsEnabled(BankSmsBank.CREDO).first())
            assertTrue(restored.bankSmsEnabled(BankSmsBank.TBC).first())
        } finally { scope.cancel(); file.delete() }
    }
}
