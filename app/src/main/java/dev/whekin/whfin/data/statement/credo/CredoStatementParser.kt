package dev.whekin.whfin.data.statement.credo

import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementFile
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementParser
import dev.whekin.whfin.data.statement.StatementRow
import dev.whekin.whfin.data.statement.XlsxSheetReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    private const val DETAILS_SHEET = "Account Details"
    private const val TRANSACTIONS_SHEET = "Transactions"

    /** Сырое грузинское название операции — стабильный ключ. */
    private val operationMap = mapOf(
        "საბარათე ოპერაცია" to StatementOperation.CARD_PAYMENT,
        "საკუთარ ანგარიშებს შორის გადარიცხვა" to StatementOperation.OWN_TRANSFER,
        "უნაღდო კონვერტაცია" to StatementOperation.CURRENCY_EXCHANGE,
        "თანხის გადარიცხვა" to StatementOperation.TRANSFER_OUT,
        "სწრაფი გადარიცხვა" to StatementOperation.TRANSFER_OUT,
        "გადარიცხვა კრედო ბანკის კლიენტებს შორის" to StatementOperation.TRANSFER_OUT,
        "ლარის გადარიცხვის საკომისიო" to StatementOperation.FEE,
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

    /** `გადახდა - NIKORA 7.14 GEL 09.07.2025` -> (NIKORA, 09.07.2025) */
    private val cardDescriptionRegex =
        Regex("""^გადახდა - (.+?)\s+[\d,]+\.\d{2} [A-Z]{3} (\d{2}\.\d{2}\.\d{4})$""")

    private val purchaseDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Excel serial date -> LocalDate (эпоха 1899-12-30, как в openpyxl). */
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    override fun canParse(file: StatementFile): Boolean = runCatching {
        val sheets = file.open().use { XlsxSheetReader().read(it) }.sheets
        sheets.containsKey(DETAILS_SHEET) && sheets.containsKey(TRANSACTIONS_SHEET)
    }.getOrDefault(false)

    override fun parse(file: StatementFile): BankStatement {
        val workbook = file.open().use { XlsxSheetReader().read(it) }
        val details = workbook.sheets[DETAILS_SHEET]
            ?: error("Sheet '$DETAILS_SHEET' not found — not a MYCREDO statement?")
        val txSheet = workbook.sheets[TRANSACTIONS_SHEET]
            ?: error("Sheet '$TRANSACTIONS_SHEET' not found — not a MYCREDO statement?")

        val meta = details.associate { row ->
            (row.cells["A"] ?: "").trim() to (row.cells["B"] ?: "").trim()
        }
        val period = meta["Statement Period"]
            ?.split("-")
            ?.map { it.trim() }
            ?.takeIf { it.size == 2 }

        val header = txSheet.firstOrNull { it.cells["A"] == "Date" }
            ?: error("Transactions header row not found")
        val rows = txSheet
            .filter { it.index > header.index }
            .mapNotNull(::parseRow)

        return BankStatement(
            bank = bank,
            accountIban = meta["Account Number"] ?: error("Account Number missing"),
            currency = meta["Account Currency"] ?: "GEL",
            periodFrom = period?.get(0)?.let { LocalDate.parse(it, purchaseDateFormat) },
            periodTo = period?.get(1)?.let { LocalDate.parse(it, purchaseDateFormat) },
            openingBalanceMinor = meta["Opening Balance"]?.let(::moneyToMinor),
            closingBalanceMinor = meta["Closing Balance"]?.let(::moneyToMinor),
            rows = rows,
        )
    }

    private fun parseRow(row: XlsxSheetReader.Row): StatementRow? {
        val serial = row.cells["A"]?.toDoubleOrNull() ?: return null
        val operationRaw = row.cells["B"]?.trim() ?: return null
        val debit = row.cells["C"]?.let(::moneyToMinor)
        val credit = row.cells["D"]?.let(::moneyToMinor)
        val amount = when {
            debit != null && debit != 0L -> -debit
            credit != null -> credit
            else -> return null
        }
        val description = row.cells["F"]?.trim().orEmpty()
        val operation = operationMap[operationRaw] ?: StatementOperation.OTHER

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
            balanceAfterMinor = row.cells["E"]?.let(::moneyToMinor),
            description = description,
            beneficiaryName = row.cells["G"]?.trim()?.takeIf { it.isNotEmpty() },
            beneficiaryAccount = row.cells["H"]?.trim()?.takeIf { it.isNotEmpty() },
            merchantRaw = merchantRaw,
            purchaseDate = purchaseDate,
        )
    }

    /** "1,083.20" -> 108320 */
    fun moneyToMinor(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return runCatching { BigDecimal(cleaned).movePointRight(2).longValueExact() }.getOrNull()
    }
}
