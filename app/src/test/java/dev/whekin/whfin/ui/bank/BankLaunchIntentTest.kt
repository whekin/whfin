package dev.whekin.whfin.ui.bank

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankLaunchIntentTest {
    @Test fun missingLauncherIsUnavailableAndDoesNotStartAnything() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(context.canLaunch(SupportedBankApp.CREDO))
        assertFalse(context.launchBank(SupportedBankApp.CREDO))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun launcherCarriesNoTransferAmountAccountOrDeepLink() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val app = SupportedBankApp.CREDO
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName)
        val resolution = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = app.packageName
                name = "${app.packageName}.MainActivity"
                exported = true
                applicationInfo = ApplicationInfo().apply { packageName = app.packageName }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(launcher, resolution)
        assertTrue(context.canLaunch(app))
        assertTrue(context.launchBank(app))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(app.packageName, started.component?.packageName)
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertTrue(started.categories.contains(Intent.CATEGORY_LAUNCHER))
        assertNull(started.data)
        assertTrue(started.extras == null || started.extras!!.isEmpty)
    }
}
