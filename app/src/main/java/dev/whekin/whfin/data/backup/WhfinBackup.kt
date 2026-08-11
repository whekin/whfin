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
import kotlinx.coroutines.NonCancellable
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

/**
 * [safetyBackup] makes every restore reversible. It is optional only so tests and the safety
 * snapshot itself can export without recursing; every user-facing path supplies one.
 */
class WhfinBackupManager(
    private val database: WhfinDatabase,
    private val safetyBackup: RestoreSafetyBackup? = null,
) {
    suspend fun export(output: OutputStream, metadata: WhfinBackupMetadata): WhfinBackupSummary =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                WhfinBackupCodec.write(database.openHelper.writableDatabase, output, metadata)
            }
        }

    suspend fun exportEncrypted(
        output: OutputStream,
        metadata: WhfinBackupMetadata,
        passphrase: CharArray,
    ): WhfinBackupSummary = withContext(Dispatchers.IO) {
        WhfinEncryptedBackupEnvelope.encryptingStream(output, passphrase).use { encrypted ->
            // Once the envelope header reaches a user-selected destination, cancellation must not
            // close AES-GCM over zero plaintext: that creates a valid-looking 60-byte empty backup.
            // Finish the already-started file; I/O failures still propagate normally.
            withContext(NonCancellable) {
                database.withTransaction {
                    WhfinBackupCodec.write(database.openHelper.writableDatabase, encrypted, metadata)
                }
            }
        }
    }

    /** Быстрая проверка без восстановления: нужен ли passphrase для этого файла. */
    suspend fun isEncrypted(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        WhfinEncryptedBackupEnvelope.detect(input).second
    }

    suspend fun restore(input: InputStream, passphrase: CharArray? = null): WhfinBackupSummary =
        withContext(Dispatchers.IO) {
            val (stream, encrypted) = WhfinEncryptedBackupEnvelope.detect(input)
            val plain = if (encrypted) {
                if (passphrase == null) {
                    throw WhfinBackupPassphraseException("This backup is encrypted; a passphrase is required.")
                }
                WhfinEncryptedBackupEnvelope.decrypt(stream, passphrase)
            } else {
                stream
            }
            val snapshot = WhfinBackupCodec.read(plain)
            // After the file has proven itself readable and before a single row is deleted: an
            // unreadable file must not cost a snapshot, and a readable one must not cost the ledger.
            safetyBackup?.capture(database)
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
    val legacyColumns: Map<String, BackupLegacyColumn> = emptyMap(),
    /**
     * Allowed values for columns Room reads back as enums.
     *
     * Room throws when it meets an unknown constant, and it throws while observing a query — long
     * after the restore returned. An unchecked value would therefore turn a bad file into an app that
     * crashes on open with no way back, so the value is rejected while parsing, before anything is
     * deleted.
     */
    val enumColumns: Map<String, Set<String>> = emptyMap(),
)

internal data class BackupLegacyColumn(
    val introducedInDatabaseVersion: Int,
    val defaultValue: BackupValue?,
)

private val ACCOUNT_TYPES = setOf("BANK", "CASH", "SAVINGS", "CRYPTO", "PERSON")
private val SAVINGS_MODES = setOf("FLEXIBLE_RESERVE", "GOAL", "TERM_DEPOSIT")
private val FUND_ROLES = setOf("AVAILABLE", "RESERVE")
private val BANK_PRODUCTS = setOf("CURRENT_ACCOUNT", "DEMAND_DEPOSIT", "TERM_DEPOSIT")
private val TRANSFER_GROUP_TYPES =
    setOf("TRANSFER", "CONVERSION", "CARD_TOPUP", "SAVINGS", "CRYPTO_SWAP", "CRYPTO_BRIDGE")

internal object WhfinBackupSchema {
    const val FORMAT = "whfin-backup"
    const val FORMAT_VERSION = 1
    /**
     * Never exported, always cleared on restore.
     *
     * `sms_diagnostics` holds message metadata that must not travel. `crypto_balances` is a cache of
     * a public chain read: exporting it would ship a stale number that one refresh reproduces exactly.
     */
    val excludedTables = setOf("sms_diagnostics", "crypto_balances", "exchange_rates", "exchange_rate_history")

    val tables = listOf(
        BackupTable(
            "financial_groups",
            listOf("id", "name", "type", "provider", "isArchived", "sortOrder"),
            enumColumns = mapOf("type" to setOf("BANK", "WALLET")),
        ),
        BackupTable("wallet_addresses", listOf("id", "groupId", "chainId", "address", "label")),
        BackupTable("crypto_assets", listOf("id", "chainId", "contractAddress", "symbol", "name", "decimals")),
        BackupTable(
            "accounts",
            listOf(
                "id", "name", "type", "groupId", "currency", "iban", "walletAddressId",
                "cryptoAssetId", "savingsGoalMinor", "savingsMode", "fundRole", "bankProduct",
                "isArchived", "sortOrder",
            ),
            legacyColumns = mapOf(
                "fundRole" to BackupLegacyColumn(11, BackupValue.Text("AVAILABLE")),
                "bankProduct" to BackupLegacyColumn(11, null),
            ),
            enumColumns = mapOf(
                "type" to ACCOUNT_TYPES,
                "savingsMode" to SAVINGS_MODES,
                "fundRole" to FUND_ROLES,
                "bankProduct" to BANK_PRODUCTS,
            ),
        ),
        BackupTable(
            "payment_instruments",
            listOf("id", "groupId", "type", "last4", "label", "isArchived"),
            enumColumns = mapOf("type" to setOf("PHYSICAL_CARD", "VIRTUAL_CARD")),
        ),
        BackupTable(
            "instrument_account_links",
            listOf("instrumentId", "accountId"),
            orderBy = listOf("instrumentId", "accountId"),
        ),
        BackupTable(
            "transfer_groups",
            listOf("id", "type", "note", "createdAt"),
            enumColumns = mapOf("type" to TRANSFER_GROUP_TYPES),
        ),
        BackupTable(
            "statement_sources",
            listOf("id", "groupId", "type", "accountId", "instrumentId", "label"),
            enumColumns = mapOf("type" to setOf("ACCOUNT", "CARD")),
        ),
        BackupTable(
            "categories",
            listOf("id", "name", "parentId", "kind", "icon", "color", "isSystem", "sortOrder"),
            enumColumns = mapOf("kind" to setOf("EXPENSE", "INCOME")),
        ),
        BackupTable("merchants", listOf("id", "normalizedKey", "displayName", "categoryId")),
        BackupTable("merchant_aliases", listOf("id", "merchantId", "pattern")),
        BackupTable(
            "people",
            listOf("id", "name", "role", "color", "isArchived"),
            enumColumns = mapOf("role" to setOf("PARTNER", "FAMILY", "FRIEND", "COLLEAGUE", "OTHER")),
        ),
        BackupTable(
            "transactions",
            listOf(
                "id", "accountId", "amountMinor", "currency", "origAmountMinor", "origCurrency",
                "occurredAt", "postedAt", "merchantId", "rawCounterparty", "counterpartyIban",
                "categoryId", "note", "status", "source", "transferGroupId", "isTransfer",
                "balanceAfterMinor", "externalKey", "gelValueMinor", "gelRateOn", "isVoided",
                "correctionOfTransactionId", "correctionRevokedAt", "createdAt",
            ),
            legacyColumns = mapOf(
                "gelValueMinor" to BackupLegacyColumn(introducedInDatabaseVersion = 7, defaultValue = null),
                "gelRateOn" to BackupLegacyColumn(introducedInDatabaseVersion = 7, defaultValue = null),
                "isVoided" to BackupLegacyColumn(introducedInDatabaseVersion = 8, defaultValue = BackupValue.Integer(0)),
                "correctionOfTransactionId" to BackupLegacyColumn(introducedInDatabaseVersion = 8, defaultValue = null),
                // A pre-v10 backup could not record a revoked correction: every correction it holds
                // still describes a voided row.
                "correctionRevokedAt" to BackupLegacyColumn(introducedInDatabaseVersion = 10, defaultValue = null),
            ),
            enumColumns = mapOf(
                "status" to setOf("PENDING", "CONFIRMED", "MANUAL"),
                "source" to setOf("SMS", "STATEMENT", "MANUAL", "ADJUSTMENT", "CRYPTO"),
            ),
        ),
        BackupTable(
            "transaction_allocations",
            listOf("id", "transactionId", "amountMinor", "categoryId", "personId", "purpose", "note"),
            enumColumns = mapOf("purpose" to setOf("PERSONAL", "SHARED", "GIFT", "LOAN", "REPAYMENT")),
        ),
        BackupTable(
            "debt_cases",
            listOf("id", "personId", "direction", "originalAmountMinor", "currency", "openedAt", "status", "closedAt", "note"),
            enumColumns = mapOf(
                "direction" to setOf("THEY_OWE_ME", "I_OWE_THEM"),
                "status" to setOf("OPEN", "CLOSED"),
            ),
        ),
        BackupTable(
            "debt_events",
            listOf(
                "id", "debtCaseId", "kind", "actualAmountMinor", "actualCurrency", "accountId",
                "transactionId", "debtValueMinor", "closesCase", "occurredAt", "note",
                "isVoided", "correctionOfEventId",
            ),
            legacyColumns = mapOf(
                "isVoided" to BackupLegacyColumn(introducedInDatabaseVersion = 9, defaultValue = BackupValue.Integer(0)),
                "correctionOfEventId" to BackupLegacyColumn(introducedInDatabaseVersion = 9, defaultValue = null),
            ),
            enumColumns = mapOf("kind" to setOf("OPENED", "SETTLEMENT", "ADJUSTMENT", "CLOSED")),
        ),
        BackupTable(
            "statement_imports",
            listOf(
                "id", "accountId", "sourceId", "fileName", "origin", "periodFrom", "periodTo",
                "openingBalanceMinor", "closingBalanceMinor", "totalRows", "inserted", "duplicates",
                "reconciled", "reviewCount", "importedAt",
            ),
            legacyColumns = mapOf(
                "origin" to BackupLegacyColumn(
                    introducedInDatabaseVersion = 4,
                    defaultValue = BackupValue.Text("FILE"),
                ),
            ),
            enumColumns = mapOf("origin" to setOf("FILE", "CREDO_SYNC")),
        ),
        BackupTable(
            "reconciliation_issues",
            listOf("id", "accountId", "transactionId", "importId", "state", "createdAt"),
            enumColumns = mapOf("state" to setOf("OPEN", "KEPT")),
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
        WhfinBackupSchema.excludedTables.forEach { table ->
            db.execSQL("DELETE FROM ${table.quoted()}")
        }
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

        if (snapshot.summary.databaseVersion < 11) {
            // Old portable backups carry the combined savingsMode. Reconstruct the same totals the
            // source installation showed rather than defaulting every restored ledger to Available.
            db.execSQL(
                "UPDATE `accounts` SET `fundRole` = 'RESERVE' " +
                    "WHERE `type` = 'SAVINGS' OR `savingsMode` IS NOT NULL",
            )
            db.execSQL(
                "UPDATE `accounts` SET `bankProduct` = 'TERM_DEPOSIT' " +
                    "WHERE `savingsMode` = 'TERM_DEPOSIT'",
            )
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
        val dbVersion = databaseVersion
            ?: throw WhfinBackupException("Missing WHFIN database version.")
        if (dbVersion !in 2..WHFIN_DATABASE_VERSION) {
            throw WhfinBackupException("Unsupported WHFIN database version: $dbVersion.")
        }
        val currency = primaryCurrency
            ?.takeIf { it.matches(Regex("[A-Z0-9]{2,12}")) }
            ?: throw WhfinBackupException("Invalid primary currency.")
        val version = appVersion?.takeIf(String::isNotBlank)
            ?: throw WhfinBackupException("Missing application version.")
        val exported = exportedAt ?: throw WhfinBackupException("Missing backup export time.")
        val tables = rowsByTable ?: throw WhfinBackupException("Backup data is missing.")
        val normalizedTables = tables.mapValues { (tableName, rows) ->
            val table = WhfinBackupSchema.byName.getValue(tableName)
            rows.map { row ->
                val normalized = LinkedHashMap(row)
                table.legacyColumns.forEach { (column, legacy) ->
                    if (column !in normalized) {
                        // Older export writers occasionally emitted the newer database version
                        // before adding a nullable/default column to every row (the demo fixture
                        // is one such portable artifact).  Only the v8 correction columns are
                        // intentionally lenient; existing provenance columns remain strict so a
                        // current backup cannot silently lose statement-import evidence.
                        if (
                            dbVersion >= legacy.introducedInDatabaseVersion &&
                            legacy.introducedInDatabaseVersion < 8
                        ) {
                            throw WhfinBackupException("Missing columns in ${table.name}: $column.")
                        }
                        normalized[column] = legacy.defaultValue
                    }
                }
                table.enumColumns.forEach { (column, allowed) ->
                    val value = normalized[column]
                    if (value is BackupValue.Text && value.value !in allowed) {
                        throw WhfinBackupException(
                            "Unsupported value in ${table.name}.$column: ${value.value}.",
                        )
                    }
                }
                normalized
            }
        }
        return BackupSnapshot(
            summary = WhfinBackupSummary(
                exportedAt = exported,
                appVersion = version,
                databaseVersion = dbVersion,
                primaryCurrency = currency,
                rowCount = normalizedTables.values.sumOf(List<*>::size),
            ),
            rowsByTable = normalizedTables,
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
        val missing = table.columns - row.keys - table.legacyColumns.keys
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
