# Settings navigation — 0.3.39

The root has five rows: Connections, Bookkeeping, Application, Data and security, About WHFIN.
The existing searchable leaf catalogue remains the source of global results. Search matches labels,
state and synonyms across groups. A bank result opens its provider page; explicit sign-in and
journal results open those destinations directly. Back restores the query. Fresh entry into Settings
resets the inner path, while returning from an external settings destination preserves its parent.
Search starts hidden behind the top-bar icon. Tapping or deliberately pulling down at the root
reveals the field, focuses it, and opens the keyboard. The field belongs to
one scroll container; nested pages always expose the top-bar search action. Root rows carry icons.

## Bank pages

Connections lists configured providers once, with original bundled artwork and current status.
Add bank lists the remaining supported providers. Shared Android permissions and statement import
stay in Connections. A common bank page contains history status and an explicit update action,
available transaction channels, accounts grouped by IBAN with currency links, saved sign-in and
secondary diagnostics. A manually created account alone does not imply authenticated API access.
No unsupported bank or notification channel is advertised. Demo cannot operate personal connections.

TBC push combines opt-in, listener/permission status and a short retention explanation in one group.
Long explanations expand on demand. Unrecognized examples or capture errors offer a journal link;
raw payloads, export and reprocessing live in Diagnostics. Existing encrypted retention and parser
contracts remain unchanged. Merely viewing settings reads credential presence, never plaintext secrets.

SMS monitoring is bank-specific, with shared Android permission. The legacy master-only preference
continues to enable existing Credo/TBC channels; newly added providers default off. Updating one bank
snapshots the others' effective settings atomically. Incoming delivery and foreground catch-up honor
these flags; OTP handoff remains independent. Entering Credo sign-in or syncing history does not
turn SMS monitoring on. Bank-scoped message pages filter records, card routing and history scan.

## Verification

Full app unit/Compose suite: 1031 tests, zero failures/errors, four skipped. Focused settings,
preference migration and bank filtering checks plus release R8/lintVital passed after final changes.
Native tests on disposable Pixel_9_Pro covered EN/light and RU/dark/system font 1.5: five root groups,
bank navigation, sync callback, correct currency account callback, global search and query restoration.
Both existing search/IME journeys and diagnostics-only UI passed. Test selection initially matched
search input instead of its identically titled result; stable row tags fixed the test ambiguity.
Final hierarchy retest: two passed. Synthetic callbacks only; no live bank APIs or owner data used.

Screenshots were inspected at `/tmp/whfin-settings-hierarchy-final` (root, bank list, both banks,
search); logs: `/tmp/whfin-settings-final-check.log`, `/tmp/whfin-settings-final-polish.log`,
`/tmp/whfin-settings-hierarchy-retest.log`. Real notification delivery on the owner's phone remains
an owner-driven check, separate from this navigation change.
