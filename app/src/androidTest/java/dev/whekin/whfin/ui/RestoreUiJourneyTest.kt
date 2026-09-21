package dev.whekin.whfin.ui

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.backup.*
import dev.whekin.whfin.data.db.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RestoreUiJourneyTest {
    @Test fun restoringReplacesTheActivityAndItsOldAccountState() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WhfinApp
        val device = UiDevice.getInstance(instrumentation)
        val metadata = WhfinBackupMetadata(Instant.now(), "test", "GEL")
        val original = ByteArrayOutputStream().also { WhfinBackupManager(app.userDb).export(it, metadata) }.toByteArray()
        val wasDemo = app.runtimeModes.demoMode
        suspend fun fixture(name: String): ByteArray {
            val db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
            return try {
                db.accountDao().insert(AccountEntity(id = 1, name = name, type = AccountType.CASH, currency = "GEL"))
                ByteArrayOutputStream().also { WhfinBackupManager(db).export(it, metadata) }.toByteArray()
            } finally { db.close() }
        }
        val manager = WhfinBackupManager(app.userDb, RestoreSafetyBackup(context, "test"))
        try {
            app.runtimeModes.demoMode = false
            app.runtimeModes.completeWelcomeChoice(false)
            manager.restore(ByteArrayInputStream(fixture("Before restore")))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var previousActivity: MainActivity? = null
                scenario.onActivity { previousActivity = it }
                val accounts = context.getString(R.string.tab_accounts)
                assertNotNull(device.wait(Until.findObject(By.text(accounts)), 30000))
                device.findObject(By.text(accounts)).click()
                assertTrue(device.wait(Until.hasObject(By.textContains("Before restore")), 10000))
                manager.restore(ByteArrayInputStream(fixture("After restore")))
                // Restart must discard the old Accounts screen and return to Home.
                assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.settings_title))), 15000))
                assertNotNull(device.wait(Until.findObject(By.text(accounts)), 10000))
                device.findObject(By.text(accounts)).click()
                assertTrue(device.wait(Until.hasObject(By.textContains("After restore")), 10000))
                assertFalse(device.hasObject(By.textContains("Before restore")))
                assertTrue("Restore must discard old ViewModels, not just refresh their lists", previousActivity!!.isDestroyed)
            }
        } finally {
            manager.restore(ByteArrayInputStream(original))
            app.runtimeModes.demoMode = wasDemo
        }
    }
}
