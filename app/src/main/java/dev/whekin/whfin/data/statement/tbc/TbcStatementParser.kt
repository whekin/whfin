package dev.whekin.whfin.data.statement.tbc

import dev.whekin.whfin.data.statement.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** TBC Online account XLSX: bilingual Summary and one IBAN-currency transaction sheet. */
object TbcStatementParser : StatementParser {
    override val bank = BankProfile("TBC", "TBC")
    private val epoch = LocalDate.of(1899, 12, 30)
    private val card = Regex("""^POS(?: wallet)? - (.+), ([\d,.]+) ([A-Z]{3}), ([A-Za-z]{3}\s+\d{1,2}\s+\d{4}\s+\d{1,2}:\d{2}[AP]M),.*$""")
    private val purchaseFormat = DateTimeFormatter.ofPattern("MMM d uuuu h:mma", Locale.ENGLISH)
        .withResolverStyle(ResolverStyle.STRICT)
    private val origin = Regex("""ა/ნ:\s*(GE[0-9]{2}[A-Z]{2}[0-9]{16})(?![A-Z0-9])""")

    override fun originAccountFromNote(accountIban: String, note: String): String? {
        if (!accountIban.matches(Regex("GE[0-9]{2}TB[0-9]{16}"))) return null
        return origin.findAll(note).map { it.groupValues[1] }.distinct().toList().singleOrNull()
    }

    override fun canParse(file: StatementFile): Boolean = runCatching {
        val sheets = file.open().use { XlsxSheetReader().read(it) }.sheets
        sheets["Summary"]?.any { it.cells["B"] == "Account No:" } == true
    }.getOrDefault(false)

    override fun parse(file: StatementFile): BankStatement {
        val sheets = file.open().use { XlsxSheetReader().read(it) }.sheets
        val summary = sheets["Summary"] ?: bad("Summary is missing")
        val meta = summary.filter { !it.cells["B"].isNullOrBlank() }.groupBy { it.cells["B"]!!.trim() }
        fun value(label: String): String = meta[label]?.singleOrNull()?.cells?.get("C")
            ?.trim()?.takeIf { it.isNotEmpty() } ?: bad("Missing or ambiguous $label")
        val iban = value("Account No:").uppercase(Locale.ROOT)
        val currency = value("Currency:").uppercase(Locale.ROOT)
        if (!iban.matches(Regex("GE[0-9]{2}TB[0-9]{16}"))) bad("Invalid account IBAN")
        if (!currency.matches(Regex("[A-Z]{3}"))) bad("Invalid currency")
        // Never silently read only one ledger out of a multi-account export.
        if (sheets.keys != setOf("Summary", "$iban-$currency")) bad("Export one account and currency per file")
        val sheet = sheets.getValue("$iban-$currency")
        val header = sheet.singleOrNull { it.cells.values.containsAll(listOf("Date", "Paid Out", "Transaction ID")) }
            ?: bad("Transaction header is missing or ambiguous")
        val labels = header.cells.entries.groupBy({ it.value.trim() }, { it.key })
        fun column(label: String) = labels[label]?.singleOrNull() ?: bad("Missing or ambiguous column $label")
        val names = listOf("Date", "Description", "Paid Out", "Paid In", "Balance", "Type", "Partner's Account", "Partner's Name", "Op. Code", "Transaction ID")
        val columns = names.associateWith(::column)
        val rows = sheet.filter { it.index > header.index && it.cells.values.any(String::isNotBlank) }.map { source ->
            fun cell(label: String) = source.cells[columns.getValue(label)].orEmpty().trim()
            try {
                val debit = cell("Paid Out").takeIf(String::isNotEmpty)?.let(::money) ?: 0L
                val credit = cell("Paid In").takeIf(String::isNotEmpty)?.let(::money) ?: 0L
                if (debit < 0 || credit < 0 || (debit == 0L) == (credit == 0L)) bad("Expected one positive debit or credit")
                val description = cell("Description")
                val type = cell("Type").ifBlank { bad("Missing operation type") }
                val id = cell("Transaction ID")
                if (!id.matches(Regex("[0-9]+"))) bad("Invalid transaction ID")
                val isCard = description.startsWith("POS - ") || description.startsWith("POS wallet - ")
                val purchase = if (isCard) card.matchEntire(description) ?: bad("Unrecognized POS description") else null
                val operation = when {
                    isCard -> StatementOperation.CARD_PAYMENT
                    cell("Op. Code") == "*TPC*" && debit > 0 -> StatementOperation.FEE
                    type == "Income" && credit > 0 -> StatementOperation.TRANSFER_IN
                    else -> StatementOperation.OTHER
                }
                StatementRow(
                    postedDate = date(cell("Date")), operation = operation, operationRaw = type,
                    amountMinor = credit - debit, balanceAfterMinor = money(cell("Balance")),
                    description = description,
                    beneficiaryName = cell("Partner's Name").takeIf(String::isNotEmpty),
                    beneficiaryAccount = cell("Partner's Account").takeIf(String::isNotEmpty),
                    merchantRaw = purchase?.groupValues?.get(1)?.trim(),
                    purchaseDate = purchase?.groupValues?.get(4)?.let {
                        LocalDateTime.parse(it.replace(Regex("\\s+"), " "), purchaseFormat).toLocalDate()
                    },
                    bankTransactionId = id,
                )
            } catch (error: Exception) {
                // Diagnostics identify the row, never copy private account/card text into an error.
                bad("Invalid financial data in row ${source.index}")
            }
        }
        if (rows.map { it.bankTransactionId }.distinct().size != rows.size) bad("Duplicate transaction ID")
        if (rows.zipWithNext().any { (a, b) -> a.postedDate > b.postedDate }) bad("Rows are not in posting order")
        val paidOut = rows.fold(0L) { sum, row -> Math.addExact(sum, if (row.amountMinor < 0) -row.amountMinor else 0) }
        val paidIn = rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor.coerceAtLeast(0)) }
        if (paidOut != money(value("Paid Out:")) || paidIn != money(value("Paid In:"))) bad("Turnover does not match Summary")
        return BankStatement(bank, iban, currency, date(value("Filter Date From:")), date(value("Filter Date To:")),
            money(value("Starting Balance:")), money(value("Closing Balance:")), rows)
    }

    private fun money(value: String): Long = BigDecimal(value).movePointRight(2).longValueExact()
    private fun date(value: String): LocalDate = epoch.plusDays(BigDecimal(value).longValueExact())
    private fun bad(message: String): Nothing = throw MalformedStatementException("TBC: $message.")
}
