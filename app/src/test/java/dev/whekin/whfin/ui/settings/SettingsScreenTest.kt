package dev.whekin.whfin.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinHaptics
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.WidgetColorMode
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun permissionAction_isShownAndClickable_whenPermissionIsUnavailable() {
        var clicked = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val action = context.getString(R.string.permission_allow)
        compose.setContent {
            WhfinTheme {
                SettingsScreen(
                    widgetColorMode = WidgetColorMode.System,
                    onWidgetColorModeChange = {},
                    smsImportEnabled = true,
                    hasSmsPermission = false,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = {},
                    onRequestSmsPermission = { clicked = true },
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = {},
                    appLockTimeout = AppLockTimeout.Disabled,
                    onOpenAppLock = {},
                    onOpenBackup = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithText(action).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun permissionAction_isHidden_whenPermissionIsGranted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val action = context.getString(R.string.permission_allow)
        compose.setContent {
            WhfinTheme {
                SettingsScreen(
                    widgetColorMode = WidgetColorMode.System,
                    onWidgetColorModeChange = {},
                    smsImportEnabled = true,
                    hasSmsPermission = true,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = {},
                    onRequestSmsPermission = {},
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = {},
                    appLockTimeout = AppLockTimeout.Disabled,
                    onOpenAppLock = {},
                    onOpenBackup = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithText(action).assertDoesNotExist()
    }

    @Test
    fun smsImportSwitch_exposesStateAndToggles() {
        var enabled = true
        val hapticEvents = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                hapticEvents += hapticFeedbackType
            }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_sms_toggle)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                WhfinTheme {
                    SettingsScreen(
                        widgetColorMode = WidgetColorMode.System,
                        onWidgetColorModeChange = {},
                        smsImportEnabled = enabled,
                        hasSmsPermission = true,
                        canRequestSmsPermission = true,
                        onSmsImportEnabledChange = { enabled = it },
                        onRequestSmsPermission = {},
                        onOpenSystemSettings = {},
                        onOpenStatements = {},
                        onOpenSmsDiagnostics = {},
                        appLockTimeout = AppLockTimeout.Disabled,
                        onOpenAppLock = {},
                        onOpenBackup = {},
                        onOpenPrivacy = {},
                        onOpenAbout = {},
                        appVersion = "Version 0.1.0 (1)",
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(description).assertIsOn().performClick()
        assertFalse(enabled)
        assertEquals(listOf(WhfinHaptics.toggle(false)), hapticEvents)
    }

    @Test
    fun smsImportSwitch_isOffWhenDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_sms_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsScreen(
                    widgetColorMode = WidgetColorMode.System,
                    onWidgetColorModeChange = {},
                    smsImportEnabled = false,
                    hasSmsPermission = true,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = {},
                    onRequestSmsPermission = {},
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = {},
                    appLockTimeout = AppLockTimeout.Disabled,
                    onOpenAppLock = {},
                    onOpenBackup = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithContentDescription(description).assertIsOff()
    }

    @Test
    fun widgetSystemColorsSwitch_exposesModeAndToggles() {
        var selectedMode = WidgetColorMode.System
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_widget_system_colors_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsScreen(
                    widgetColorMode = selectedMode,
                    onWidgetColorModeChange = { selectedMode = it },
                    smsImportEnabled = false,
                    hasSmsPermission = true,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = {},
                    onRequestSmsPermission = {},
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = {},
                    appLockTimeout = AppLockTimeout.Disabled,
                    onOpenAppLock = {},
                    onOpenBackup = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithContentDescription(description).assertIsOn().performClick()
        assertEquals(WidgetColorMode.Whfin, selectedMode)
    }

    @Test
    fun productionInfoRows_showVersionAndOpenDestinations() {
        var privacyOpened = false
        var backupOpened = false
        var appLockOpened = false
        var aboutOpened = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsScreen(
                    widgetColorMode = WidgetColorMode.System,
                    onWidgetColorModeChange = {},
                    smsImportEnabled = false,
                    hasSmsPermission = false,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = {},
                    onRequestSmsPermission = {},
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = {},
                    appLockTimeout = AppLockTimeout.OneMinute,
                    onOpenAppLock = { appLockOpened = true },
                    onOpenBackup = { backupOpened = true },
                    onOpenPrivacy = { privacyOpened = true },
                    onOpenAbout = { aboutOpened = true },
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.app_lock_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.backup_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.privacy_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.about_title)).performScrollTo().performClick()
        compose.onNodeWithText("Version 0.1.0 (1)").performScrollTo().assertIsDisplayed()
        assertTrue(appLockOpened)
        assertTrue(backupOpened)
        assertTrue(privacyOpened)
        assertTrue(aboutOpened)
    }

    @Test
    fun privacyPermissions_opensSystemSettings() {
        var opened = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                PrivacyScreen(onOpenSystemSettings = { opened = true })
            }
        }

        compose.onNodeWithText(context.getString(R.string.privacy_permissions_title))
            .performScrollTo()
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun aboutScreen_showsPackageVersionAndAttribution() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                AboutScreen(appVersion = "Version 0.1.0 (1)")
            }
        }

        compose.onNodeWithText("Version 0.1.0 (1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_love_title)).performScrollTo().assertIsDisplayed()
        assertEquals("whekin", context.getString(R.string.about_author_value))
        compose.onNodeWithText(context.getString(R.string.about_author_value)).performScrollTo().assertIsDisplayed()
    }
}
