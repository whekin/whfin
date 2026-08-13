package dev.whekin.whfin.data.db

enum class AccountType { BANK, CASH, SAVINGS, CRYPTO, PERSON }

/** Whether this ledger contributes to money available for ordinary spending. */
enum class FundRole { AVAILABLE, RESERVE }

/** The bank's product contract, independent from how the owner intends to use its money. */
enum class BankProduct { CURRENT_ACCOUNT, DEMAND_DEPOSIT, TERM_DEPOSIT }

/** Legacy combined purpose/product field. New behavior uses [FundRole] and [BankProduct]. */
enum class SavingsMode { FLEXIBLE_RESERVE, GOAL, TERM_DEPOSIT }

enum class FinancialGroupType { BANK, WALLET }

enum class PaymentInstrumentType { PHYSICAL_CARD, VIRTUAL_CARD }

enum class StatementSourceType { ACCOUNT, CARD }

/** How a statement reached WHFIN; kept separately from the bank account/card it describes. */
enum class StatementImportOrigin { FILE, CREDO_SYNC }

enum class TransferGroupType { TRANSFER, CONVERSION, CARD_TOPUP, SAVINGS, CRYPTO_SWAP, CRYPTO_BRIDGE }

enum class CategoryKind { EXPENSE, INCOME }

/**
 * PENDING = a legacy/explicit review draft; CONFIRMED = an active bank-evidenced operation;
 * MANUAL = manually maintained. TxSource retains SMS/statement provenance independently.
 */
enum class TxStatus { PENDING, CONFIRMED, MANUAL }

enum class TxSource { SMS, STATEMENT, MANUAL, ADJUSTMENT, CRYPTO }

enum class PersonRole { PARTNER, FAMILY, FRIEND, COLLEAGUE, OTHER }

/** Как учитывать долю операции. LOAN/REPAYMENT не являются расходом/доходом. */
enum class AllocationPurpose { PERSONAL, SHARED, GIFT, LOAN, REPAYMENT }

enum class DebtDirection { THEY_OWE_ME, I_OWE_THEM }
enum class DebtStatus { OPEN, CLOSED }
enum class DebtEventKind { OPENED, SETTLEMENT, ADJUSTMENT, CLOSED }

enum class ReconciliationIssueState { OPEN, KEPT }

enum class SmsDiagnosticKind {
    CARD_PAYMENT,
    OUTGOING_TRANSFER,
    INCOMING_TRANSFER,
    DEPOSIT_TOP_UP,
    OWN_TRANSFER,
    CURRENCY_EXCHANGE,
    BILL_PAYMENT,
    CASH_DEPOSIT,
    INTEREST,
    IGNORED,
    UNRECOGNIZED,
}

enum class SmsDiagnosticOutcome {
    IMPORTED,
    /** The bank reversed a card payment; the draft it had created was withdrawn. */
    CANCELED,
    ATTACHED,
    DUPLICATE,
    IGNORED,
    UNRECOGNIZED,
    NEEDS_CARD_MAPPING,
    CHOOSE_ACCOUNT,
    ERROR,
}

enum class SmsDiagnosticReason {
    OTP,
    REJECTED,
    UNRELATED,
    PARSE_FAILURE,
    /** A cancellation did not identify exactly one prior card payment, so no money was changed. */
    CANCELLATION_TARGET_NOT_FOUND,
    NO_ACCOUNT,
    MULTIPLE_ACCOUNTS,
    NO_CARD_MAPPING,
    /**
     * The statement already covers this account on this day and holds no matching row. The message
     * is kept as evidence rather than written: inside a covered period the statement is the truth of
     * that account, and a second row for one operation is worse than a visible question.
     */
    STATEMENT_COVERS_PERIOD,
    STORAGE_ERROR,
}
