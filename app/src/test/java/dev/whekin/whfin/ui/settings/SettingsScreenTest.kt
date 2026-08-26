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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinHaptics
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
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
    fun categoryIntelligence_isAVisibleSettingsDestination() {
        var opened = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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
                    onOpenCategoryIntelligence = { opened = true },
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.category_intelligence_title))
            .performScrollTo()
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun permissionAction_isShownAndClickable_whenPermissionIsUnavailable() {
        var clicked = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val action = context.getString(R.string.permission_allow)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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
                SettingsContent(
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
                    SettingsContent(
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

        compose.onNodeWithContentDescription(description).performScrollTo().assertIsOn().performClick()
        assertFalse(enabled)
        assertEquals(listOf(WhfinHaptics.toggle(false)), hapticEvents)
    }

    @Test
    fun smsImportSwitch_isOffWhenDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_sms_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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
    fun quickExpenseKeypadSwitch_exposesStateAndToggles() {
        var enabled = true
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_quick_keypad_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
                    quickExpenseKeypadEnabled = enabled,
                    onQuickExpenseKeypadEnabledChange = { enabled = it },
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

        compose.onNodeWithContentDescription(description)
            .performScrollTo()
            .assertIsOn()
            .performClick()
        assertFalse(enabled)
    }

    @Test
    fun widgetOpenAppSwitch_exposesStateAndToggles() {
        var enabled = true
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_widget_open_app_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
                    widgetOpenAppButtonEnabled = enabled,
                    onWidgetOpenAppButtonEnabledChange = { enabled = it },
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

        compose.onNodeWithContentDescription(description)
            .performScrollTo()
            .assertIsOn()
            .performClick()
        assertFalse(enabled)
    }

    @Test
    fun smsMonitoringSwitch_enablesBeforeCardMapping_andRequestsPermission() {
        var enabled = false
        var diagnosticsOpened = false
        var permissionRequested = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.settings_sms_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
                    smsImportEnabled = false,
                    hasSmsCardMapping = false,
                    hasSmsPermission = false,
                    canRequestSmsPermission = true,
                    onSmsImportEnabledChange = { enabled = it },
                    onRequestSmsPermission = { permissionRequested = true },
                    onOpenSystemSettings = {},
                    onOpenStatements = {},
                    onOpenSmsDiagnostics = { diagnosticsOpened = true },
                    appLockTimeout = AppLockTimeout.Disabled,
                    onOpenAppLock = {},
                    onOpenBackup = {},
                    onOpenPrivacy = {},
                    onOpenAbout = {},
                    appVersion = "Version 0.1.0 (1)",
                )
            }
        }

        compose.onNodeWithContentDescription(description).performScrollTo().assertIsOff().performClick()

        assertTrue(enabled)
        assertFalse(diagnosticsOpened)
        assertTrue(permissionRequested)
    }

    @Test
    fun appearanceControls_selectThemeAndToggleOptions() {
        var selectedTheme = AppThemeMode.System
        var dynamicColors = true
        var useSystemFont = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dynamicColorsDescription = context.getString(R.string.settings_dynamic_colors_toggle)
        val systemFontDescription = context.getString(R.string.settings_system_font_toggle)
        compose.setContent {
            WhfinTheme {
                SettingsContent(
                    appThemeMode = selectedTheme,
                    dynamicColorsEnabled = dynamicColors,
                    useSystemFont = useSystemFont,
                    onAppThemeModeChange = { selectedTheme = it },
                    onDynamicColorsEnabledChange = { dynamicColors = it },
                    onUseSystemFontChange = { useSystemFont = it },
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

        compose.onNodeWithText(context.getString(R.string.settings_theme_dark))
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription(dynamicColorsDescription).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription(systemFontDescription).performScrollTo().assertIsOff().performClick()
        assertEquals(AppThemeMode.Dark, selectedTheme)
        assertFalse(dynamicColors)
        assertTrue(useSystemFont)
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
                SettingsContent(
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

    @Test
    fun personalSettings_opensDemoThroughExplanationSheet() {
        var opened = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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
                    onEnterDemo = { opened = true },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.demo_entry_title))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.demo_entry_isolated_title))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.demo_entry_open))
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun demoSettings_exposesResetOnlyAndRequiresConfirmation() {
        var reset = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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
                    demoMode = true,
                    onResetDemoData = { reset = true },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.demo_entry_title)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_sms_toggle)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.demo_mode_reset))
            .performScrollTo()
            .performClick()
        assertFalse(reset)
        compose.onNodeWithText(context.getString(R.string.demo_mode_reset_confirm)).performClick()
        assertTrue(reset)
    }

    @Test
    fun easterEgg_unlocksPersistentDeveloperToggle() {
        var requested = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val version = "Version 0.1.0 (1)"
        compose.setContent {
            WhfinTheme {
                AboutScreen(
                    appVersion = version,
                    onDeveloperModeChange = { requested = it },
                )
            }
        }

        repeat(5) { compose.onNodeWithText(version).performClick() }
        compose.onNodeWithContentDescription(context.getString(R.string.developer_mode_toggle))
            .performScrollTo()
            .assertIsOff()
            .performClick()
        assertTrue(requested)
    }

    @Test
    fun search_findsARowByWhatPeopleCallIt_andHidesTheRest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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

        compose.onNode(hasSetTextAction()).performTextInput("pin")

        compose.onNodeWithText(context.getString(R.string.app_lock_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.categories_title)).assertDoesNotExist()
    }

    @Test
    fun search_saysWhenNothingMatches() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
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

        compose.onNode(hasSetTextAction()).performTextInput("mortgage")

        compose.onNodeWithText(context.getString(R.string.settings_search_empty)).assertIsDisplayed()
    }

    @Test
    fun bankRows_leadWithTheirOwnState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SettingsContent(
                    status = SettingsStatus(integrityIssues = 2),
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

        compose.onNodeWithText(context.getString(R.string.settings_credo_never)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_sms_state_off)).assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.settings_data_health_issues, 2, 2),
        ).performScrollTo().assertIsDisplayed()
    }
}
