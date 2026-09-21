package dev.whekin.whfin.ui.feed

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
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
import dev.whekin.whfin.data.preferences.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.Locale

class HomeDecisionQueueJourneyTest {
    @Test fun englishLight() = journey("en", false, 1f)
    @Test fun englishDark() = journey("en", true, 1f)
    @Test fun russianLarge() = journey("ru", true, 1.5f)

    private fun journey(language: String, dark: Boolean, font: Float) = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WhfinApp
        val device = UiDevice.getInstance(instrumentation)
        val preferences = UiPreferences(context)
        val oldTheme = preferences.appThemeMode.first()
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val oldLocale = localeManager.applicationLocales
        val oldFont = device.executeShellCommand("settings get system font_scale").trim()
        val oldDemo = app.runtimeModes.demoMode
        val metadata = WhfinBackupMetadata(Instant.now(), "QA", "GEL")
        val original = ByteArrayOutputStream().also { WhfinBackupManager(app.userDb).export(it, metadata) }.toByteArray()
        val fixture = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        try {
            fixture.financialGroupDao().insert(FinancialGroupEntity(id = 1, name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"))
            fixture.accountDao().insert(AccountEntity(id = 1, name = "Current", type = AccountType.BANK, groupId = 1, currency = "GEL"))
            fixture.categoryDao().insert(CategoryEntity(id = 1, name = "Coffee", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0xFFB47748.toInt()))
            listOf(
                Triple(TxSource.SMS, "SMS coffee", 1L),
                Triple(TxSource.BANK_HOLD, "Card hold", 1L),
                Triple(TxSource.STATEMENT, "Needs category", null),
            ).forEachIndexed { index, (source, name, category) ->
                fixture.transactionDao().insert(TransactionEntity(accountId = 1, amountMinor = -500, currency = "GEL",
                    occurredAt = System.currentTimeMillis() - index * 1000, rawCounterparty = name, categoryId = category,
                    status = if (source == TxSource.BANK_HOLD) TxStatus.PENDING else TxStatus.CONFIRMED, source = source))
            }
            val bytes = ByteArrayOutputStream().also { WhfinBackupManager(fixture).export(it, metadata) }.toByteArray()
            app.runtimeModes.demoMode = false
            app.runtimeModes.completeWelcomeChoice(false)
            preferences.setAppThemeMode(if (dark) AppThemeMode.Dark else AppThemeMode.Light)
            localeManager.applicationLocales = LocaleList.forLanguageTags(language)
            device.executeShellCommand("settings put system font_scale $font")
            WhfinBackupManager(app.userDb).restore(ByteArrayInputStream(bytes))
            val res = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }).resources
            val out = File(context.getExternalFilesDir(null), "polish-home").apply { mkdirs() }
            fun click(label: String) {
                var node = device.wait(Until.findObject(By.text(label)), 5000)
                if (node == null) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 25)
                    node = device.wait(Until.findObject(By.text(label)), 5000)
                }
                assertNotNull(label, node); node.click()
            }
            fun capture(name: String) {
                device.waitForIdle(1000)
                device.takeScreenshot(File(out, "$language-$dark-$font-$name.png"))
                device.dumpWindowHierarchy(File(out, "$language-$dark-$font-$name.xml"))
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue(device.wait(Until.hasObject(By.text(res.getString(R.string.tab_accounts))), 30000))
                capture("home")
                click(res.getString(R.string.home_waiting_bank, 2))
                assertTrue(device.wait(Until.hasObject(By.textContains("SMS coffee")), 8000))
                assertTrue(device.wait(Until.gone(By.textContains("Needs category")), 5000))
                assertTrue(device.hasObject(By.text(res.getString(R.string.feed_waiting_bank))))
                capture("waiting")
                device.pressBack()
                click(res.getString(R.string.home_needs_category, 1))
                assertTrue(device.wait(Until.hasObject(By.textContains("Needs category")), 8000))
                assertTrue(device.wait(Until.gone(By.textContains("SMS coffee")), 5000))
                assertTrue(device.hasObject(By.text(res.getString(R.string.home_needs_attention))))
                capture("decisions")
            }
        } finally {
            fixture.close()
            WhfinBackupManager(app.userDb).restore(ByteArrayInputStream(original))
            app.runtimeModes.demoMode = oldDemo
            preferences.setAppThemeMode(oldTheme)
            localeManager.applicationLocales = oldLocale
            device.executeShellCommand("settings put system font_scale $oldFont")
        }
    }
}
