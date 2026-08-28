package dev.whekin.whfin.ui.accounts

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the account shapes the demo fixture does not contain: three currencies in one strip, a
 * five-figure balance inside a narrow cell, and the fall back to stacked rows at a large font scale.
 *
 * Stateless UI only: no Room writes, restore, permission changes or bank actions.
 */
class AccountCardsVisualTest {
    @Test fun threeCurrenciesLight() = render("accounts-3cur-light")
    @Test fun threeCurrenciesDark() = render("accounts-3cur-dark", dark = true)
    @Test fun largeRussianFallsBackToRows() = render("accounts-3cur-ru-large", dark = true, large = true)

    private fun render(name: String, dark: Boolean = false, large: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        val previousLocale = Locale.getDefault()
        val intent = Intent(context, AccountsQaActivity::class.java).apply {
            putExtra("dark", dark)
            putExtra("language", if (large) "ru" else "en")
            putExtra("fontScale", if (large) 1.5f else 1f)
        }
        try {
            ActivityScenario.launch<AccountsQaActivity>(intent).use {
                // The imported name carries nothing of its own, so the card is named by its number.
                assertNotNull(device.wait(Until.findObject(By.textContains("0001")), 10_000))
                device.waitForIdle(2_000)
                android.os.SystemClock.sleep(400)
                val directory = File(context.getExternalFilesDir(null), "accounts-qa").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(directory, "$name.png")))
                device.dumpWindowHierarchy(File(directory, "$name.xml"))
                // Every currency of the strip is present, including the empty one.
                listOf("GEL", "EUR", "USD").forEach { currency ->
                    assertNotNull(currency, device.findObject(By.text(currency)))
                }
                // The widest balance a cell has to hold is printed, not dropped for want of room.
                assertNotNull(device.findObject(By.textContains("488")))
                // Colour never carries the warning alone: a cell says it in its description, a row
                // in its supporting line.
                val status = if (large) "Очень мало" else "Very low"
                assertTrue(
                    status,
                    device.hasObject(By.descContains(status)) || device.hasObject(By.textContains(status)),
                )
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
