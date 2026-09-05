package dev.whekin.whfin.data.backup

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup is a file that already exists, written by whatever version was installed at the time.
 *
 * The first column added after a real release therefore cannot be demanded of it: doing so would
 * reject every copy the owner already has — the nightly one included, at the moment they most need
 * it. Read back as absent, which for a column nothing had yet written is the truth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupColumnsSinceTest {

    @Test
    fun aColumnIsRequiredOnlyOfFilesOldEnoughToHaveIt() {
        val accounts = WhfinBackupSchema.byName.getValue("accounts")

        assertTrue("depositNumber" in accounts.columns)
        assertTrue("depositNumber" !in accounts.requiredColumns(2))
        assertTrue("depositNumber" in accounts.requiredColumns(3))
        // Everything that always existed stays required at every version.
        assertTrue("iban" in accounts.requiredColumns(2))
    }

    @Test
    fun aVersion2FileRestoresWithoutTheColumnItPredates() {
        val snapshot = WhfinBackupCodec.read(ByteArrayInputStream(backup(2, withDepositNumber = false).toByteArray()))

        assertEquals(2, snapshot.summary.databaseVersion)
        val account = snapshot.rowsByTable.getValue("accounts").single()
        assertNull(account["depositNumber"])
        assertEquals(BackupValue.Text("Everyday"), account["name"])
    }

    @Test
    fun aFileClaimingTheCurrentVersionMustCarryTheColumn() {
        val error = runCatching {
            WhfinBackupCodec.read(ByteArrayInputStream(backup(3, withDepositNumber = false).toByteArray()))
        }.exceptionOrNull()

        // Optional forever would let a newer file quietly drop it; the version says when it appeared.
        if (error !is WhfinBackupException) fail("Expected a rejection, got $error")
        assertTrue(error!!.message!!.contains("depositNumber"))
    }

    /**
     * The weekend rule replaced a mandatory outer date, and a v3 file predates it. It restores with
     * the rule absent — the default the migration applies is the same answer arrived at twice.
     */
    @Test
    fun aVersion3FileRestoresWithoutTheWeekendRule() {
        val sources = WhfinBackupSchema.byName.getValue("income_sources")
        assertTrue("weekendRule" !in sources.requiredColumns(3))
        assertTrue("weekendRule" in sources.requiredColumns(4))

        val snapshot = WhfinBackupCodec.read(ByteArrayInputStream(backup(3, withDepositNumber = true).toByteArray()))

        val source = snapshot.rowsByTable.getValue("income_sources").single()
        assertNull(source["weekendRule"])
        // The old outer date is still in the file and is read back as written.
        assertEquals(BackupValue.Integer(10), source["expectedDayTo"])
    }

    /**
     * A whole table can be younger than a file too, and the same reasoning applies: a v3 copy cannot
     * carry confirmations that did not exist, and demanding them would reject it outright.
     */
    @Test
    fun aVersion3FileRestoresWithoutTheTablesItPredates() {
        val snapshot = WhfinBackupCodec.read(ByteArrayInputStream(backup(3, withDepositNumber = true).toByteArray()))

        assertEquals(emptyList<Map<String, BackupValue?>>(), snapshot.rowsByTable.getValue("income_source_payments"))
        assertEquals(5, WhfinBackupSchema.byName.getValue("income_source_payments").sinceVersion)
    }

    @Test
    fun aVersion3FileKeepsTheNumberItCarries() {
        val snapshot = WhfinBackupCodec.read(ByteArrayInputStream(backup(3, withDepositNumber = true).toByteArray()))

        val account = snapshot.rowsByTable.getValue("accounts").single()
        assertEquals(BackupValue.Text("10002888"), account["depositNumber"])
    }

    private fun backup(databaseVersion: Int, withDepositNumber: Boolean): String {
        // A real file carries only the tables that existed when it was written, so the fixture must
        // leave out the newer ones rather than declaring them empty.
        val tables = WhfinBackupSchema.tables
            .filter { it.sinceVersion <= databaseVersion }
            .joinToString(",") { table ->
                val rows = when (table.name) {
                    "accounts" -> accountRow(withDepositNumber)
                    "income_sources" -> incomeSourceRow(databaseVersion)
                    else -> ""
                }
                "\"${table.name}\":[$rows]"
            }
        return """
            {"format":"${WhfinBackupSchema.FORMAT}","schemaVersion":${WhfinBackupSchema.FORMAT_VERSION},
            "exportedAt":"2026-09-01T00:00:00Z","appVersion":"0.3.21","databaseVersion":$databaseVersion,
            "primaryCurrency":"GEL","tables":{$tables}}
        """.trimIndent()
    }

    private fun accountRow(withDepositNumber: Boolean): String = buildString {
        append("""{"id":1,"name":"Everyday","type":"BANK","groupId":null,"currency":"GEL",""")
        append(""""iban":"GE00CD0000000000000001",""")
        if (withDepositNumber) append(""""depositNumber":"10002888",""")
        append(""""walletAddressId":null,"cryptoAssetId":null,"savingsGoalMinor":null,""")
        append(""""savingsMode":null,"fundRole":"AVAILABLE","bankProduct":"CURRENT_ACCOUNT",""")
        append(""""isArchived":0,"sortOrder":0}""")
    }

    private fun incomeSourceRow(databaseVersion: Int): String = buildString {
        append("""{"id":1,"label":"Salary","amountMinor":270000,"currency":"USDT","accountId":1,""")
        append(""""expectedDayFrom":5,"expectedDayTo":10,""")
        if (databaseVersion >= 4) append(""""weekendRule":"LATER",""")
        append(""""startedOn":20500,"endedOn":null,"createdAt":0}""")
    }
}
