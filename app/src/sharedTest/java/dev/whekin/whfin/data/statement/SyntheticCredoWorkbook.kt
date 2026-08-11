package dev.whekin.whfin.data.statement

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a Credo-shaped xlsx from fully synthetic data.
 *
 * Real statements never enter the repository, so the golden coverage of the adapter boundary has to
 * be generated. Values here are invented: IBAN uses the `GE00` checksum reserved for fixtures.
 */
object SyntheticCredoWorkbook {

    const val IBAN = "GE00WH0000000000000000"

    private val excelEpoch = LocalDate.of(1899, 12, 30)

    data class Row(
        val date: LocalDate,
        val operation: String,
        val debit: String? = null,
        val credit: String? = null,
        val balance: String? = null,
        val description: String = "",
        val beneficiaryName: String? = null,
        val beneficiaryAccount: String? = null,
    )

    data class Columns(
        val date: String = "A",
        val operation: String = "B",
        val debit: String = "C",
        val credit: String = "D",
        val balance: String = "E",
        val description: String = "F",
        val beneficiaryName: String = "G",
        val beneficiaryAccount: String = "H",
    )

    fun build(
        iban: String = IBAN,
        currency: String = "GEL",
        periodFrom: LocalDate = LocalDate.of(2026, 1, 1),
        periodTo: LocalDate = LocalDate.of(2026, 1, 31),
        periodText: String = "${periodFrom.format()} - ${periodTo.format()}",
        openingBalance: String = "100.00",
        openingBalanceLabel: String = "Opening Balance",
        closingBalance: String = "63.86",
        closingBalanceLabel: String = "Closing Balance",
        rows: List<Row> = emptyList(),
        includeDetailsSheet: Boolean = true,
        detailsSheetName: String = "Account Details",
        transactionsSheetName: String = "Transactions",
        columns: Columns = Columns(),
    ): ByteArray {
        val details = listOf(
            "Account Number" to iban,
            "Account Currency" to currency,
            "Statement Period" to periodText,
            openingBalanceLabel to openingBalance,
            closingBalanceLabel to closingBalance,
        ).mapIndexed { index, (label, value) ->
            xmlRow(index + 1, mapOf("A" to label, "B" to value))
        }.joinToString("\n")

        val header = xmlRow(
            1,
            mapOf(
                columns.date to "Date",
                columns.operation to "Operation",
                columns.debit to "Turnover DB",
                columns.credit to "Turnover Cr",
                columns.balance to "Balance",
                columns.description to "Description",
                columns.beneficiaryName to "Beneficiary Name",
                columns.beneficiaryAccount to "Beneficiary Account",
            ),
        )
        val body = rows.mapIndexed { index, row ->
            xmlRow(
                index + 2,
                buildMap {
                    put(columns.date, serial(row.date))
                    put(columns.operation, row.operation)
                    row.debit?.let { put(columns.debit, it) }
                    row.credit?.let { put(columns.credit, it) }
                    row.balance?.let { put(columns.balance, it) }
                    if (row.description.isNotEmpty()) put(columns.description, row.description)
                    row.beneficiaryName?.let { put(columns.beneficiaryName, it) }
                    row.beneficiaryAccount?.let { put(columns.beneficiaryAccount, it) }
                },
                numericColumns = setOf(columns.date),
            )
        }.joinToString("\n")

        val sheets = buildList {
            if (includeDetailsSheet) add(detailsSheetName to details)
            add(transactionsSheetName to "$header\n$body")
        }

        val workbook = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """)
            append("""xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets>""")
            sheets.forEachIndexed { index, (name, _) ->
                append("""<sheet name="$name" sheetId="${index + 1}" r:id="rId${index + 1}" />""")
            }
            append("""</sheets></workbook>""")
        }
        val rels = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.indices.forEach { index ->
                append("""<Relationship Id="rId${index + 1}" Type="ws" Target="worksheets/sheet${index + 1}.xml" />""")
            }
            append("""</Relationships>""")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", rels)
            sheets.forEachIndexed { index, (_, sheetRows) ->
                put(
                    "xl/worksheets/sheet${index + 1}.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                       <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                         <sheetData>$sheetRows</sheetData>
                       </worksheet>""",
                )
            }
        }
        return out.toByteArray()
    }

    private fun serial(date: LocalDate): String = ChronoUnit.DAYS.between(excelEpoch, date).toString()

    private fun LocalDate.format(): String =
        "%02d.%02d.%04d".format(dayOfMonth, monthValue, year)

    private fun xmlRow(
        index: Int,
        cells: Map<String, String>,
        numericColumns: Set<String> = emptySet(),
    ): String = buildString {
        append("""<row r="$index">""")
        cells.forEach { (column, value) ->
            if (column in numericColumns) {
                append("""<c r="$column$index"><v>$value</v></c>""")
            } else {
                append("""<c r="$column$index" t="inlineStr"><is><t>${value.escaped()}</t></is></c>""")
            }
        }
        append("</row>")
    }

    private fun String.escaped(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
