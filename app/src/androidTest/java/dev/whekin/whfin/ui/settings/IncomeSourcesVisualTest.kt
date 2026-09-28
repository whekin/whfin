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
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try { ActivityScenario.launch<IncomeSourcesQaActivity>(Intent(context, IncomeSourcesQaActivity::class.java)).use {
            assertNotNull(device.wait(Until.findObject(By.textContains(if (editor) "Salary" else "USDT")), 10_000))
            device.waitForIdle(2000)
            val dir = File(context.getExternalFilesDir(null), "income-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            device.dumpWindowHierarchy(File(dir, "$name.xml"))
            if (editor) {
                device.findObject(By.desc(if (large) "Что это" else "What is it")).click()
                val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                assertTrue(device.wait(Until.hasObject(By.pkg(imePackage)), 5000))
                device.waitForIdle(2000)
                assertNotNull(device.findObject(By.text(if (large) "Сохранить" else "Save")))
                assertTrue(device.takeScreenshot(File(dir, "$name-ime.png")))
                device.pressBack()
                device.swipe(700, 2300, 700, 1300, 30)
                device.waitForIdle(2000)
                // One date and a weekend habit is the whole timing question now; the mandatory
                // "latest by" it replaced must be gone, not merely moved below the fold.
                assertNotNull(device.findObject(By.text(
                    if (large) "Если дата выпадает на выходной" else "If the date falls on a weekend",
                )))
                assertNull(device.findObject(By.text(if (large) "Крайний срок" else "Latest by")))
                assertTrue(device.takeScreenshot(File(dir, "$name-scrolled.png")))
            } else {
                // The credit nobody has answered about yet: the question that has to be reachable
                // before anything is counted as pay.
                device.findObject(By.textContains(if (large) "не подтверждено" else "not confirmed")).click()
                assertNotNull(device.wait(Until.findObject(By.text(
                    if (large) "Это была выплата" else "This was the payment",
                )), 5000))
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-payment.png")))
                device.pressBack()
                device.waitForIdle(2000)
                // The wallet-to-bank offer sits below the questions, so it has to be scrolled to at
                // font 1.5 — the size the owner actually reads this at.
                // At font 1.5 the offer sits below the questions and has to be scrolled to; at
                // 1.0 the whole page fits and there is nothing scrollable to ask.
                device.findObject(By.scrollable(true))?.scrollUntil(
                    androidx.test.uiautomator.Direction.DOWN,
                    Until.findObject(By.textContains("→")),
                )
                device.findObject(By.textContains("→")).click()
                assertNotNull(device.wait(Until.findObject(By.text(if (large) "Связать" else "Link")), 5000))
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-bridge.png")))
            }
        } } finally {
            if (previousIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousIme")
        }
    }
}
