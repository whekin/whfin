# Private MyCredo connector

Status: experimental personal dogfood, foreground-only. This is not an official Credo Open Banking
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
  no proxy or telemetry server.
- The adapter exposes login, OTP challenge, account discovery and statement export only. No payment
  mutation exists in the connector.
- OTP, access token and refresh token are memory-only and are not logged, exported or backed up.
- Remembering the password is opt-in and disabled until WHFIN App Lock has a code. The password is
  encrypted with AES-GCM using a non-exportable Android Keystore key. App Lock is the product access
  gate; its PIN is not an encryption key and is not derivation material for the bank credential.
- `whfin_credo_secrets` and `whfin_credo_device` preferences are outside the strict Android backup
  allowlist. Portable JSON backup uses a database-table allowlist and cannot include either file.
- “Forget MyCredo login” clears the stored ciphertext and username. It does not revoke an active bank
  session server-side because the current public protocol exposes no verified logout contract.

## Failure policy

The public web protocol can change without notice. Unknown responses become stable local error codes;
raw responses and credentials are never shown or logged. Authentication failures are not automatically
retried. A failed statement does not roll back successful imports from other ledgers, and the existing
transaction deduplication makes a deliberate later retry safe.

If real-device dogfood shows a changed request contract, capture only sanitized request/response shapes
from the user's own browser session: remove credentials, OTP, cookies, authorization headers, account
numbers, names, balances and transaction data before adding fixtures. Never commit a HAR file.

## Exit criteria for a later phase

Before adding background refresh, prove real login/OTP, all-ledger export and duplicate re-import on the
owner's device. Then decide separately whether persisting a refresh token is justified. Official Credo
Open Banking remains the production direction and must keep a separate implementation and consent model.
