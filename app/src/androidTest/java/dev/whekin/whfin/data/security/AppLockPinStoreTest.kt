package dev.whekin.whfin.data.security

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
class AppLockPinStoreTest {
    private var now = 10_000L
    private lateinit var store: AppLockPinStore

    @Before
    fun setUp() {
        store = AppLockPinStore(ApplicationProvider.getApplicationContext()) { now }
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun pin_isVerifiedByAndroidKeystoreBackedDigest() {
        assertFalse(store.hasPin())
        store.setPin("2468".toCharArray())

        assertTrue(store.hasPin())
        assertEquals(PinVerificationResult.Success, store.verify("2468".toCharArray()))
        assertEquals(PinVerificationResult.Invalid(4), store.verify("1111".toCharArray()))
    }

    @Test
    fun fiveWrongAttempts_startThirtySecondLockout() {
        store.setPin("2468".toCharArray())
        repeat(4) { store.verify("1111".toCharArray()) }

        assertEquals(PinVerificationResult.Locked(30_000L), store.verify("1111".toCharArray()))
        assertEquals(PinVerificationResult.Locked(30_000L), store.verify("2468".toCharArray()))
        now += 30_000L
        assertEquals(PinVerificationResult.Success, store.verify("2468".toCharArray()))
    }
}
