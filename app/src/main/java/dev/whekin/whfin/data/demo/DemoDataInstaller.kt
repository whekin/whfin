package dev.whekin.whfin.data.demo

import android.content.Context
import androidx.room.withTransaction
import dev.whekin.whfin.data.backup.WhfinBackupManager
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class DemoDataInstaller(
    private val context: Context,
    private val database: WhfinDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun install() {
        val summary = context.assets.open(ASSET_NAME).use { input ->
            WhfinBackupManager(database).restore(input)
        }
        val fixtureDate = summary.exportedAt.atZone(ZoneOffset.UTC).toLocalDate()
        val today = LocalDate.now(clock)
        val dayDelta = ChronoUnit.DAYS.between(fixtureDate, today)
        if (dayDelta != 0L) shiftDates(dayDelta)
    }

    private suspend fun shiftDates(dayDelta: Long) {
        val millisDelta = Math.multiplyExact(dayDelta, MILLIS_PER_DAY)
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            listOf(
                "transfer_groups" to listOf("createdAt"),
                "transactions" to listOf("occurredAt", "postedAt", "createdAt"),
                "debt_cases" to listOf("openedAt", "closedAt"),
                "debt_events" to listOf("occurredAt"),
                "statement_imports" to listOf("importedAt"),
                "reconciliation_issues" to listOf("createdAt"),
            ).forEach { (table, columns) ->
                columns.forEach { column ->
                    sql.execSQL(
                        "UPDATE `$table` SET `$column` = `$column` + ? WHERE `$column` IS NOT NULL",
                        arrayOf(millisDelta),
                    )
                }
            }
            listOf("periodFrom", "periodTo").forEach { column ->
                sql.execSQL(
                    "UPDATE `statement_imports` SET `$column` = `$column` + ? WHERE `$column` IS NOT NULL",
                    arrayOf(dayDelta),
                )
            }
        }
        database.invalidationTracker.refreshAsync()
    }

    companion object {
        const val DATABASE_NAME = "whfin-demo.db"
        const val ASSET_NAME = "whfin-demo-v4.json"
        const val FIXTURE_VERSION = 1
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
