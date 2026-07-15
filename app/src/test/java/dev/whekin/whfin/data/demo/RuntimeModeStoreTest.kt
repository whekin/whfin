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

        store.demoMode = true
        store.developerMode = true
        store.demoFixtureVersion = 4

        val reopened = RuntimeModeStore(context)
        assertTrue(reopened.demoMode)
        assertTrue(reopened.developerMode)
        assertEquals(4, reopened.demoFixtureVersion)
    }
}
