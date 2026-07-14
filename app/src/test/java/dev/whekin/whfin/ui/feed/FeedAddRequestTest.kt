package dev.whekin.whfin.ui.feed

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedAddRequestTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addRequestIsConsumedAfterOpeningComposer() {
        var requestKey by mutableIntStateOf(1)
        var opened = 0

        compose.setContent {
            AddRequestEffect(
                requestKey = requestKey,
                onConsumed = { requestKey = 0 },
                onAdd = { opened += 1 },
            )
        }

        compose.waitForIdle()
        assertEquals(0, requestKey)
        assertEquals(1, opened)
    }
}
