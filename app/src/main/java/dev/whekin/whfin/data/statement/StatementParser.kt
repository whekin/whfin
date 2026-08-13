package dev.whekin.whfin.data.statement

import dev.whekin.whfin.data.statement.credo.CredoStatementParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A statement file buffered in memory so several adapters can probe the same bytes.
 *
 * Bank exports are small (a year of one ledger is well under a megabyte), and the importer already
 * reads the whole file before its single Room transaction.
 */
class StatementFile(val fileName: String?, private val bytes: ByteArray) {

    fun open(): InputStream = ByteArrayInputStream(bytes)

    companion object {
        private const val MAX_STATEMENT_BYTES = 32 * 1024 * 1024

        fun read(input: InputStream, fileName: String? = null): StatementFile =
            StatementFile(fileName, input.readStatementBytes())

        private fun InputStream.readStatementBytes(): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_STATEMENT_BYTES) {
                    throw MalformedStatementException("Statement file is larger than 32 MiB.")
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}

/** No registered adapter recognized the file. */
class UnsupportedStatementException(val fileName: String?) :
    IllegalArgumentException("Unsupported statement file: ${fileName ?: "unnamed"}")

/** An adapter recognized the file, but one of its financial rows could not be parsed safely. */
class MalformedStatementException(message: String) : IllegalArgumentException(message)

/**
 * Bank-specific boundary. An adapter turns one bank's export into a [BankStatement]; deduplication,
 * import, reconciliation, coverage/history and the review queue stay shared for every bank.
 */
interface StatementParser {

    val bank: BankProfile

    /**
     * Note fragments that mark a conversion leg of this bank.
     *
     * Transfer pairing runs over already stored transactions, long after the file is gone, so the
     * vocabulary belongs to the adapter rather than to the importer.
     */
    val conversionNoteMarkers: List<String>
        get() = emptyList()

    /** Structural probe over the same bytes [parse] will read. Must not throw. */
    fun canParse(file: StatementFile): Boolean

    fun parse(file: StatementFile): BankStatement
}

/** Registry of supported banks. Adding a bank means adding an adapter here, nothing else. */
object StatementParsers {

    /**
     * Probes are strict, so order only matters if two banks ever share a structure.
     */
    val all: List<StatementParser> = listOf(CredoStatementParser)

    val conversionNoteMarkers: List<String> = all.flatMap { it.conversionNoteMarkers }.distinct()

    fun parse(file: StatementFile, parsers: List<StatementParser> = all): BankStatement {
        val parser = parsers.firstOrNull { runCatching { it.canParse(file) }.getOrDefault(false) }
            ?: throw UnsupportedStatementException(file.fileName)
        return parser.parse(file)
    }
}
