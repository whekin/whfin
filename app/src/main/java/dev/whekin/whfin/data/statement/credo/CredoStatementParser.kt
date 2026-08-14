package dev.whekin.whfin.data.statement.credo

import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementFile
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementParser
import dev.whekin.whfin.data.statement.StatementRow
import dev.whekin.whfin.data.statement.MalformedStatementException
import dev.whekin.whfin.data.statement.XlsxSheetReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Credo adapter: выписка MYCREDO (*.xlsx), лист "Account Details" + лист "Transactions".
 * Проверен на приватных fixtures, покрывающих 17 типов операций.
 *
 * Всё грузино-специфичное живёт здесь; наружу выходит только bank-neutral [BankStatement].
 */
object CredoStatementParser : StatementParser {

    override val bank = BankProfile(provider = "Credo", displayName = "Credo")

    /** Обе формы встречаются в Description конвертации: английская и грузинская. */
    override val conversionNoteMarkers = listOf("exchange", "კონვერტ")

    private val detailsSheetNames = setOf("account details", "account detail")
    private val transactionsSheetNames = setOf("transactions", "transaction details")

    /** Сырое грузинское название операции — стабильный ключ. */
    private val operationMap = mapOf(
        "საბარათე ოპერაცია" to StatementOperation.CARD_PAYMENT,
        "საკუთარ ანგარიშებს შორის გადარიცხვა" to StatementOperation.OWN_TRANSFER,
        "უნაღდო კონვერტაცია" to StatementOperation.CURRENCY_EXCHANGE,
        "თანხის გადარიცხვა" to StatementOperation.TRANSFER_OUT,
        "სწრაფი გადარიცხვა" to StatementOperation.TRANSFER_OUT,
        "გადარიცხვა კრედო ბანკის კლიენტებს შორის" to StatementOperation.TRANSFER_OUT,
        "ლარის გადარიცხვის საკომისიო" to StatementOperation.FEE,
        "გადარიცხვის საკომისიო" to StatementOperation.FEE,
        "სწრაფი გადარიცხვის საკომისიო" to StatementOperation.FEE,
        "სხვა და სხვა საკომისიო" to StatementOperation.FEE,
        "გადახდები" to StatementOperation.BILL_PAYMENT,
        "ელექტრონული ყულაბის სერვისით ანაბარზე თანხის დამატება" to StatementOperation.SAVINGS_TOPUP,
        "სხვა ბანკიდან ჩარიცხვა" to StatementOperation.TRANSFER_IN,
        "ბარათზე თანხის ჩარიცხვა" to StatementOperation.TRANSFER_IN,
        "სწრაფი გადახდის აპარატით თანხის შეტანა" to StatementOperation.CASH_DEPOSIT,
        "ანგარიშზე თანხის შეტანა" to StatementOperation.CASH_DEPOSIT,
        "საპროცენტო სარგებლის გადახდა" to StatementOperation.INTEREST,
        "ანაბრის თანხის გადატანა" to StatementOperation.OWN_TRANSFER,
    )
    private val normalizedOperationMap = operationMap.mapKeys { (name, _) -> normalizedLabel(name) }

    /** `გადახდა - NIKORA 7.14 GEL 09.07.2025` -> (NIKORA, 09.07.2025) */
    private val cardDescriptionRegex =
        Regex("""^გადახდა - (.+?)\s+[\d,]+\.\d{2} [A-Z]{3} (\d{2}\.\d{2}\.\d{4})$""")

    private val purchaseDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** One date of a statement period, in any of the orders and separators Credo has used. */
    private val periodDateRegex = Regex("""\d{1,4}[./-]\d{1,2}[./-]\d{1,4}""")

    /** Excel serial date -> LocalDate (эпоха 1899-12-30, как в openpyxl). */
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    override fun operationFor(rawLabel: String): StatementOperation? =
        normalizedOperationMap[normalizedLabel(rawLabel)]

    override fun canParse(file: StatementFile): Boolean = runCatching {
        val sheets = file.open().use { XlsxSheetReader().read(it) }.sheets
        sheets.keys.any { normalizedLabel(it) in detailsSheetNames } &&
            sheets.keys.any { normalizedLabel(it) in transactionsSheetNames }
    }.getOrDefault(false)

    override fun parse(file: StatementFile): BankStatement {
        val workbook = file.open().use { XlsxSheetReader().read(it) }
        val details = workbook.sheets.entries
            .firstOrNull { normalizedLabel(it.key) in detailsSheetNames }
            ?.value
            ?: malformed("Credo account details sheet not found.")
        val txSheet = workbook.sheets.entries
            .firstOrNull { normalizedLabel(it.key) in transactionsSheetNames }
            ?.value
            ?: malformed("Credo transactions sheet not found.")

        val meta = details.associate { row ->
            normalizedLabel(row.cells["A"].orEmpty()) to row.cells["B"].orEmpty().trim()
        }
        val period = splitPeriod(requiredMeta(meta, "statement period", "period"))

        val header = txSheet.firstOrNull { row ->
            row.cells.values.any { normalizedLabel(it) == "date" } &&
                row.cells.values.any { normalizedLabel(it) == "operation" }
        } ?: malformed("Credo transactions header row not found.")
        val columns = TransactionColumns.from(header)
        val rows = txSheet
            .filter { row ->
                row.index > header.index &&
                    (row.cells[columns.date]?.toDoubleOrNull() != null ||
                        columns.financial.any { column -> !row.cells[column].isNullOrBlank() })
            }
            .map { row ->
                parseRow(row, columns) ?: throw MalformedStatementException(
                    "Credo statement row ${row.index} contains incomplete financial data.",
                )
            }

        return BankStatement(
            bank = bank,
            accountIban = requiredMeta(meta, "account number", "iban"),
            currency = requiredMeta(meta, "account currency", "currency").uppercase(Locale.ROOT),
            periodFrom = parseCredoDate(period[0]),
            periodTo = parseCredoDate(period[1]),
            openingBalanceMinor = requiredMoney(meta, "opening balance", "beginning balance"),
            closingBalanceMinor = requiredMoney(meta, "closing balance", "ending balance"),
            rows = rows,
        )
    }

    private fun parseRow(row: XlsxSheetReader.Row, columns: TransactionColumns): StatementRow? {
        val serial = row.cells[columns.date]?.toDoubleOrNull() ?: return null
        val operationRaw = row.cells[columns.operation]?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val debit = row.cells[columns.debit]?.let(::moneyToMinor)
        val credit = row.cells[columns.credit]?.let(::moneyToMinor)
        val debitAmount = debit?.takeIf { it != 0L }
        val creditAmount = credit?.takeIf { it != 0L }
        val amount = when {
            debitAmount != null && creditAmount != null -> return null
            debitAmount != null -> -debitAmount
            creditAmount != null -> creditAmount
            else -> return null
        }
        val description = columns.description?.let(row.cells::get)?.trim().orEmpty()
        val operation = normalizedOperationMap[normalizedLabel(operationRaw)] ?: StatementOperation.OTHER

        var merchantRaw: String? = null
        var purchaseDate: LocalDate? = null
        if (operation == StatementOperation.CARD_PAYMENT) {
            cardDescriptionRegex.find(description)?.let { match ->
                merchantRaw = match.groupValues[1].trim()
                purchaseDate = runCatching {
                    LocalDate.parse(match.groupValues[2], purchaseDateFormat)
                }.getOrNull()
            }
        }

        return StatementRow(
            postedDate = excelEpoch.plusDays(serial.toLong()),
            operation = operation,
            operationRaw = operationRaw,
            amountMinor = amount,
            balanceAfterMinor = row.cells[columns.balance]?.let(::moneyToMinor),
            description = description,
            beneficiaryName = columns.beneficiaryName?.let(row.cells::get)?.trim()?.takeIf { it.isNotEmpty() },
            beneficiaryAccount = columns.beneficiaryAccount?.let(row.cells::get)?.trim()?.takeIf { it.isNotEmpty() },
            merchantRaw = merchantRaw,
            purchaseDate = purchaseDate,
        )
    }

    private data class TransactionColumns(
        val date: String,
        val operation: String,
        val debit: String,
        val credit: String,
        val balance: String,
        val description: String?,
        val beneficiaryName: String?,
        val beneficiaryAccount: String?,
    ) {
        val financial = listOf(operation, debit, credit, balance)

        companion object {
            fun from(header: XlsxSheetReader.Row): TransactionColumns {
                val labels = header.cells.entries.groupBy(
                    keySelector = { (_, value) -> normalizedLabel(value) },
                    valueTransform = { (column, _) -> column },
                )
                fun columns(vararg aliases: String): List<String> = aliases
                    .flatMap { alias -> labels[alias].orEmpty() }
                    .distinct()
                fun required(vararg aliases: String): String {
                    val candidates = columns(*aliases)
                    if (candidates.size != 1) {
                        malformed("Credo transactions column '${aliases.first()}' is missing or ambiguous.")
                    }
                    return candidates.single()
                }
                fun optional(vararg aliases: String): String? {
                    val candidates = columns(*aliases)
                    if (candidates.size > 1) {
                        malformed("Credo transactions column '${aliases.first()}' is ambiguous.")
                    }
                    return candidates.singleOrNull()
                }

                return TransactionColumns(
                    date = required("date", "posting date"),
                    operation = required("operation", "operation type"),
                    debit = required("turnover db", "turnover debit", "debit"),
                    credit = required("turnover cr", "turnover credit", "credit"),
                    balance = required("balance", "account balance"),
                    description = optional("description", "details"),
                    beneficiaryName = optional("beneficiary name", "beneficiary"),
                    beneficiaryAccount = optional("beneficiary account", "beneficiary iban"),
                )
            }
        }
    }

    private fun requiredMeta(meta: Map<String, String>, vararg aliases: String): String =
        aliases.asSequence().mapNotNull(meta::get).firstOrNull(String::isNotBlank)
            ?: malformed("Credo statement field '${aliases.first()}' is missing.")

    private fun requiredMoney(meta: Map<String, String>, vararg aliases: String): Long =
        moneyToMinor(requiredMeta(meta, *aliases))
            ?: malformed("Credo statement field '${aliases.first()}' is unreadable.")

    /**
     * The two ends of the statement period, however Credo chose to join them today.
     *
     * The separator has already changed once (a spaced dash became a spaced colon), and an export
     * covering a single day states that day once. Neither is a reason to refuse a statement whose
     * balances and rows are intact, so the shape of the join is read loosely while the dates
     * themselves stay strict. A value that yields no date at all still stops the import, and it says
     * what it saw: without that, the same failure is undiagnosable once the file is gone.
     */
    private fun splitPeriod(raw: String): List<String> {
        // Reading the dates rather than the separator: a dash also lives inside an ISO date, so
        // splitting on punctuation would break the very format it was meant to accept.
        val dates = periodDateRegex.findAll(raw).map { it.value }.toList()
        return when (dates.size) {
            1 -> listOf(dates[0], dates[0])
            2 -> dates
            else -> malformed("Credo statement period is unreadable: \"$raw\".")
        }
    }

    private fun parseCredoDate(raw: String): LocalDate =
        listOf(
            purchaseDateFormat,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )
            .firstNotNullOfOrNull { format -> runCatching { LocalDate.parse(raw, format) }.getOrNull() }
            ?: malformed("Credo statement date is unreadable.")

    private fun malformed(message: String): Nothing = throw MalformedStatementException(message)

    private fun normalizedLabel(raw: String): String = raw
        .replace('\u00A0', ' ')
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("""[():]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** "1,083.20" -> 108320 */
    fun moneyToMinor(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return runCatching { BigDecimal(cleaned).movePointRight(2).longValueExact() }.getOrNull()
    }
}
