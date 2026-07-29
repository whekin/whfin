# Keep unrouted SMS outside the ledger

Supported-bank SMS may be parsed before WHFIN knows which ledger owns the operation. We represent that
state as an **Unrouted operation**, not as a `Transaction` with a nullable, placeholder, or guessed
account. The Feed may project it as a muted financial row, but balances, day/month totals, categories,
and statistics ignore it. Routing either creates the real pending transaction or attaches the SMS
evidence to a transaction already confirmed by a statement. This keeps the account-bound ledger model
truthful while making incomplete SMS work visible instead of dropping it or hiding it in diagnostics.
