# Safe replacement of the ledger

Restoring a backup replaces account IDs and all relationships, not merely the visible lists. A bank or
chain response prepared against the old ledger must not write into that replacement.

`LedgerRestoreState` reserves money-form submissions and long-running bank work before dispatch and covers wallet discovery,
balance/history refresh and statement import. These operations may overlap each other, but a restore
is rejected while any reservation is active. New reads and a second restore are rejected while the
replacement is active. Reservations release on completion, failure and cancellation; repeated close
is harmless. The local and cloud restore screens explain that the current operation must finish first.

After parsing and validating the input, the safety export and replacement share one Room transaction.
An incoming message therefore cannot write in the gap between the recoverable copy and replacement.
If the safety file cannot be written, the ledger remains unchanged. Foreign-key failure rolls back the
replacement. The two most recent private safety copies remain available for undo.

A successful commit notifies the database's observers. MainActivity discards the old Activity and its
ViewModels, and clears retained bank results, challenges and initial-balance reads. Encrypted bank
credentials remain intact. A failed restore does not emit this notification. Demo and personal databases
have separate notifications; installing demo data does not reset a personal screen.

Tests: LedgerRestoreCoordinationTest, BankSyncRuntimeTest, RestoreSafetyBackupInstrumentedTest,
WhfinBackupInstrumentedTest and RestoreUiJourneyTest. Destructive scenarios run only on a disposable
emulator or an in-memory test database.


## Preview before replacement — 0.3.67

Selecting a local file or downloading a Drive copy first parses and validates it without writing to the ledger. Encrypted files
ask for their passphrase before the preview. The confirmation shows creation time, account and active
transaction counts, history dates, and the replacement/undo scope. Person ledgers and voided
transactions do not inflate those user-facing counts.

The prepared snapshot is retained only in memory. Confirmation restores that exact snapshot, not a
second read of a mutable document URI; the passphrase is cleared after preparation. Dismissal drops
the prepared snapshot. Replacement still takes the exclusive restore reservation and transactional
safety copy. The restarted, unlocked Activity gives a one-shot success message; the existing local
safety copy remains the undo route in Backups. Drive uses the same prepared snapshot and preview; confirmation never downloads the file again.
