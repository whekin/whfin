# Private MyCredo connector

Status: experimental personal dogfood, foreground-only. This is not an official bank API
integration and must not be presented as reliable unattended synchronization.

## Product flow

`Settings → Credo sync` signs in to MyCredo, confirms the explicit 4-digit OTP when required, discovers
each account/currency ledger, and lets the user import the previous 12 months in one batch. Each XLSX is
fed unchanged to the existing `StatementImporter`; statement history, external-key deduplication,
pending reconciliation and balance verification therefore remain the source-of-truth path.

The batch continues when one ledger fails and shows a result per ledger. It never creates a payment or
retries authentication silently. The in-memory session ends with the process; reconnecting can require
a new OTP.

## Security boundary

- Credentials and OTP go directly from the Android device to Credo's HTTPS MyCredo service. WHFIN has
  no proxy or telemetry server. The production transport rejects cleartext URLs, unexpected hosts and
  redirects; the application manifest also disables cleartext traffic globally.
- The adapter exposes login, OTP challenge, account discovery and statement export only. No payment
  mutation exists in the connector. This is a limit of WHFIN's implementation, not a bank-issued
  read-only scope on the user's MyCredo credential.
- OTP, access token and refresh token are memory-only and are not logged, exported or backed up.
- Remembering the login is opt-in and disabled until WHFIN App Lock is actively enabled. Username and
  password share one authenticated AES-256-GCM payload under a non-exportable Android Keystore key;
  the username is not duplicated as plaintext preferences. Before production there is deliberately no
  credential migration path: an incompatible development ciphertext is deleted and must be re-entered.
  App Lock is the product access gate; its PIN is not an encryption key and is not derivation material
  for the bank credential. Opening the connector without active App Lock deletes saved credentials.
- `whfin_credo_secrets` and `whfin_credo_device` preferences are outside the strict Android backup
  allowlist. Portable JSON backup uses a database-table allowlist and cannot include either file.
- “Forget MyCredo login” clears the stored ciphertext and deletes its Keystore key. It does not revoke
  an active bank session server-side because the current public protocol exposes no
  verified logout contract.

## Failure policy

The public web protocol can change without notice. Unknown responses become stable local error codes;
raw responses and credentials are never shown or logged. The UI shows the safe code for an otherwise
unknown response; HTTP 403/429 is called out as website protection and tells the user not to retry in a
loop. Authentication failures are not automatically retried. A failed statement does not roll back
successful imports from other ledgers, and the existing transaction deduplication makes a deliberate
later retry safe.

When the XLSX download itself succeeds but WHFIN rejects the workbook during parsing or balance
validation, the affected result offers `Save original XLSX`. This copies the exact downloaded bytes
through Android's document picker so the owner can inspect or attach the statement for parser
diagnosis. A transport/API failure has no file and therefore never shows that action. The downloaded
copy exists only in process memory and is discarded when a new sync/history scan starts, MyCredo is
disconnected, or the process dies; it is never added to Room, logs or backup. The suggested filename
contains only currency and the last four account digits. The explicitly saved document is an
unencrypted bank statement and must be handled accordingly.

Hardening (2026-07-16):

- Transient failures (`NETWORK_ERROR`, HTTP 5xx) during a statement download are retried up to two
  times per ledger with a short backoff. HTTP 403/429 is deliberately excluded from retries so WHFIN
  does not behave like a bot against website protection.
- An expired authorization (HTTP 401 / `UNAUTHORIZED` / GraphQL auth codes) stops the whole batch
  instead of failing every remaining ledger with the same error. The in-memory session is dropped, the
  partial per-ledger results stay visible, and the UI returns to the sign-in state with
  `SESSION_EXPIRED`. Silent re-login is impossible by design: a fresh login requires an explicit OTP,
  and WHFIN never triggers an OTP SMS without the user.
- These paths are covered by Robolectric tests with a scripted gateway (retry exhaustion, permanent
  errors, 401 mid-batch, partial success keeping the connected state).

Credential hardening (2026-08-04):

- The Keystore key size is explicit rather than provider-dependent: AES-256-GCM with a 128-bit tag,
  randomized IV and purpose/version AAD. An Android instrumentation test reads `KeyInfo.keySize` from
  the generated key and verifies round-trip, deletion and corrupted-ciphertext failure on the platform
  Keystore. The unused legacy migration was removed before production; only one credential format exists.
- The password field uses composition-only state and is cleared after the login challenge advances; it
  is never placed in Compose saved-instance state. The route keeps that memory-only draft in its
  ViewModel so opening App Lock and returning does not erase an already entered username/password;
  process death still clears it. Remembering is no longer preselected for new logins.
- The four-digit OTP surface is fixed-height rather than part of the connector's scrolling content:
  its dots, numeric keypad, Confirm and Resend actions never require a scroll. OTP still exists only in
  the current composition and is cleared when the challenge ends or is resent. Opening Credo setup also
  enables transaction SMS monitoring and requests the shared `RECEIVE_SMS` permission. A new exact
  MyCredo login-code broadcast can fill the dots through a non-replaying process-only handoff; Inbox is
  not queried, payment OTP templates do not match, and Confirm remains explicit. Because Samsung One UI
  may omit a sideloaded manifest receiver from an otherwise delivered `SMS_RECEIVED` broadcast, the live
  `Connecting` / `AwaitingOtp` route also owns a context-registered receiver protected by
  `BROADCAST_SMS`; it is closed as soon as the login leaves those stages.
- App Lock remembers Credo as its caller. Completing PIN setup or backing out returns directly to the
  in-progress Credo form in both Personal setup and Settings instead of falling through to Settings.
- The sign-in screen explains the direct connection, local encryption, non-persistence of OTP/session
  tokens, payment-free adapter surface and the important distinction between that surface and the
  privileges of the bank login itself.

The first OnePlus dogfood attempt on 2026-07-14 failed during `Auth/Initiate`, before OTP. The request
fingerprint was then aligned with the current public web bundle (`ENGLISH`, `mobile`, `Android`, and the
CSS-pixel screen size). The statement range also matches the observed web request exactly: current
instant minus 12 calendar months through the same current instant, without day-boundary expansion. A
second owner-driven attempt is required to determine whether the old request was rejected by validation
or whether Cloudflare blocks a native direct client entirely.

That attempt exposed the safe code `null`: successful REST envelopes include `"errorCode": null`, and
`JSONObject.optString` had converted JSON null into the literal string `"null"`. The adapter now checks
JSON null before interpreting a non-empty string as an error; the real envelope shape is a regression
fixture. Login still requires another owner-driven attempt before it is considered proven.

If real-device dogfood shows a changed request contract, capture only sanitized request/response shapes
from the user's own browser session: remove credentials, OTP, cookies, authorization headers, account
numbers, names, balances and transaction data before adding fixtures. Never commit a HAR file.

## Exit criteria for a later phase

Before adding background refresh, prove real login/OTP, all-ledger export and duplicate re-import on the
owner's device. Then decide separately whether persisting a refresh token is justified. Official bank
API access is outside the current roadmap; if reconsidered after a public launch, it must use a separate
implementation, consent model and security review.
