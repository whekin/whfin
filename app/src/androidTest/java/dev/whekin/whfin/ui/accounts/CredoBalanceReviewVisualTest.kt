package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CredoBalanceReviewVisualTest {
    @Test fun english() = show("en",false,1f)
    @Test fun russianLarge() = show("ru",true,1.5f)
    private fun show(language:String,dark:Boolean,font:Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val old=device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        try { ActivityScenario.launch<AccountsQaActivity>(Intent(context,AccountsQaActivity::class.java)
            .putExtra("balanceReview",true).putExtra("language",language).putExtra("dark",dark).putExtra("fontScale",font)).use { activity ->
            val apply=if(language=="ru") "Применить" else "Apply changes"
            assertNotNull(device.wait(Until.findObject(By.text(apply)),10000))
            activity.onActivity { assertFalse(it.balanceReviewConfirmed) }
            device.waitForIdle(1000)
            val out=File(context.getExternalFilesDir(null),"credo-balance-review").apply { mkdirs() }
            device.takeScreenshot(File(out,"$language.png"));device.dumpWindowHierarchy(File(out,"$language.xml"))
            device.findObject(By.text(apply)).click()
            activity.onActivity { assertTrue(it.balanceReviewConfirmed) }
        }
        ActivityScenario.launch<AccountsQaActivity>(Intent(context,AccountsQaActivity::class.java)
            .putExtra("transferLabels",true).putExtra("language",language).putExtra("dark",dark).putExtra("fontScale",font)).use {
            assertNotNull(device.wait(Until.findObject(By.text("Credo •0001 GEL → •0002 EUR")),10000))
            device.waitForIdle(1000)
            val out=File(context.getExternalFilesDir(null),"credo-balance-review").apply { mkdirs() }
            device.takeScreenshot(File(out,"$language-transfer.png"))
        }
        } finally { device.executeShellCommand("settings put system font_scale ${old.toFloatOrNull() ?: 1f}") }
    }
}
