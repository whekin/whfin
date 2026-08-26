package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.drive.DriveBackupStore
import dev.whekin.whfin.data.preferences.UiPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What Settings can say about itself before the person opens anything.
 *
 * A row that only names the door behind it makes the reader walk through every door to find out
 * whether their money is actually being read, backed up and checked. These are the answers that fit on
 * the row that leads to them; nothing here is a new fact, only one already recorded elsewhere.
 */
internal data class SettingsStatus(
    /** Last completed Credo reconciliation, or null when the bank has never been read. */
    val lastCredoSyncAt: Long? = null,
    /** Last statement import of any origin. */
    val lastStatementImportAt: Long? = null,
    val driveBackupEnabled: Boolean = false,
    val lastDriveBackupAt: Long? = null,
    val integrityIssues: Int = 0,
)

internal class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val whfin = app as WhfinApp
    private val db = whfin.db
    private val preferences = UiPreferences(app)

    /**
     * Read once per screen rather than observed: the Drive state lives in preferences written by a
     * background worker, and a settings row is a snapshot of what was true when it was opened.
     */
    private val drive = DriveBackupStore(app)

    val status: StateFlow<SettingsStatus> = combine(
        preferences.lastCredoSyncAt,
        db.statementImportDao().observeLatestCredoImportAt(),
        db.statementImportDao().observeAll(),
        whfin.integrityIssues,
    ) { lastSync, latestCredoImport, imports, issues ->
        SettingsStatus(
            // The same freshness baseline Home uses, so the two screens cannot disagree about when
            // the bank was last read.
            lastCredoSyncAt = listOfNotNull(lastSync, latestCredoImport).maxOrNull(),
            lastStatementImportAt = imports.maxOfOrNull { it.importedAt },
            driveBackupEnabled = drive.enabled,
            lastDriveBackupAt = drive.lastSuccessAt.takeIf { it > 0L },
            integrityIssues = issues,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStatus())
}
