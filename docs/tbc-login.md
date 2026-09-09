# TBC in-app sign-in checkpoint

Added 2026-09-09. Sign-in/session restoration starts foreground transaction synchronization, with
full initial history and an XLSX or owner-confirmed opening. See [history sync](tbc-history-sync.md).
Version 0.3.31 fixes authentication persistence and the saved-sign-in screen;
[connection design](bank-connection-design.md) records the changes and original artwork sources.
The sections below record the initial authentication checkpoint. Settings → TBC connection opens the native form. The Personal workspace
alone can connect; Demo disables the entry and the ViewModel also rejects network work in Demo.

## Scope

The Android gateway independently implements the protocol described by the public TBC Georgia
ZenPlugins adapter. Sources and channel differences are in [connector research](tbc-connector-research.md).
It calls password login, OTP certification (SMS or TBC Pass), current-user verification, and the
mobile account dashboard. It does not register or trust a device, change credentials, issue payments,
or assume a mobile cookie works on the web API. Normal system TLS verification remains enabled.

A successful session reads the account list and starts the history sync described separately.
The dashboard list is not claimed to enumerate every banking product or every currency ledger.

## State and recovery

Login → working → one-time code → verified session and account list. A direct login without a
challenge is also handled. Unsupported challenge types, password-change requirements and profile
selection stop with an explicit message directing the owner to the bank app. Failed codes can be
retried manually; restart starts a new password login. Rate limits/protection failures are never
retried automatically. Only one request sequence runs at a time, and leaving the screen cancels it.

After certification, saving is checkpointed before the account read. An account-list failure retains
that verified session and the sync button retries the read. Session expiry clears the cookie payload
and returns to the password form while retaining the owner's opt-in and an explanation for the next
visit. No exception contains
a raw bank response. The login, OTP and account APIs are only exercised against scripted responses in
host tests; the first successful real-bank login was subsequently reported by the owner on the phone,
including a visible account list (2026-09-09). Session reuse/expiry against the real bank is not yet verified.

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
