package dev.whekin.whfin.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.db.WHFIN_DATABASE_VERSION
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WhfinBackupMetadata(
    val exportedAt: Instant,
    val appVersion: String,
    val primaryCurrency: String,
)

data class WhfinBackupSummary(
    val exportedAt: Instant,
    val appVersion: String,
    val databaseVersion: Int,
    val primaryCurrency: String,
    val rowCount: Int,
)

class WhfinBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WhfinBackupManager(private val database: WhfinDatabase) {
    suspend fun export(output: OutputStream, metadata: WhfinBackupMetadata): WhfinBackupSummary =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                WhfinBackupCodec.write(database.openHelper.writableDatabase, output, metadata)
            }
        }

    suspend fun restore(input: InputStream): WhfinBackupSummary = withContext(Dispatchers.IO) {
        val snapshot = WhfinBackupCodec.read(input)
        database.withTransaction {
            WhfinBackupCodec.restore(database.openHelper.writableDatabase, snapshot)
        }
        database.invalidationTracker.refreshAsync()
        snapshot.summary
    }
}

internal data class BackupTable(
    val name: String,
    val columns: List<String>,
    val orderBy: List<String> = listOf("id"),
)

internal object WhfinBackupSchema {
    const val FORMAT = "whfin-backup"
    const val FORMAT_VERSION = 1

    val tables = listOf(
        BackupTable("financial_groups", listOf("id", "name", "type", "provider", "isArchived", "sortOrder")),
        BackupTable("wallet_addresses", listOf("id", "groupId", "chainId", "address", "label")),
        BackupTable("crypto_assets", listOf("id", "chainId", "contractAddress", "symbol", "name", "decimals")),
        BackupTable(
            "accounts",
            listOf(
                "id", "name", "type", "groupId", "currency", "iban", "walletAddressId",
                "cryptoAssetId", "savingsGoalMinor", "savingsMode", "isArchived", "sortOrder",
            ),
        ),
        BackupTable("payment_instruments", listOf("id", "groupId", "type", "last4", "label", "isArchived")),
        BackupTable(
            "instrument_account_links",
            listOf("instrumentId", "accountId"),
            orderBy = listOf("instrumentId", "accountId"),
        ),
        BackupTable("transfer_groups", listOf("id", "type", "note", "createdAt")),
        BackupTable("statement_sources", listOf("id", "groupId", "type", "accountId", "instrumentId", "label")),
        BackupTable("categories", listOf("id", "name", "parentId", "kind", "icon", "color", "isSystem", "sortOrder")),
        BackupTable("merchants", listOf("id", "normalizedKey", "displayName", "categoryId")),
        BackupTable("merchant_aliases", listOf("id", "merchantId", "pattern")),
        BackupTable("people", listOf("id", "name", "role", "color", "isArchived")),
        BackupTable(
            "transactions",
            listOf(
                "id", "accountId", "amountMinor", "currency", "origAmountMinor", "origCurrency",
                "occurredAt", "postedAt", "merchantId", "rawCounterparty", "counterpartyIban",
                "categoryId", "note", "status", "source", "transferGroupId", "isTransfer",
                "balanceAfterMinor", "externalKey", "createdAt",
            ),
        ),
        BackupTable(
            "transaction_allocations",
            listOf("id", "transactionId", "amountMinor", "categoryId", "personId", "purpose", "note"),
        ),
        BackupTable(
            "debt_cases",
            listOf("id", "personId", "direction", "originalAmountMinor", "currency", "openedAt", "status", "closedAt", "note"),
        ),
        BackupTable(
            "debt_events",
            listOf(
                "id", "debtCaseId", "kind", "actualAmountMinor", "actualCurrency", "accountId",
                "transactionId", "debtValueMinor", "closesCase", "occurredAt", "note",
            ),
        ),
        BackupTable(
            "statement_imports",
            listOf(
                "id", "accountId", "sourceId", "fileName", "periodFrom", "periodTo",
                "openingBalanceMinor", "closingBalanceMinor", "totalRows", "inserted", "duplicates",
                "reconciled", "reviewCount", "importedAt",
            ),
        ),
        BackupTable(
            "reconciliation_issues",
            listOf("id", "accountId", "transactionId", "importId", "state", "createdAt"),
        ),
    )

    val byName = tables.associateBy { it.name }
}

internal sealed interface BackupValue {
    data class Integer(val value: Long) : BackupValue
    data class Decimal(val value: Double) : BackupValue
    data class Text(val value: String) : BackupValue
}

internal data class BackupSnapshot(
    val summary: WhfinBackupSummary,
    val rowsByTable: Map<String, List<Map<String, BackupValue?>>>,
)

internal object WhfinBackupCodec {
    private const val MAX_TOTAL_ROWS = 500_000
    private const val MAX_STRING_LENGTH = 1_000_000

    fun write(
        db: SupportSQLiteDatabase,
        output: OutputStream,
        metadata: WhfinBackupMetadata,
    ): WhfinBackupSummary {
        require(metadata.appVersion.isNotBlank())
        require(metadata.primaryCurrency.matches(Regex("[A-Z0-9]{2,12}")))

        var totalRows = 0
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name("format").value(WhfinBackupSchema.FORMAT)
            writer.name("schemaVersion").value(WhfinBackupSchema.FORMAT_VERSION.toLong())
            writer.name("exportedAt").value(metadata.exportedAt.toString())
            writer.name("appVersion").value(metadata.appVersion)
            writer.name("databaseVersion").value(WHFIN_DATABASE_VERSION.toLong())
            writer.name("primaryCurrency").value(metadata.primaryCurrency)
            writer.name("tables").beginObject()
            WhfinBackupSchema.tables.forEach { table ->
                writer.name(table.name).beginArray()
                db.query(table.selectSql()).use { cursor ->
                    while (cursor.moveToNext()) {
                        totalRows += 1
                        writer.beginObject()
                        table.columns.forEachIndexed { index, column ->
                            writer.name(column)
                            writer.writeCursorValue(cursor, index)
                        }
                        writer.endObject()
                    }
                }
                writer.endArray()
            }
            writer.endObject()
            writer.endObject()
        }
        return WhfinBackupSummary(
            exportedAt = metadata.exportedAt,
            appVersion = metadata.appVersion,
            databaseVersion = WHFIN_DATABASE_VERSION,
            primaryCurrency = metadata.primaryCurrency,
            rowCount = totalRows,
        )
    }

    fun read(input: InputStream): BackupSnapshot {
        try {
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.isLenient = false
                val snapshot = reader.readSnapshot()
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw WhfinBackupException("Backup contains trailing data.")
                }
                return snapshot
            }
        } catch (error: WhfinBackupException) {
            throw error
        } catch (error: Exception) {
            throw WhfinBackupException("The selected file is not a valid WHFIN backup.", error)
        }
    }

    fun restore(db: SupportSQLiteDatabase, snapshot: BackupSnapshot) {
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        WhfinBackupSchema.tables.asReversed().forEach { table ->
            db.execSQL("DELETE FROM ${table.name.quoted()}")
        }
        db.execSQL(
            "DELETE FROM `sqlite_sequence` WHERE `name` IN (" +
                WhfinBackupSchema.tables.joinToString(",") { "'${it.name}'" } +
                ")",
        )

        WhfinBackupSchema.tables.forEach { table ->
            snapshot.rowsByTable.getValue(table.name).forEach { row ->
                val values = ContentValues(table.columns.size)
                table.columns.forEach { column -> values.putBackupValue(column, row.getValue(column)) }
                if (db.insert(table.name, SQLiteDatabase.CONFLICT_ABORT, values) == -1L) {
                    throw WhfinBackupException("Could not restore table ${table.name}.")
                }
            }
        }

        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) {
                throw WhfinBackupException("Backup contains broken relationships in ${cursor.getString(0)}.")
            }
        }
    }

    private fun JsonReader.readSnapshot(): BackupSnapshot {
        var format: String? = null
        var schemaVersion: Int? = null
        var exportedAt: Instant? = null
        var appVersion: String? = null
        var databaseVersion: Int? = null
        var primaryCurrency: String? = null
        var rowsByTable: Map<String, List<Map<String, BackupValue?>>>? = null

        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "format" -> format = nextBoundedString("format")
                "schemaVersion" -> schemaVersion = nextInt()
                "exportedAt" -> exportedAt = runCatching { Instant.parse(nextBoundedString("exportedAt")) }
                    .getOrElse { throw WhfinBackupException("Invalid backup export time.", it) }
                "appVersion" -> appVersion = nextBoundedString("appVersion")
                "databaseVersion" -> databaseVersion = nextInt()
                "primaryCurrency" -> primaryCurrency = nextBoundedString("primaryCurrency")
                "tables" -> rowsByTable = readTables()
                else -> skipValue()
            }
        }
        endObject()

        if (format != WhfinBackupSchema.FORMAT) throw WhfinBackupException("This is not a WHFIN backup.")
        if (schemaVersion != WhfinBackupSchema.FORMAT_VERSION) {
            throw WhfinBackupException("Unsupported backup format version: $schemaVersion.")
        }
        if (databaseVersion != WHFIN_DATABASE_VERSION) {
            throw WhfinBackupException("Unsupported WHFIN database version: $databaseVersion.")
        }
        val currency = primaryCurrency
            ?.takeIf { it.matches(Regex("[A-Z0-9]{2,12}")) }
            ?: throw WhfinBackupException("Invalid primary currency.")
        val version = appVersion?.takeIf(String::isNotBlank)
            ?: throw WhfinBackupException("Missing application version.")
        val exported = exportedAt ?: throw WhfinBackupException("Missing backup export time.")
        val tables = rowsByTable ?: throw WhfinBackupException("Backup data is missing.")
        return BackupSnapshot(
            summary = WhfinBackupSummary(
                exportedAt = exported,
                appVersion = version,
                databaseVersion = databaseVersion,
                primaryCurrency = currency,
                rowCount = tables.values.sumOf(List<*>::size),
            ),
            rowsByTable = tables,
        )
    }

    private fun JsonReader.readTables(): Map<String, List<Map<String, BackupValue?>>> {
        val result = LinkedHashMap<String, List<Map<String, BackupValue?>>>()
        var totalRows = 0
        beginObject()
        while (hasNext()) {
            val tableName = nextName()
            val table = WhfinBackupSchema.byName[tableName]
                ?: throw WhfinBackupException("Backup contains unsupported table: $tableName.")
            if (result.containsKey(tableName)) throw WhfinBackupException("Duplicate table: $tableName.")
            val rows = mutableListOf<Map<String, BackupValue?>>()
            beginArray()
            while (hasNext()) {
                totalRows += 1
                if (totalRows > MAX_TOTAL_ROWS) throw WhfinBackupException("Backup contains too many rows.")
                rows += readRow(table)
            }
            endArray()
            result[tableName] = rows
        }
        endObject()
        val missing = WhfinBackupSchema.byName.keys - result.keys
        if (missing.isNotEmpty()) throw WhfinBackupException("Backup is missing tables: ${missing.joinToString()}.")
        return result
    }

    private fun JsonReader.readRow(table: BackupTable): Map<String, BackupValue?> {
        val row = LinkedHashMap<String, BackupValue?>()
        beginObject()
        while (hasNext()) {
            val column = nextName()
            if (column !in table.columns) {
                throw WhfinBackupException("Unsupported column $column in ${table.name}.")
            }
            if (row.containsKey(column)) throw WhfinBackupException("Duplicate column $column in ${table.name}.")
            row[column] = readBackupValue()
        }
        endObject()
        val missing = table.columns - row.keys
        if (missing.isNotEmpty()) {
            throw WhfinBackupException("Missing columns in ${table.name}: ${missing.joinToString()}.")
        }
        return row
    }

    private fun JsonReader.readBackupValue(): BackupValue? = when (peek()) {
        JsonToken.NULL -> nextNull().let { null }
        JsonToken.STRING -> BackupValue.Text(nextBoundedString("value"))
        JsonToken.NUMBER -> nextString().let { raw ->
            raw.toLongOrNull()?.let(BackupValue::Integer)
                ?: raw.toDoubleOrNull()?.let(BackupValue::Decimal)
                ?: throw WhfinBackupException("Invalid number in backup.")
        }
        else -> throw WhfinBackupException("Unsupported value type in backup.")
    }

    private fun JsonReader.nextBoundedString(field: String): String = nextString().also {
        if (it.length > MAX_STRING_LENGTH) throw WhfinBackupException("$field is too long.")
    }

    private fun BackupTable.selectSql(): String = buildString {
        append("SELECT ")
        append(columns.joinToString(",") { it.quoted() })
        append(" FROM ")
        append(name.quoted())
        append(" ORDER BY ")
        append(orderBy.joinToString(",") { it.quoted() })
    }

    private fun JsonWriter.writeCursorValue(cursor: Cursor, index: Int) {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> nullValue()
            Cursor.FIELD_TYPE_INTEGER -> value(cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> value(cursor.getDouble(index))
            Cursor.FIELD_TYPE_STRING -> value(cursor.getString(index))
            Cursor.FIELD_TYPE_BLOB -> throw WhfinBackupException("Binary database columns are not supported by this backup format.")
            else -> throw WhfinBackupException("Unsupported SQLite value type.")
        }
    }

    private fun ContentValues.putBackupValue(column: String, value: BackupValue?) {
        when (value) {
            null -> putNull(column)
            is BackupValue.Integer -> put(column, value.value)
            is BackupValue.Decimal -> put(column, value.value)
            is BackupValue.Text -> put(column, value.value)
        }
    }

    private fun String.quoted(): String = "`" + replace("`", "``") + "`"
}
