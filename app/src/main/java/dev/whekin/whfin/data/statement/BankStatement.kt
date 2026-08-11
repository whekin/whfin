package dev.whekin.whfin.data.statement

import java.time.LocalDate

/** Bank whose export format one [StatementParser] understands. */
data class BankProfile(
    /** Stable key stored as `FinancialGroup.provider`; never localized. */
    val provider: String,
    val displayName: String,
)

/**
 * Bank-neutral meaning of a statement row.
 *
 * Each adapter maps its own raw operation names onto these values; the importer, reconciliation,
 * coverage and review queue only ever see this enum.
 */
enum class StatementOperation {
    /** Card payment at a merchant. */
    CARD_PAYMENT,

    /** Transfer between the owner's own accounts. */
    OWN_TRANSFER,

    /** Currency conversion; also a pair of legs, not income or expense. */
    CURRENCY_EXCHANGE,

    /** Outgoing transfer to somebody else. */
    TRANSFER_OUT,

    /** Incoming transfer. */
    TRANSFER_IN,

    /** Any fee; belongs to the operation it follows. */
    FEE,

    /** Bill/service payment. */
    BILL_PAYMENT,

    /** Automatic top-up of a savings product. */
    SAVINGS_TOPUP,

    /** Cash paid into the account. */
    CASH_DEPOSIT,

    /** Interest paid by the bank. */
    INTEREST,

    OTHER;

    /** Movement inside the owner's own money: excluded from income and expenses. */
    val isOwnMovement: Boolean
        get() = this == OWN_TRANSFER || this == CURRENCY_EXCHANGE || this == SAVINGS_TOPUP
}

/** One parsed statement for exactly one currency ledger of one account. */
data class BankStatement(
    val bank: BankProfile,
    val accountIban: String,
    val currency: String,
    val periodFrom: LocalDate?,
    val periodTo: LocalDate?,
    val openingBalanceMinor: Long?,
    val closingBalanceMinor: Long?,
    val rows: List<StatementRow>,
) {
    /** Balance-proven bank labels that were imported but are not classified yet. */
    val unmappedOperationNames: Set<String>
        get() = rows.asSequence()
            .filter { it.operation == StatementOperation.OTHER }
            .map { it.operationRaw.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
}

data class StatementRow(
    /** Posting date, i.e. the statement's own Date column. */
    val postedDate: LocalDate,
    val operation: StatementOperation,
    /** Raw bank-specific operation name; kept as a stable diagnostic key. */
    val operationRaw: String,
    /** Signed minor units: debit < 0, credit > 0. */
    val amountMinor: Long,
    val balanceAfterMinor: Long?,
    val description: String,
    val beneficiaryName: String?,
    val beneficiaryAccount: String?,
    /** Raw merchant name when the bank prints one. */
    val merchantRaw: String? = null,
    /** Real purchase date when it differs from [postedDate]. */
    val purchaseDate: LocalDate? = null,
)
