package dev.whekin.whfin.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Синтетический xlsx, повторяющий структуру выписки MYCREDO (без персональных данных). */
class XlsxSheetReaderTest {

    private fun syntheticXlsx(): ByteArray {
        val workbook = """<?xml version="1.0" encoding="utf-8"?>
            <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                      xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheets>
                <sheet name="Account Details" sheetId="1" r:id="rId1" />
                <sheet name="Transactions" sheetId="2" r:id="rId3" />
              </sheets>
            </workbook>"""
        val rels = """<?xml version="1.0" encoding="utf-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="ws" Target="worksheets/sheet1.xml" />
              <Relationship Id="rId3" Type="ws" Target="worksheets/sheet2.xml" />
            </Relationships>"""
        val sharedStrings = """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="8" uniqueCount="8">
              <si><t>Account Number</t></si>
              <si><t>GE00CD0000000000000000</t></si>
              <si><t>Date</t></si>
              <si><t>Operation</t></si>
              <si><t>საბარათე ოპერაცია</t></si>
              <si><t>7.14</t></si>
              <si><t>86.07</t></si>
              <si><t xml:space="preserve">გადახდა - NIKORA 7.14 GEL 09.07.2025</t></si>
            </sst>"""
        val sheet1 = """<?xml version="1.0" encoding="utf-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
              </sheetData>
            </worksheet>"""
        val sheet2 = """<?xml version="1.0" encoding="utf-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="s"><v>2</v></c><c r="B1" t="s"><v>3</v></c></row>
                <row r="2">
                  <c r="A2"><v>45849</v></c>
                  <c r="B2" t="s"><v>4</v></c>
                  <c r="C2" t="s"><v>5</v></c>
                  <c r="E2" t="s"><v>6</v></c>
                  <c r="F2" t="s"><v>7</v></c>
                </row>
              </sheetData>
            </worksheet>"""

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", rels)
            put("xl/sharedStrings.xml", sharedStrings)
            put("xl/worksheets/sheet1.xml", sheet1)
            put("xl/worksheets/sheet2.xml", sheet2)
        }
        return out.toByteArray()
    }

    @Test
    fun `reads sheets shared strings and numeric cells`() {
        val wb = XlsxSheetReader().read(ByteArrayInputStream(syntheticXlsx()))

        assertEquals(setOf("Account Details", "Transactions"), wb.sheets.keys)

        val details = wb.sheets.getValue("Account Details")
        assertEquals("Account Number", details[0].cells["A"])
        assertEquals("GE00CD0000000000000000", details[0].cells["B"])

        val tx = wb.sheets.getValue("Transactions")
        val dataRow = tx.first { it.index == 2 }
        assertEquals("45849", dataRow.cells["A"])
        assertEquals("საბარათე ოპერაცია", dataRow.cells["B"])
        assertEquals("7.14", dataRow.cells["C"])
        assertEquals("გადახდა - NIKORA 7.14 GEL 09.07.2025", dataRow.cells["F"])
    }
}
