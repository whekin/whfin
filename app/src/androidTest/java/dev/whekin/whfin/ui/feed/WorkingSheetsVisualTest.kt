package dev.whekin.whfin.ui.feed

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import dev.whekin.whfin.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class WorkingSheetsVisualTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun englishSplitRejectsExcessAndSavesExactShareWithIme() = journey("split", false) { device, context, activity ->
        device.wait(Until.findObject(By.text("Mira")), 10000).click()
        val custom = context.getString(R.string.split_custom)
        compose.onNodeWithText(custom).performScrollTo().performClick(); compose.waitForIdle()
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        field.click(); compose.onNode(hasSetTextAction()).performTextReplacement("99"); compose.waitForIdle()
        val save = context.getString(R.string.action_save)
        capture(device, "split-excess-before-assert")
        compose.onNodeWithText(save).assertIsNotEnabled()
        activity.onActivity { assertNull(it.saved) }
        capture(device, "split-excess-en")
        compose.onNode(hasSetTextAction()).performTextReplacement("21.13"); compose.waitForIdle()
        assertNotNull(device.wait(Until.findObject(By.text(save).enabled(true)), 5000))
        assertAboveIme(device, device.findObject(By.text(save)))
        capture(device, "split-valid-ime-en")
        compose.onNodeWithText(save).performClick(); compose.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.text("Closed")), 5000))
        activity.onActivity { assertEquals(2113L, it.saved?.shareMinor) }
    }

    @Test fun russianCompactNewPersonKeepsInputAndSaveAboveIme() = journey("split", true) { device, context, activity ->
        tap(device, context.getString(R.string.debt_new_person))
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        field.click(); compose.onNode(hasSetTextAction()).performTextReplacement("New friend"); compose.waitForIdle()
        val save = device.wait(Until.findObject(By.text(context.getString(R.string.action_save)).enabled(true)), 5000)
        assertAboveIme(device, save)
        assertAboveIme(device, field)
        assertTrue("Focused field must have room for its full input surface", field.visibleBounds.height() >= (56 * context.resources.displayMetrics.density).toInt())
        activity.onActivity { assertNull(it.saved) }
        capture(device, "split-new-person-ime-ru")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick(); compose.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.text("Closed")), 5000))
        activity.onActivity { assertEquals("New friend", it.saved?.newPersonName); assertEquals(1056L, it.saved?.shareMinor) }
    }

    @Test fun debtSearchHandlesThirtyPeople() = journey("debt", true, 30) { device, context, activity ->
        tap(device, context.getString(R.string.allocation_find_person))
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        field.click(); compose.onNode(hasSetTextAction()).performTextReplacement("Person 30"); compose.waitForIdle()
        compose.onNodeWithTag("allocation-person-30").performScrollTo().performClick(); compose.waitForIdle()
        device.waitForIdle(1000)
        val save = device.wait(Until.findObject(By.text(context.getString(R.string.debt_action_short)).enabled(true)), 5000)
        capture(device, "debt-person-30-ru")
        compose.onNodeWithText(context.getString(R.string.debt_action_short)).performClick(); compose.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.text("Closed")), 5000))
        activity.onActivity { assertEquals(30L, it.saved?.personId) }
    }

    @Test fun reviewRequiresConfirmationAndScrollsLongQueue() = review(false)
    @Test fun russianCompactReviewKeepsBothActionsReadable() = review(true)
    private fun review(large: Boolean) = journey("review", large) { device, context, activity ->
        tap(device, context.getString(R.string.action_delete_draft))
        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.statements_review_delete_title))), 5000))
        activity.onActivity { assertEquals(0, it.deleted) }
        capture(device, "review-confirm-$large")
        tap(device, context.getString(R.string.action_cancel))
        tap(device, context.getString(R.string.action_delete_draft))
        device.wait(Until.findObject(By.text(context.getString(R.string.statements_review_delete_title))), 5000)
        tap(device, context.getString(R.string.action_delete_draft))
        activity.onActivity { assertEquals(1, it.deleted) }
        compose.onNodeWithTag("review-items").performScrollToNode(hasText("Example receipt 30"))
        assertNotNull(device.findObject(By.text("Example receipt 30")))
        capture(device, "review-last-$large")
    }

    private fun journey(mode: String, large: Boolean, people: Int = 3,
        body: (UiDevice, android.content.Context, ActivityScenario<WorkingSheetsQaActivity>) -> Unit) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(i)
        val font = device.executeShellCommand("settings get system font_scale").trim()
        val ime = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        val oldLocale = Locale.getDefault()
        val size = device.executeShellCommand("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(": ")
        device.executeShellCommand("settings put system font_scale ${if (large) 1.5f else 1f}")
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        if (large) device.executeShellCommand("wm size 1200x1920")
        val language = if (large) "ru" else "en"
        val context = i.targetContext.createConfigurationContext(android.content.res.Configuration(i.targetContext.resources.configuration).apply { setLocale(Locale(language)) })
        try {
            ActivityScenario.launch<WorkingSheetsQaActivity>(Intent(i.targetContext, WorkingSheetsQaActivity::class.java)
                .putExtra("mode", mode).putExtra("language", language).putExtra("dark", large)
                .putExtra("fontScale", if (large) 1.5f else 1f).putExtra("people", people)).use { activity ->
                val ready = context.getString(when (mode) {
                    "review" -> R.string.action_done
                    "debt" -> R.string.debt_action_short
                    else -> R.string.action_save
                })
                compose.waitForIdle()
                compose.onNodeWithText(ready).assertExists()
                assertNotNull(device.wait(Until.findObject(By.text(ready)), 10000))
                device.waitForIdle(1000)
                try { body(device, context, activity) }
                catch (error: Throwable) { capture(device, "failure-$mode-$large"); throw error }
            }
        } finally {
            Locale.setDefault(oldLocale)
            device.executeShellCommand("wm size ${size ?: "reset"}")
            device.executeShellCommand("settings put system font_scale ${font.toFloatOrNull() ?: 1f}")
            if (ime == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $ime")
        }
    }

    private fun tap(device: UiDevice, text: String) {
        compose.waitForIdle()
        if (!device.hasObject(By.text(text))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)
        device.wait(Until.findObject(By.text(text)), 5000).click()
        compose.waitForIdle()
        device.waitForIdle(1000)
    }
    private fun assertAboveIme(device: UiDevice, target: UiObject2) {
        val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
        val keyboard = device.wait(Until.findObject(By.pkg(ime)), 5000)
        assertNotNull("A real keyboard must be visible", keyboard)
        assertTrue("Control must remain above the keyboard", target.visibleBounds.bottom <= keyboard.visibleBounds.top)
    }
    private fun capture(device: UiDevice, name: String) {
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "working-sheets-qa").apply { mkdirs() }
        device.waitForIdle(1000)
        device.takeScreenshot(File(out, "$name.png"))
        device.dumpWindowHierarchy(File(out, "$name.xml"))
    }
}
