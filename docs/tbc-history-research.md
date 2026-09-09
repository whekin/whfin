# TBC mobile transaction history: implementation evidence

Checked 2026-09-09 against ZenPlugins commit
`c7af1ee865ae40482694f572e8205babcb8aaf15`. These are observations of a public
connector and its tests, not bank API guarantees. No authenticated endpoint was
called. Account/card/merchant examples are not copied into this note.

## Account selection

| Source on `rmbgw.tbconline.ge` | Selection and history ID in ZenPlugins |
| --- | --- |
| GET `/products/api/v1/cards` | Expand each product's `accounts` by currency. Use **`account.id`**, not `account.coreAccountId`; retain product IBAN. Products without cards are still imported as checking accounts. Card-backed products use their first card for display/suffix association. |
| GET `/dashboard/api/v1/cards-and-accounts` | Iterate `accountsAndDebitCards`, **skip `type === "Card"`**. Non-card entries use `id.toString()`, falling back to IBAN when ID is null. Use entry currency, IBAN and `amount` balance. |

Sources: [fetch functions](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[account converters](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/converters.ts),
[history selection in entry point](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/index.ts).

The non-card model type is `Saving`; it must not be omitted merely because cards
were successfully fetched. The model's `coreAccountId` name is misleading for
request selection: the implementation sends `account.id` inside the history
request's `coreAccountIds`. This is source behavior, not proof that substituting
the other ID works. Dashboard fallback to IBAN is likewise an observed client
choice, not a tested server contract. Cross-source account deduplication should
preserve currency and full IBAN.
Source: [models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).

## History and identity

POST `/pfm/api/v1/transactions/history` sends one selector
`{currency, iban, id, type: "200"}` in `coreAccountIds`, page size 100,
`pageType: "History"`, `isChildCardRequest: false`, and
`showBlockedTransactions: false`. Continuations are `lastSortColKey` and
`lastBlockedMovementDate`; the connector derives them from nonzero
`transactionId` and blocked movement date respectively. Rows are grouped under
an outer epoch-millisecond `date`. The existing repeated-date stop condition is
not evidence that all rows of a day fit in one page; WHFIN should detect cursor
progress rather than infer completeness from day equality.
Source: [fetchHistoryV2](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts).

Ordinary row identity is **`movementId`** in the converter. `transactionId` is a
separate numeric field used for cursors and transfer grouping. Public fixtures
include `c_<id>` and `d_<id>` legs sharing one transaction ID, but also a cash-out
fixture whose movement-ID numeric suffix **differs** from its transaction ID.
The fixture factory confirms that these are separate arguments. Thus stripping
`c_`/`d_` is not a proven identity conversion. Retain both original fields and
scope identities to the bank/account context.
Sources: [conversion grouping test](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/__tests__/convertersV2/transactions/currencyExchangeAdjust.test.ts),
[cash-out test](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/__tests__/convertersV2/transactions/cashTransfer.test.ts),
[fixture constructors](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/common-tests/classes.ts).

**No paired mobile-history / CSV-XLSX fixture was found.** Nothing in the
inspected code or public test tree proves whether the statement's Transaction ID
matches `transactionId`, `movementId`, or either numeric suffix. Cross-channel
merging based on that equality needs owner-authorized paired evidence. A
candidate ID match must not erase the opposite transfer leg or merge rows with
incompatible account, currency or signed amount.
Source scope: [TBC plugin tree](https://github.com/zenmoney/ZenPlugins/tree/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge).

## Transfer and conversion descriptions

Verified V2 test titles are `კონვერტაცია` (conversion) and
`Transfer between your accounts`, with `subTitle: "internal transfer"`.
The conversion test uses `categoryCode: "PAYMENTS"`,
`subCategoryCode: "PAYMENTS"` and subtype 5. The converter treats
`INCOME`, `PAYMENTS` and `BANK_INSURE_TAX` categories as transfer-like records,
retains title as comment and groups using transaction ID plus title. That broad
category set includes ordinary payments/fees and is **not sufficient evidence
of an own-account transfer**. No structured counterparty IBAN exists in the
history row model; these generic titles do not reconstruct one.
Sources: [FX row test](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/__tests__/convertersV2/transactions/currencyExchange.test.ts),
[own transfer fixture](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/__tests__/convertersV2/transactions/blockedTransaction.test.ts),
[models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).

Amounts are already signed; the converter does not invert them using `isDebit`.
Account balances are independent snapshots, not row balances. Historical row
models contain neither opening/closing balances nor per-row balance. Do not
manufacture statement balance anchors from current account snapshots.
Sources: [models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts),
[transaction converter](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/converters.ts).

## Mobile XLSX status

No mobile XLSX/export/download implementation or paired statement fixture was
found in the inspected current connector. The known XLSX routes belong to the
web `ribgw` client, whereas mobile history uses `rmbgw`. No speculative bank
request was made, and mobile-cookie interoperability with web export remains
unverified. See [connector research](tbc-connector-research.md) for verified web
routes and the separate authentication hosts.
Source: [current mobile fetch implementation](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts).
