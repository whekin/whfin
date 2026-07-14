package dev.whekin.whfin.data.db

enum class AccountType { BANK, CASH, SAVINGS, CRYPTO, PERSON }

/** FLEXIBLE_RESERVE = liquid money, separated from day-to-day available balance but part of net worth. */
enum class SavingsMode { FLEXIBLE_RESERVE, GOAL, TERM_DEPOSIT }

enum class FinancialGroupType { BANK, WALLET }

enum class PaymentInstrumentType { PHYSICAL_CARD, VIRTUAL_CARD }

enum class StatementSourceType { ACCOUNT, CARD }

enum class TransferGroupType { TRANSFER, CONVERSION, CARD_TOPUP, SAVINGS, CRYPTO_SWAP, CRYPTO_BRIDGE }

enum class CategoryKind { EXPENSE, INCOME }

/** PENDING = черновик из SMS, CONFIRMED = подтверждено выпиской/крипто-API, MANUAL = ручной ввод. */
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
    OWN_TRANSFER,
    CURRENCY_EXCHANGE,
    IGNORED,
    UNRECOGNIZED,
}

enum class SmsDiagnosticOutcome {
    IMPORTED,
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
    NO_ACCOUNT,
    MULTIPLE_ACCOUNTS,
    NO_CARD_MAPPING,
    STORAGE_ERROR,
}
