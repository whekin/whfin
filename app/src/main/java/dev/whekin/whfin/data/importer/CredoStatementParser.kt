package dev.whekin.whfin.data.importer

import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Парсер выписки MYCREDO (*.xlsx): лист "Account Details" + лист "Transactions".
 * Проверен на приватных fixtures, покрывающих 17 типов операций.
 */
object CredoStatementParser {

    /** Семантика типа операции; сырое грузинское название — стабильный ключ. */
    enum class OperationType {
        /** საბარათე ოპერაცია — оплата картой у мерчанта. */
        CARD_PAYMENT,

        /** საკუთარ ანგარიშებს შორის გადარიცხვა — перевод между своими счетами. */
        OWN_TRANSFER,

        /** უნაღდო კონვერტაცია — конвертация валюты (тоже пара, не расход). */
        CURRENCY_EXCHANGE,

        /** თანხის გადარიცხვა / სწრაფი გადარიცხვა / კრედო-კრედო — перевод человеку. */
        TRANSFER_OUT,

        /** სხვა ბანკიდან ჩარიცხვა / ბარათზე ჩარიცხვა — входящий перевод. */
        TRANSFER_IN,

        /** Комиссии всех видов — приклеивать к родительской операции. */
        FEE,

        /** გადახდები — оплата услуг (мобильный и т.п.). */
        BILL_PAYMENT,

        /** ელექტრონული ყულაბა — автопополнение вклада-копилки. */
        SAVINGS_TOPUP,

        /** Внесение наличных (аппарат/касса). */
        CASH_DEPOSIT,

        /** საპროცენტო სარგებელი — проценты по вкладу. */
        INTEREST,

        OTHER,
    }

    private val operationMap = mapOf(
        "საბარათე ოპერაცია" to OperationType.CARD_PAYMENT,
        "საკუთარ ანგარიშებს შორის გადარიცხვა" to OperationType.OWN_TRANSFER,
        "უნაღდო კონვერტაცია" to OperationType.CURRENCY_EXCHANGE,
        "თანხის გადარიცხვა" to OperationType.TRANSFER_OUT,
        "სწრაფი გადარიცხვა" to OperationType.TRANSFER_OUT,
        "გადარიცხვა კრედო ბანკის კლიენტებს შორის" to OperationType.TRANSFER_OUT,
        "ლარის გადარიცხვის საკომისიო" to OperationType.FEE,
        "სწრაფი გადარიცხვის საკომისიო" to OperationType.FEE,
        "სხვა და სხვა საკომისიო" to OperationType.FEE,
        "გადახდები" to OperationType.BILL_PAYMENT,
        "ელექტრონული ყულაბის სერვისით ანაბარზე თანხის დამატება" to OperationType.SAVINGS_TOPUP,
        "სხვა ბანკიდან ჩარიცხვა" to OperationType.TRANSFER_IN,
        "ბარათზე თანხის ჩარიცხვა" to OperationType.TRANSFER_IN,
        "სწრაფი გადახდის აპარატით თანხის შეტანა" to OperationType.CASH_DEPOSIT,
        "ანგარიშზე თანხის შეტანა" to OperationType.CASH_DEPOSIT,
        "საპროცენტო სარგებლის გადახდა" to OperationType.INTEREST,
        "ანაბრის თანხის გადატანა" to OperationType.OWN_TRANSFER,
    )

    data class Statement(
        val accountIban: String,
        val currency: String,
        val periodFrom: LocalDate?,
        val periodTo: LocalDate?,
        val openingBalanceMinor: Long?,
        val closingBalanceMinor: Long?,
        val rows: List<Row>,
    )

    data class Row(
        /** Дата списания (колонка Date). */
        val postedDate: LocalDate,
        val operation: OperationType,
        val operationRaw: String,
        /** Со знаком: дебет < 0, кредит > 0. Minor units. */
        val amountMinor: Long,
        val balanceAfterMinor: Long?,
        val description: String,
        val beneficiaryName: String?,
        val beneficiaryAccount: String?,
        /** Для CARD_PAYMENT: сырое имя мерчанта из Description. */
        val merchantRaw: String? = null,
        /** Для CARD_PAYMENT: реальная дата покупки из Description (раньше даты списания). */
        val purchaseDate: LocalDate? = null,
    )

    /** `გადახდა - NIKORA 7.14 GEL 09.07.2025` -> (NIKORA, 09.07.2025) */
    private val cardDescriptionRegex =
        Regex("""^გადახდა - (.+?)\s+[\d,]+\.\d{2} [A-Z]{3} (\d{2}\.\d{2}\.\d{4})$""")

    private val purchaseDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Excel serial date -> LocalDate (эпоха 1899-12-30, как в openpyxl). */
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    fun parse(input: InputStream): Statement {
        val workbook = XlsxSheetReader().read(input)
        val details = workbook.sheets["Account Details"]
            ?: error("Sheet 'Account Details' not found — not a MYCREDO statement?")
        val txSheet = workbook.sheets["Transactions"]
            ?: error("Sheet 'Transactions' not found — not a MYCREDO statement?")

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

        return Statement(
            accountIban = meta["Account Number"] ?: error("Account Number missing"),
            currency = meta["Account Currency"] ?: "GEL",
            periodFrom = period?.get(0)?.let { LocalDate.parse(it, purchaseDateFormat) },
            periodTo = period?.get(1)?.let { LocalDate.parse(it, purchaseDateFormat) },
            openingBalanceMinor = meta["Opening Balance"]?.let(::moneyToMinor),
            closingBalanceMinor = meta["Closing Balance"]?.let(::moneyToMinor),
            rows = rows,
        )
    }

    private fun parseRow(row: XlsxSheetReader.Row): Row? {
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
        val operation = operationMap[operationRaw] ?: OperationType.OTHER

        var merchantRaw: String? = null
        var purchaseDate: LocalDate? = null
        if (operation == OperationType.CARD_PAYMENT) {
            cardDescriptionRegex.find(description)?.let { match ->
                merchantRaw = match.groupValues[1].trim()
                purchaseDate = runCatching {
                    LocalDate.parse(match.groupValues[2], purchaseDateFormat)
                }.getOrNull()
            }
        }

        return Row(
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
