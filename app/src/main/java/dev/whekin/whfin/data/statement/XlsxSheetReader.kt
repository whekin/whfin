package dev.whekin.whfin.data.statement

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Минимальный ридер xlsx под фиксированные банковские выписки.
 * xlsx = ZIP c XML внутри; Apache POI не тянем сознательно (размер, совместимость с Android).
 *
 * Поддержано ровно то, что встречается в выписках: shared strings (`t="s"`),
 * inline strings (`t="inlineStr"`), числовые ячейки (даты-сериалы и числа).
 * Формулы, стили, rich text runs внутри si — по минимуму (конкатенация всех <t>).
 */
class XlsxSheetReader {

    /** Ячейка: колонка (буква) -> строковое значение; числовые значения как в XML (raw). */
    data class Row(val index: Int, val cells: Map<String, String>)

    data class Workbook(val sheets: Map<String, List<Row>>)

    fun read(input: InputStream): Workbook {
        val entries = mutableMapOf<String, ByteArray>()
        var entryCount = 0
        var expandedBytes = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount += 1
                if (entryCount > MAX_ENTRIES) malformed("XLSX archive contains too many entries.")
                val name = entry.name
                if (name.startsWith('/') || '\\' in name || name.split('/').any { it == ".." }) {
                    malformed("XLSX archive contains an unsafe entry path.")
                }
                if (entry.size > MAX_ENTRY_BYTES) malformed("XLSX archive entry is too large.")
                if (!entry.isDirectory) {
                    val bytes = zip.readEntryBounded()
                    expandedBytes += bytes.size
                    if (expandedBytes > MAX_EXPANDED_BYTES) {
                        malformed("XLSX archive expands beyond the safe limit.")
                    }
                    if (name.isWorkbookData()) {
                        if (entries.put(name, bytes) != null) {
                            malformed("XLSX archive contains duplicate entries.")
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetNamesById = parseWorkbookSheets(
            entries["xl/workbook.xml"] ?: error("workbook.xml missing"),
        )
        val targetsByRid = parseWorkbookRels(
            entries["xl/_rels/workbook.xml.rels"] ?: error("workbook.xml.rels missing"),
        )

        val sheets = sheetNamesById.mapNotNull { (rid, name) ->
            val target = targetsByRid[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.drop(1) else "xl/$target"
            val bytes = entries[path] ?: return@mapNotNull null
            name to parseSheet(bytes, shared)
        }.toMap()

        return Workbook(sheets)
    }

    private fun String.isWorkbookData(): Boolean =
        this == "xl/workbook.xml" ||
            this == "xl/_rels/workbook.xml.rels" ||
            this == "xl/sharedStrings.xml" ||
            (startsWith("xl/worksheets/") && endsWith(".xml"))

    private fun ZipInputStream.readEntryBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) malformed("XLSX archive entry is too large.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun malformed(message: String): Nothing = throw MalformedStatementException(message)

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        // Файлы MYCREDO начинаются с UTF-8 BOM — kxml на нём падает ("PI must not start with xml")
        val start = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        parser.setInput(bytes.inputStream(start, bytes.size - start), "UTF-8")
        return parser
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val result = mutableListOf<String>()
        var current: StringBuilder? = null
        var inT = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) current?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> {
                        result += current?.toString().orEmpty()
                        current = null
                    }
                }
            }
        }
        return result
    }

    /** workbook.xml: sheet name + r:id в порядке объявления. */
    private fun parseWorkbookSheets(bytes: ByteArray): List<Pair<String, String>> {
        val parser = newParser(bytes)
        val result = mutableListOf<Pair<String, String>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: continue
                // Без namespace-processing атрибут приходит как "r:id"
                val rid = (0 until parser.attributeCount)
                    .firstOrNull {
                        val attr = parser.getAttributeName(it)
                        attr == "id" || attr.endsWith(":id")
                    }
                    ?.let { parser.getAttributeValue(it) } ?: continue
                result += rid to name
            }
        }
        return result
    }

    private fun parseWorkbookRels(bytes: ByteArray): Map<String, String> {
        val parser = newParser(bytes)
        val result = mutableMapOf<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id") ?: continue
                val target = parser.getAttributeValue(null, "Target") ?: continue
                result[id] = target
            }
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Row> {
        val parser = newParser(bytes)
        val rows = mutableListOf<Row>()
        var rowIndex = 0
        var cells: MutableMap<String, String>? = null
        var cellRef: String? = null
        var cellType: String? = null
        var inV = false
        var inInlineT = false
        val value = StringBuilder()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowIndex = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                        cells = mutableMapOf()
                    }
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r")
                        cellType = parser.getAttributeValue(null, "t")
                        value.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inInlineT = true
                }
                XmlPullParser.TEXT -> if (inV || inInlineT) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inInlineT = false
                    "c" -> {
                        val ref = cellRef
                        val raw = value.toString()
                        if (ref != null && raw.isNotEmpty()) {
                            val column = ref.takeWhile { it.isLetter() }
                            val resolved = if (cellType == "s") {
                                shared.getOrElse(raw.trim().toInt()) { "" }
                            } else {
                                raw
                            }
                            if (resolved.isNotEmpty()) cells?.put(column, resolved)
                        }
                        cellRef = null
                        cellType = null
                    }
                    "row" -> {
                        cells?.let { rows += Row(rowIndex, it) }
                        cells = null
                    }
                }
            }
        }
        return rows
    }

    private companion object {
        const val MAX_ENTRIES = 512
        const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024
    }
}
