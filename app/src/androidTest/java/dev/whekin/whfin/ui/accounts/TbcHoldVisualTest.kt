package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TbcHoldVisualTest {
    @Test fun pendingEnglishLight() = show("en",false,1f)
    @Test fun pendingRussianDarkLarge() = show("ru",true,1.5f)
    private fun show(language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val old = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        try {
            ActivityScenario.launch<AccountsQaActivity>(Intent(context,AccountsQaActivity::class.java)
                .putExtra("transaction",true).putExtra("hold",true).putExtra("language",language)
                .putExtra("dark",dark).putExtra("fontScale",font)).use {
                assertNotNull(device.wait(Until.findObject(By.text(if(language=="ru") "Ожидает списания" else "Pending bank charge")),10000))
                assertFalse(device.hasObject(By.text(if(language=="ru") "Подтвердить" else "Confirm")))
                device.waitForIdle(1000)
                val out = File(context.getExternalFilesDir(null),"tbc-hold-qa").apply { mkdirs() }
                device.takeScreenshot(File(out,"$language.png"));device.dumpWindowHierarchy(File(out,"$language.xml"))
            }
        } finally { device.executeShellCommand("settings put system font_scale ${old.toFloatOrNull() ?: 1f}") }
    }
}
