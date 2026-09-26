package dev.whekin.whfin.data.drive

import androidx.test.core.app.ApplicationProvider
import android.os.Looper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveBackupStoreTest {
    @Test
    fun settingsCanSeeANewBackupWhileItStaysOpen() = runBlocking {
        val store = DriveBackupStore(ApplicationProvider.getApplicationContext())
        store.clearAll()
        store.enabled = true
        val readings = mutableListOf<DriveBackupStatus>()
        try {
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                store.observeStatus().take(2).toList(readings)
            }
            withTimeout(5_000) { while (readings.isEmpty()) yield() }
            store.lastSuccessAt = 123L
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            withTimeout(5_000) { collector.join() }

            assertEquals(listOf(
                DriveBackupStatus(true, 0L),
                DriveBackupStatus(true, 123L),
            ), readings)
        } finally {
            store.clearAll()
        }
    }
}
