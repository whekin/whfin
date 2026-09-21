# Confirm pay instead of inferring it

A declared income source counted every positive row that landed on its receiving account. In a ledger
a refund, a friend settling up and the salary are the same shape, so the largest credit of the month
closed the declaration silently and moved Home's payday a month forward. Telling somebody they have
been paid when they have not is the failure this feature exists to prevent, so the app no longer
guesses.

Evidence comes from the owner, once, and is stored as a **Confirmed payment** in
`income_source_payments`. The counterparty that confirmation names — a merchant row for a bank credit,
the exact sending address for a wallet — carries later payments on its own. The amount is deliberately
not identity: it changes every month, and matching on it was the old guess. Everything else on the
account is offered back as a visible question, so every counted sum traces to an answer somebody gave.

Withdrawing the confirmation is deleting the row; payments recognised only through the learned sender
go with it, rather than being contradicted by a second answer. Nothing about the transaction changes
either way.

Editing or ending an income declaration updates it in place. SQLite REPLACE is forbidden here:
it deletes the parent and cascades the owner's confirmations. Starting a new receiving-account era
retains all confirmations on the old era. Regression: IncomeSourceRepositoryTest.
