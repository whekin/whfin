package dev.whekin.whfin.ui.settings

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class IncomeSourcesVisualTest {
    @Test fun englishLight() = render("income-en", false, false)
    @Test fun russianLargeDark() = render("income-ru-large", true, false)
    @Test fun englishEditor() = render("income-editor-en", false, true)
    @Test fun russianLargeEditor() = render("income-editor-ru-large", true, true)
    private fun render(name: String, large: Boolean, editor: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        IncomeSourcesQaActivity.language = if (large) "ru" else "en"
        IncomeSourcesQaActivity.fontScale = if (large) 1.5f else 1f
        IncomeSourcesQaActivity.dark = large
        IncomeSourcesQaActivity.editor = editor
        ActivityScenario.launch<IncomeSourcesQaActivity>(Intent(context, IncomeSourcesQaActivity::class.java)).use {
            assertNotNull(device.wait(Until.findObject(By.textContains(if (editor) "Salary" else "USDT")), 10_000))
            device.waitForIdle(2000)
            val dir = File(context.getExternalFilesDir(null), "income-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            device.dumpWindowHierarchy(File(dir, "$name.xml"))
            if (editor) {
                device.findObject(By.desc(if (large) "С какой даты (ГГГГ-ММ-ДД)" else "Since (YYYY-MM-DD)")).click()
                assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.inputmethod.latin")), 5000))
                device.waitForIdle(2000)
                assertNotNull(device.findObject(By.text(if (large) "Сохранить" else "Save")))
                assertTrue(device.takeScreenshot(File(dir, "$name-ime.png")))
                device.pressBack()
                device.swipe(700, 2300, 700, 1300, 30)
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-scrolled.png")))
            } else {
                device.findObject(By.textContains("→")).click()
                assertNotNull(device.wait(Until.findObject(By.text(if (large) "Связать" else "Link")), 5000))
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-bridge.png")))
            }
        }
    }
}
