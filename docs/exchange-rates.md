# Exchange rates and the converted total

Status: implemented 2026-07-31. Room DB v6.

## Why one pivot

Every quote is stored as **GEL for one unit** of a currency or asset. Converting between any two
currencies is then a single multiply and divide, and switching the display currency cannot chain two
independently stale quotes into a number nobody can explain. GEL itself is implicit and never stored.

## Sources

| Source | Gives | Notes |
|---|---|---|
| National Bank of Georgia | GEL per USD, EUR, RUB and the rest of the published list | Authoritative for the lari, one publication per banking day |
| CoinGecko | USD price of ETH, TRX, USDT | Reaches GEL through the NBG USD quote |

Providers run in order, because the crypto price is meaningless until the USD pivot exists.

**The `quantity` trap.** NBG quotes some currencies per 100 or per 1000 units — the ruble is per 100.
Using the raw `rate` makes it a hundred times too valuable. `NbgFiatRateProvider` divides by
`quantity`, and a test pins RUB and AMD specifically.

**Privacy.** The full published rate list is requested rather than the currencies held, and the
crypto asset list is a build constant rather than the wallet contents. Neither request can reveal
what this ledger contains, and neither carries an address.

## Rules the UI must keep

- A converted total is a **reading**, never a record. Accounts keep their own currency; nothing is
  rewritten when the display currency changes.
- A missing quote is never zero. `ConvertedTotal.missing` names the currencies left out, and the
  headline label says so instead of quietly shrinking.
- Freshness is only reported when it matters: a same-day quote is normal and silent, an older one is
  labelled. The label carries the **oldest** quote the total depends on, including the display
  currency's own.
- Past transactions are never re-priced. An FX purchase already carries the actual GEL the bank
  charged, which is more accurate than any later rate.
- Volatile assets stay out of income and expense analysis. A price swing is not a month's spending.
- Refresh is foreground: quotes older than `RatesRepository.STALE_AFTER_MILLIS` are re-read when the
  Accounts screen opens, and the wallet refresh action updates prices and balances together.

## Storage

`exchange_rates` holds one row per code (`code`, `gelPerUnit`, `observedAt`, `validOn`, `source`). A
refresh replaces the row: quotes are current values, not a price history. The table is excluded from
the portable JSON export because one refresh reproduces it exactly.

## Tests

- `MoneyConverterTest` — mixed wallets, display rotation, missing quotes, sign, freshness selection.
- `RateProvidersTest` — real response shapes from both endpoints, the `quantity` normalization, the
  constant asset list, and the refusal to price crypto without a USD pivot.
- `RatesRepositoryInstrumentedTest` — provider ordering, replace-not-append, and a failing source
  leaving the last good quote intact.
- `WhfinDatabaseMigrationTest.migrate5To6_addsAnEmptyQuoteSnapshot`.
