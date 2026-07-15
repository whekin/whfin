package dev.whekin.whfin.data.demo

import android.content.Context

/**
 * Local runtime flags are deliberately kept outside DataStore and Android backup.
 * Demo/developer state must not travel to another device with the user's data.
 */
class RuntimeModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
    }
}
