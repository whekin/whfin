package dev.whekin.whfin.widget

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetOpenAppIntentTest {
    @Test
    fun openActionTargetsMainLauncherTask() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = openAppIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_LAUNCHER))
        assertEquals("whfin://widget/open-app", intent.dataString)
    }
}
