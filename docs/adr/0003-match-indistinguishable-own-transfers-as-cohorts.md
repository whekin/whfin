# Match indistinguishable own transfers as cohorts

A bank statement can contain two equal own transfers on the same day with the same source and
receiving IBAN. SMS can confirm the complete set without identifying which receipt belongs to which
individual bank row. Assigning receipts by order would invent identity and could later misrepresent
owner categories or links.

The device-local SMS journal therefore has a `MATCHED_GROUP` outcome. It requires equal multiplicity
of the full receipt cohort and complete bank-confirmed transfer pairs, with exact day, currency,
amount and both IBANs. Each bank source has one receiving member in the same permitted automatic
transfer group. An occupied source or owner-created `OWN_LINK` blocks this proof. No subset search,
new transaction, category mutation, transfer mutation or per-message transactionId is allowed.

Structured receipt fields already identify this cohort, so no new table, column or migration is
needed. Individual matching reserves its bank sources while the proof holds. Batch reconciliation
recomputes that proof from current bank rows. Changed bank multiplicity invalidates the entire result
and returns receipts to quiet reconciliation; a remaining row must never claim one arbitrarily.

This extends ADR 0001's handling of evidence outside the ledger: a receipt can be fully accounted for
by a set of existing bank rows, as well as by an individual row. It never becomes money without routing
and never writes a second copy of money already confirmed by the bank. The journal says
“Transfers matched as a group” and offers the originals, without an account choice or a guessed
single transaction to open. Portable backups continue to exclude the device-local SMS journal.
