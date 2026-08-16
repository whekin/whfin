package dev.whekin.whfin.data.demo

import android.content.Context

/**
 * Local runtime flags are deliberately kept outside DataStore and Android backup.
 * Demo/developer state must not travel to another device with the user's data.
 */
class RuntimeModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val hasWelcomeChoice: Boolean
        get() = preferences.contains(KEY_WELCOME_COMPLETED)

    val welcomeCompleted: Boolean
        get() = preferences.getBoolean(KEY_WELCOME_COMPLETED, false)

    var personalSetupPending: Boolean
        get() = preferences.getBoolean(KEY_PERSONAL_SETUP_PENDING, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_PERSONAL_SETUP_PENDING, value).commit())
        }

    /**
     * Settles the first-run gate for an installation that already carries a ledger.
     *
     * Somebody who has been using WHFIN must never be interrupted by a welcome screen after an
     * update. The signal is the presence of their data: the earlier test — whether the package had
     * ever been updated — was true for every sideloaded build after the first and survived a data
     * wipe, which made the gate permanently unreachable on a device that had once been updated.
     */
    fun adoptExistingInstallation(hasExistingUserData: Boolean) {
        if (hasWelcomeChoice || !hasExistingUserData) return
        completeWelcomeChoice(personalSetupPending = false)
    }

    fun completeWelcomeChoice(personalSetupPending: Boolean) {
        check(
            preferences.edit()
                .putBoolean(KEY_WELCOME_COMPLETED, true)
                .putBoolean(KEY_PERSONAL_SETUP_PENDING, personalSetupPending)
                .commit(),
        )
    }

    /**
     * A standing refusal of the setup offer Home makes to a workspace with no bank behind it.
     *
     * Skipping setup is a legitimate answer and the offer must be refusable for good, not just for
     * this launch. It sits here rather than in the backed-up preferences because it describes this
     * installation's first run, not the ledger: a restore onto a fresh device should ask again.
     */
    var personalSetupInvitationDismissed: Boolean
        get() = preferences.getBoolean(KEY_SETUP_INVITATION_DISMISSED, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_SETUP_INVITATION_DISMISSED, value).commit())
        }

    var demoMode: Boolean
        get() = preferences.getBoolean(KEY_DEMO_MODE, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_DEMO_MODE, value).commit())
        }

    var developerMode: Boolean
        get() = preferences.getBoolean(KEY_DEVELOPER_MODE, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_DEVELOPER_MODE, value).commit())
        }

    var demoFixtureVersion: Int
        get() = preferences.getInt(KEY_DEMO_FIXTURE_VERSION, 0)
        set(value) {
            check(preferences.edit().putInt(KEY_DEMO_FIXTURE_VERSION, value).commit())
        }

    private companion object {
        const val PREFERENCES_NAME = "whfin_runtime"
        const val KEY_DEMO_MODE = "demo_mode"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val KEY_DEMO_FIXTURE_VERSION = "demo_fixture_version"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
        const val KEY_PERSONAL_SETUP_PENDING = "personal_setup_pending"
        const val KEY_SETUP_INVITATION_DISMISSED = "personal_setup_invitation_dismissed"
    }
}
