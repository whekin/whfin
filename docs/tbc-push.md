# TBC push notifications and local journal

## Entry point and consent

Settings → TBC push. Reading and logging are off by default. Enabling opens Android notification
access when needed. The listener is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`; the user must
grant access in Android. That access is broad, but the callback rejects other packages before reading
their notification fields. The exact consumer package allowlist is `com.icomvision.bsc.tbc`, matching
[TBC Bank on Google Play](https://play.google.com/store/apps/details?id=com.icomvision.bsc.tbc).
The registration and callbacks follow Android's
[NotificationListenerService contract](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

The service processes new posts and currently active notifications when Android connects the listener.
It does not read Android's deleted-notification history, contact a bank API, sign in, or request OTP.
It always uses `userDb`, even while the app displays Demo. The Demo settings entry is disabled.

## Parsing and reconciliation

The two owner-observed amount-first card-payment layouts are treated as expenses. Title/text/bigText/
subText/textLines are captured separately; the most complete consistent parsed representation is used.
Conflicting representations, group summaries, oversized/truncated messages and unknown formats do not
create expenses. Explicit refund/deposit/incoming/rejection wording is not passed through the expense
fallback. Populations of income and refund examples are still needed before adding push credit rules.

The existing TBC SMS parser supplies exact minor units, card tail, currency, merchant and transaction
local time. Printed available balance remains in the original text but is never used as a booked
balance anchor. Shared bank-message routing handles missing cards/accounts and subsequent statement/
API reconciliation. The persisted `SMS` source is the legacy bank-message evidence type; push channel
identity is retained as `sms|tbc|push|…`, not conflated with a received SMS body hash. No schema change
or new backup enum is required.

Notification updates use a ledger key from Android notification key plus the parsed operation identity.
Changing the displayed balance or delivery timestamp does not create another purchase. Exact repeated
journal records are updated; different raw notification versions are retained separately.

SMS/push matching is one-to-one across channels, scoped to TBC, card, amount, currency, minute and
normalized merchant. Matching also runs when a card is mapped after both messages arrived. Multiple
unconsumed candidates wait for review rather than create a guessed third purchase. Distinct posts
within one channel are not collapsed solely because their money/date match. Already linked evidence
is idempotent after statement reconciliation and does not resurrect a voided transaction.

## Journal and export

`PushJournal` uses AES-256-GCM with an Android Keystore key, AAD and AtomicFile in `noBackupFilesDir`.
It stores the original available text fields, package, Android notification key, delivery/capture times,
channel, category, summary/truncation flags and processing outcome/diagnostic link. It never serializes
PendingIntent, parcelled extras, images or opaque action payloads. Known authentication/code/password
messages are excluded before persistence; DTO `toString` values are redacted. There is no raw-text
Logcat output or automatic upload.

Retention is 30 days and at most 300 entries, pruned on receipt/opening; an 8 MiB encrypted-file bound
can prune earlier entries sooner. Capture limits text to 8192 characters and textLines to 30. A truncated
entry is explicitly marked and cannot be automatically imported. Original logs and opt-in preferences
are excluded from Android/device-transfer and portable backups. Derived financial records and structured
bank-message diagnostics follow the existing financial backup contract.

The page shows receipt time and readable outcomes. Opening an entry gives the exact JSON example,
Save example (Android file picker) and Process again. Export is explicit, unencrypted JSON containing
that entry's bank text. Nothing is shared with Codex or another service automatically. Only an opaque
entry id is held in saved UI state across the file picker; the encrypted example is reread for writing.
The main app's lock protects entry browsing and the production activity protects screen captures.
Clearing the journal requires confirmation and does not delete recorded transactions. Disabling capture
retains prior examples until expiry or explicit clearing.

## Verification

Host tests cover both amount-first templates, original fields, conflicting text, wrong package,
OTP exclusion, group summaries, truncated messages, explicit non-expense wording, stable update
identity, SMS-first/push-first delivery, delayed card mapping, ambiguity and separate same-channel
posts. Android tests cover real Keystore ciphertext/reconstruction, retention/capacity and Notification
extras extraction. UI QA uses only synthetic notifications.

After installing debug and androidTest APKs, run:

```sh
python3 scripts/test-tbc-push-emulator.py --serial emulator-5554
```

This builds a temporary, clearly labelled synthetic sender under the TBC package id, grants notification
posting on the emulator and exercises actual Android delivery into the production listener. It refuses
physical devices and refuses to replace any existing TBC package. There is no bank UI, network,
credential or payment code. The fixture is uninstalled and its temporary signing files removed after
the run. The instrumentation restores listener access and opt-in, and excludes this scenario from
ordinary suites without the explicit host flag. Actual TBC delivery on the owner's phone remains an
owner-enabled validation after installation and Android access approval.
