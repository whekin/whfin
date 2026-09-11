package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CredoDuplicateReviewVisualTest {
    @Test fun englishLight() = show("en",false,1f)
    @Test fun russianDarkLarge() = show("ru",true,1.5f)
    private fun show(language:String,dark:Boolean,font:Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val old=device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        try { ActivityScenario.launch<AccountsQaActivity>(Intent(context,AccountsQaActivity::class.java)
            .putExtra("duplicateReview",true).putExtra("language",language).putExtra("dark",dark).putExtra("fontScale",font)).use { activity ->
            val base=if(language=="ru") "Объединить выбранные" else "Merge selected"
            val apply="$base (0)"
            assertNotNull(device.wait(Until.findObject(By.text(apply)),10000))
            activity.onActivity { assertFalse(it.balanceReviewConfirmed) }
            device.findObject(By.text(apply)).click()
            activity.onActivity { assertFalse(it.balanceReviewConfirmed) }
            device.findObject(By.textContains("EXAMPLE STORE")).click()
            assertNotNull(device.wait(Until.findObject(By.text("$base (1)")),5000))
            device.findObject(By.textContains("EXAMPLE STORE")).click()
            assertNotNull(device.wait(Until.findObject(By.text("$base (0)")),5000))
            device.findObject(By.text(if(language=="ru") "Выбрать все" else "Select all")).click()
            assertNotNull(device.wait(Until.findObject(By.text("$base (2)")),5000))
            device.waitForIdle(1000)
            val out=File(context.getExternalFilesDir(null),"credo-duplicate-review").apply { mkdirs() }
            device.takeScreenshot(File(out,"$language.png"));device.dumpWindowHierarchy(File(out,"$language.xml"))
            device.findObject(By.text("$base (2)")).click()
            activity.onActivity { assertTrue(it.balanceReviewConfirmed); assertEquals(2,it.mergedPairCount) }
        } } finally { device.executeShellCommand("settings put system font_scale ${old.toFloatOrNull() ?: 1f}") }
    }
}
