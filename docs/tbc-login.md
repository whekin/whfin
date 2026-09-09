# TBC in-app sign-in checkpoint

Added 2026-09-09. This is a testable mobile sign-in and account-discovery surface, not automatic
statement synchronization. Settings → TBC connection opens the native form. The Personal workspace
alone can connect; Demo disables the entry and the ViewModel also rejects network work in Demo.

## Scope

The Android gateway independently implements the protocol described by the public TBC Georgia
ZenPlugins adapter. Sources and channel differences are in [connector research](tbc-connector-research.md).
It calls password login, OTP certification (SMS or TBC Pass), current-user verification, and the
mobile account dashboard. It does not register or trust a device, change credentials, issue payments,
or assume a mobile cookie works on the web API. Normal system TLS verification remains enabled.

A successful session reads the account list, but creates no ledger accounts or transactions. XLSX
import remains the financial source. Mobile `movementId`/`transactionId` compatibility with XLSX,
mobile export support, and account-history balance evidence need a real authenticated comparison
before enabling automatic history writes. The dashboard list is not claimed to enumerate every
banking product or every currency ledger.

## State and recovery

Login → working → one-time code → verified session and account list. A direct login without a
challenge is also handled. Unsupported challenge types, password-change requirements and profile
selection stop with an explicit message directing the owner to the bank app. Failed codes can be
retried manually; restart starts a new password login. Rate limits/protection failures are never
retried automatically. Only one request sequence runs at a time, and leaving the screen cancels it.

After certification, an account-list failure retains a verified session and exposes a retry of that
read. Session expiry clears local saved state and returns to the password form. No exception contains
a raw bank response. The login, OTP and account APIs are only exercised against scripted responses in
host tests; the first successful real-bank login remains an owner-driven test on the phone.

## Session handling

Password and OTP stay in composition/process memory, never SavedStateHandle, saved-instance state,
preferences, logs or backup. The screen sets FLAG_SECURE through the Activity's privacy owner, so it
also remains protected across background/resume and WHFIN locking.

Saving a session is optional and off by default. It requires a configured WHFIN code. The saved
payload contains cookies and the generated device ID, not the bank password. BankSessionStore encrypts
it using AES-256-GCM with a non-exportable Android Keystore key and bank-specific authenticated data.
Atomic ciphertext storage lives in `noBackupFilesDir`; neither Android backup nor the portable
ledger backup includes it. A later visit does not automatically load it: an explicit use-saved-session
action passes the existing BankCredential sensitive-action gate. Cookie updates from account reads
are retained when saving. An expired or invalid ciphertext session is discarded; forgetting removes
both the file and its key. Forgetting locally is not represented as server-side session revocation.

The session lasts as long as the bank accepts its cookies. This checkpoint has no trusted-device
registration, passcode/easyLogin or silent password replay. Credo remains on its existing connector;
BOG is a later, separate protocol implementation.

## Verification

TbcGatewayTest covers OTP construction, cookie propagation/renewal, resume, unsupported challenges,
credential/profile requirements, status handling and transport host/TLS constraints.
TbcLoginViewModelTest covers consent, no automatic secret reads, bad-code retry, expiry, account-read
retry, cancellation and duplicate submissions. TbcSessionStoreInstrumentedTest exercises Android
Keystore round-trip, ciphertext tampering and deletion using a separate synthetic test namespace.
TbcLoginVisualTest uses a non-exported, debug-only host with no network or database access for
RU/EN, light/dark, font 1.5, error/code/connected states and an IME journey.
