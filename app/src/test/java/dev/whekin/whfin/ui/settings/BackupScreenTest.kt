package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun idle_exposesExportAndRestoreActions() {
        var exported = false
        var restored = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupScreen(
                    uiState = BackupUiState.Idle,
                    showRestoreConfirmation = false,
                    onExport = { exported = true },
                    onRestore = { restored = true },
                    onConfirmRestore = {},
                    onDismissRestore = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.backup_export_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.backup_restore_action)).performClick()
        assertTrue(exported)
        assertTrue(restored)
    }

    @Test
    fun restoreConfirmation_requiresExplicitDestructiveAction() {
        var confirmed = false
        var dismissed = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupScreen(
                    uiState = BackupUiState.Idle,
                    showRestoreConfirmation = true,
                    onExport = {},
                    onRestore = {},
                    onConfirmRestore = { confirmed = true },
                    onDismissRestore = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.backup_restore_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.backup_restore_confirm_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun working_disablesBothFileActions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupScreen(
                    uiState = BackupUiState.Restoring,
                    showRestoreConfirmation = false,
                    onExport = {},
                    onRestore = {},
                    onConfirmRestore = {},
                    onDismissRestore = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.backup_export_action)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_restore_action)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_restoring_title)).assertIsDisplayed()
    }

    @Test
    fun success_reportsRestoredRecordCount() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupScreen(
                    uiState = BackupUiState.Restored(rowCount = 42),
                    showRestoreConfirmation = false,
                    onExport = {},
                    onRestore = {},
                    onConfirmRestore = {},
                    onDismissRestore = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.backup_restored_body, 42))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
