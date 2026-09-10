# TBC holds are provisional ledger evidence

An explicitly requested bank sync imports supported debit card holds as active spending immediately,
without requiring a push notification or a manual confirmation. `BANK_HOLD`/`PENDING` distinguishes
bank authorization from both booked statement truth and a user review task. This extends the active
evidence model of ADR-0002 without changing routed SMS behavior.

A durable `bank_holds` alias relates an account/card/time fingerprint to one transaction through push,
SMS, posting, duplicate merge and backup/restore. Mutable amount is not identity. Matching must be
unique; ambiguous settlement or changes to allocated money stop the account import. A bank posting
updates the same transaction and preserves the owner's category and explicit links.

Absence of a hold from a read is not proof of cancellation. It remains provisional until settlement
or an explicit owner correction. No background authentication, OTP storage or raw bank-response log
is introduced. Detailed limits and verification: ../tbc-pending.md.
