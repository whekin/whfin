package dev.whekin.whfin.data.statement

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Invented data; mirrors the bilingual TBC export without retaining any private statement. */
object SyntheticTbcWorkbook {
    const val IBAN = "GE00TB0000000000000001"
    const val CREDO_IBAN = "GE00CD0000000000000001"
    val defaultRows = listOf(
        listOf("46273", "Example Owner, ა/ნ: $CREDO_IBAN", "", "250", "250", "Income", "GE00TB0000000000000099", "Example Processor", "GDB", "101"),
        listOf("46274", "ნაკრების საკომისიო", "7", "", "243", "Other Expenses", "GE00TB0000000000000098", "Example Bank", "*TPC*", "102"),
        listOf("46274", "POS wallet - EXAMPLE DENTIST, 40.00 GEL, Sep  8 2026  8:52PM, MCC: 8071, 000000******0001", "40", "", "203", "Transfer Out And Cash Withdrawal", "GE00TB0000000000000097", "Example Settlement", "ISSTR", "103"),
        listOf("46274", "POS - EXAMPLE BUS, 2.00 GEL, Sep  8 2026 10:22PM, MCC: 4131, 000000******0001", "2", "", "201", "Transfer Out And Cash Withdrawal", "GE00TB0000000000000097", "Example Settlement", "ISSTR", "104"),
    )
    fun build(rows: List<List<String>> = defaultRows, closing: String = "201", paidOut: String = "49", paidIn: String = "250", iban: String = IBAN, opening: String = "0"): ByteArray {
        val summary = listOf(
            listOf("", "Account No:", iban), listOf("", "Currency:", "GEL"),
            listOf("", "Filter Date From:", "45909"), listOf("", "Filter Date To:", "46274"),
            listOf("", "Starting Balance:", opening), listOf("", "Closing Balance:", closing),
            listOf("", "Paid Out:", paidOut), listOf("", "Paid In:", paidIn),
        )
        val header = listOf("Date", "Description", "Paid Out", "Paid In", "Balance", "Type", "Partner's Account", "Partner's Name", "Op. Code", "Transaction ID")
        val sheets = listOf("Summary" to summary, "$iban-GEL" to (listOf(listOf("თარიღი"), header) + rows))
        fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" + sheets.mapIndexed { i, s -> """<sheet name="${s.first}" r:id="r${i}"/>""" }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<Relationships>" + sheets.indices.joinToString("") { """<Relationship Id="r$it" Target="worksheets/sheet$it.xml"/>""" } + "</Relationships>")
            sheets.forEachIndexed { i, (_, rows) ->
                val body = rows.mapIndexed { r, row ->
                    """<row r="${r+1}">""" + row.mapIndexed { c, v -> """<c r="${('A'.code+c).toChar()}${r+1}" t="inlineStr"><is><t>${xml(v)}</t></is></c>""" }.joinToString("") + "</row>"
                }.joinToString("")
                put("xl/worksheets/sheet$i.xml", "<worksheet><sheetData>$body</sheetData></worksheet>")
            }
        }
        return out.toByteArray()
    }
}
