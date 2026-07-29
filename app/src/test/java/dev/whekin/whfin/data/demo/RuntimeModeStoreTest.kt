package dev.whekin.whfin.data.demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeModeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context.getSharedPreferences("whfin_runtime", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun flagsPersistLocallyAcrossStoreInstances() {
        val store = RuntimeModeStore(context)
        assertFalse(store.demoMode)
        assertFalse(store.developerMode)
        assertFalse(store.hasWelcomeChoice)
        assertFalse(store.welcomeCompleted)
        assertFalse(store.personalSetupPending)

        store.demoMode = true
        store.developerMode = true
        store.demoFixtureVersion = 4
        store.completeWelcomeChoice(personalSetupPending = true)

        val reopened = RuntimeModeStore(context)
        assertTrue(reopened.demoMode)
        assertTrue(reopened.developerMode)
        assertEquals(4, reopened.demoFixtureVersion)
        assertTrue(reopened.hasWelcomeChoice)
        assertTrue(reopened.welcomeCompleted)
        assertTrue(reopened.personalSetupPending)

        reopened.personalSetupPending = false
        assertFalse(RuntimeModeStore(context).personalSetupPending)
    }
}
