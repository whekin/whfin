package dev.whekin.whfin.data.credo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredoSecretStoreTest {
    private lateinit var store: CredoSecretStore

    @Before
    fun setUp() {
        store = CredoSecretStore(ApplicationProvider.getApplicationContext())
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun credentials_roundTripThroughAndroidKeystore_andCanBeForgotten() {
        store.save(CredoCredentials("private-user", "private-password"))

        assertTrue(store.hasCredentials())
        assertEquals(CredoCredentials("private-user", "private-password"), store.load())

        store.clear()
        assertFalse(store.hasCredentials())
        assertEquals(null, store.load())
    }
}
