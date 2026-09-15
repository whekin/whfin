# User-started bank sync beyond the screen

Credo and TBC keep their existing authentication and import contracts. An explicit owner action starts
the work; scheduled authentication, unattended OTP requests and automatic login after process death
are not introduced.

BankSyncRuntime owns the bank controllers in a process-level ViewModelStore and runs their jobs in
an application-lifetime supervisor scope. Routes observe those same controllers. In particular,
disposing the TBC route no longer cancels authenticated history work. Controllers use the Personal
workspace database even if another UI workspace is later displayed.

Each running coroutine holds a per-bank lease. Nested recent-history/full-history jobs share one run
and keep the foreground service alive without a gap. Starting another bank shares that service.
Cancellation before dispatch, failed service startup and cancellation during execution all release
the lease. Credo reserves its Syncing state before dispatching IO to prevent double taps or onboarding
collectors from starting a second pass.

## Home and progress

Once the controller begins authenticated history work, a one-shot handoff returns its bank page to
Home. This event survives fast completion and Activity absence; opening progress later does not bounce
back to Home again. Invalid OTP remains on the confirmation screen. TBC's separate initial-balance
editor does not trigger a new history handoff.

The Home sync action rotates while a run is active and has an accessible running label. Its sheet
shows each bank's current phase and account counter, or completion, attention, confirmation or
interruption. Tapping opens the existing bank page with the current result without starting a second
sync. The bank page still provides an explicit retry/sync action. API detail retrieval is labelled
“Getting transactions”, rather than incorrectly calling the whole phase XLSX reading.

## Android lifetime and privacy

The non-exported BankSyncService uses the `dataSync` foreground-service type and a low-importance,
ongoing notification. A bounded partial wake lock allows the current work to finish with the screen
off. The service stops and releases it when all leases finish. Android's service timeout and a
30-minute local watchdog cancel outstanding jobs and stop the service. Already committed account
imports remain; the interrupted account retains its existing transactional guarantees.

The service uses START_NOT_STICKY. Its Intent and notification contain no credentials, OTP, session,
balances, account identifiers or bank payloads. The only durable runtime marker is the set of bank
names whose jobs were in flight. A new process presents those as interrupted and does not resume
authentication. Completion details stay in the process-owned controllers; existing bank failure
retention and last-sync timestamps keep their previous behavior.

Minimizing and destroying the Activity are supported. Force-stop, reboot or process death are reported
as interruption on the next launch; they cannot be promised as uninterrupted execution. The owner
retries explicitly and the existing idempotent import rules reconcile already completed work.

Android references: [service launch](https://developer.android.com/develop/background-work/services/fgs/launch),
[service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

## Verification

Unit tests cover nested leases, the one-shot handoff after completion, cancellation before dispatch,
service startup rejection, Android timeout and process-restart markers without launching bank work.
Native emulator tests use a synthetic file gate: work is released only after Home is pressed and the
Activity is destroyed, then must finish under an OS-recognized foreground service. No real bank is
contacted. EN/light, RU/dark/system font 1.5 and compact-height renders exercise the status sheet and
the selected-bank callback. Native tests refuse physical hardware.
