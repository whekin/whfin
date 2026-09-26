package dev.whekin.whfin.ui.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.demo.RuntimeModeStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersonalSetupNavigationTest {
    @Test fun `restart retains setup progress without completing setup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = RuntimeModeStore(context)
        store.completeWelcomeChoice(personalSetupPending = true)
        store.personalSetupStage = SetupStage.Plans.name
        val restarted = RuntimeModeStore(context)
        assertTrue(restarted.personalSetupPending)
        assertEquals(SetupStage.Plans, setupStageFromSaved(restarted.personalSetupStage))
    }

    @Test fun `unknown or old progress safely starts with banks`() {
        assertEquals(SetupStage.Banks, setupStageFromSaved(null))
        assertEquals(SetupStage.Banks, setupStageFromSaved("Cash"))
    }

    @Test fun `saved nested pages from previous versions still resolve`() {
        assertEquals(SetupPage.Messages, SetupPage.fromSaved("messages"))
        assertEquals(SetupPage.Accounts, SetupPage.fromSaved("accounts"))
        assertEquals(SetupPage.History, SetupPage.fromSaved("history"))
        assertEquals(SetupPage.Settings, SetupPage.fromSaved("settings"))
        assertNull(SetupPage.fromSaved("removed-page"))
    }
}
