package dev.whekin.whfin.ui.settings

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertArrayEquals
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

    private fun screen(
        uiState: BackupUiState = BackupUiState.Idle,
        pendingRestore: PendingRestore? = null,
        onExportEncrypted: () -> Unit = {},
        onExportPlain: () -> Unit = {},
        onRestore: () -> Unit = {},
        onConfirmRestore: () -> Unit = {},
        onDismissRestore: () -> Unit = {},
    ) {
        compose.setContent {
            WhfinTheme {
                BackupScreen(
                    uiState = uiState,
                    pendingRestore = pendingRestore,
                    onExportEncrypted = onExportEncrypted,
                    onExportPlain = onExportPlain,
                    onRestore = onRestore,
                    onConfirmRestore = onConfirmRestore,
                    onDismissRestore = onDismissRestore,
                )
            }
        }
    }

    @Test
    fun idle_exposesExportAndRestoreActions() {
        var encrypted = false
        var plain = false
        var restored = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screen(
            onExportEncrypted = { encrypted = true },
            onExportPlain = { plain = true },
            onRestore = { restored = true },
        )

        compose.onNodeWithText(context.getString(R.string.backup_export_encrypted_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.backup_export_plain_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.backup_restore_action)).performClick()
        assertTrue(encrypted)
        assertTrue(plain)
        assertTrue(restored)
    }

    @Test
    fun restoreConfirmation_requiresExplicitDestructiveAction() {
        var confirmed = false
        var dismissed = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screen(
            pendingRestore = PendingRestore(Uri.parse("content://backup"), encrypted = false),
            onConfirmRestore = { confirmed = true },
            onDismissRestore = { dismissed = true },
        )

        compose.onNodeWithText(context.getString(R.string.backup_restore_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.backup_restore_confirm_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun restoreConfirmation_mentionsPassphraseForEncryptedFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screen(pendingRestore = PendingRestore(Uri.parse("content://backup"), encrypted = true))

        compose.onNodeWithText(
            context.getString(R.string.backup_restore_encrypted_hint),
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun working_disablesAllFileActions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screen(uiState = BackupUiState.Restoring)

        compose.onNodeWithText(context.getString(R.string.backup_export_encrypted_action)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_export_plain_action)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_restore_action)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_restoring_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun success_reportsRestoredRecordCount() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screen(uiState = BackupUiState.Restored(rowCount = 42))

        compose.onNodeWithText(context.getString(R.string.backup_restored_body, 42))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun passphraseSheet_gatesOnLengthAndMatchThenSubmits() {
        var submitted: CharArray? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupPassphraseSheet(
                    title = context.getString(R.string.backup_passphrase_title),
                    body = context.getString(R.string.backup_passphrase_body),
                    primaryLabel = context.getString(R.string.action_continue),
                    requireConfirmation = true,
                    errorText = null,
                    onDismiss = {},
                    onSubmit = { submitted = it },
                )
            }
        }
        val continueLabel = context.getString(R.string.action_continue)

        compose.onNodeWithText(continueLabel).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_passphrase)).performTextInput("secret1")
        // Пока подтверждение не совпало, primary остаётся выключенным.
        compose.onNodeWithText(continueLabel).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.backup_passphrase_repeat)).performTextInput("secret1")
        compose.onNodeWithText(continueLabel).performClick()

        assertArrayEquals("secret1".toCharArray(), submitted)
    }

    @Test
    fun passphraseSheet_showsWrongPassphraseError() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                BackupPassphraseSheet(
                    title = context.getString(R.string.backup_restore_passphrase_title),
                    body = context.getString(R.string.backup_restore_passphrase_body),
                    primaryLabel = context.getString(R.string.backup_restore_start),
                    requireConfirmation = false,
                    errorText = context.getString(R.string.backup_wrong_passphrase),
                    onDismiss = {},
                    onSubmit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.backup_wrong_passphrase)).assertIsDisplayed()
    }
}
