# Remembered bank sign-in

This is the common contract for every current and future bank connector offering a username/password
login. “Remember sign-in” means remembering both credentials, not merely caching a short-lived cookie.

## Storage and authorization

Use `BankCredentialStore` / `EncryptedBankCredentialStore` with a stable lowercase bank key.
Username and password share one AES-256-GCM payload under a bank-specific Android Keystore key and
AAD. No plaintext username duplicate, password, OTP or token belongs in UI preferences, saved-instance
state, logs or portable/Android backup. `BankCredentials.toString()` is redacted. The dedicated encrypted
preferences are outside the Android backup allowlist.

Saving is opt-in and requires the configured WHFIN code. Read saved credentials only through the
existing BankCredential sensitive-action gate. Save a newly entered credential only after successful
bank authentication, including OTP when required. Forgetting or disabling storage clears credentials
and cached sessions. OTPs remain process-only and never enter the credential payload.

Credo's wrapper now delegates to this implementation without changing its existing preference name,
key alias, IV/ciphertext keys, JSON payload or AAD. Existing Credo sign-ins require no migration or
re-entry. New banks must reuse the common implementation rather than adding another encryption store.

## Reauthentication

An explicit sync/continue action first tries the available session. If the bank rejects that session,
TBC uses the encrypted username/password for one new login attempt and presents the bank's OTP
challenge when necessary. Expiry must not erase valid saved credentials. Network/protection failures
must not trigger new-login loops. A bank-rejected saved password is forgotten and the owner is asked
to update it. There are no background OTP requests or trusted-device registrations.

Earlier TBC versions stored only cookies. They cannot supply a password they never saved: the owner
must enter the username/password once with Remember sign-in enabled. The legacy-session screen says
this explicitly. Afterwards session expiry no longer requires retyping credentials.

## TBC OTP

The native route registers the foreground receiver before login, opens a challenge-bound inbox and
starts Android SMS User Consent before requesting the bank code. An already-granted RECEIVE_SMS
permission provides the direct broadcast path; an already-granted READ_SMS permission provides a
bounded fresh-inbox fallback. Both are independent of financial SMS monitoring.

Unattended parsing requires a TBC sender and either the verified TBC SMS wrapper or explicit
login/authorization wording, with a numeric 4–8 digit code. The owner's phone supplied two wrapper
variants: `<#> TBC SMS code:` followed by an English/transliterated mobilebank warning and an app
hash. The labelled code is read independently of digits in that hash. Real codes/identifiers were
masked before inspection; fixtures use synthetic code and hash values. Payment/PIN messages, other senders, old messages and duplicates are rejected. SMSC
second precision is respected. When Android explicitly obtains consent for one message, a generic
single-code template can also be read, still excluding payment/PIN messages. SMS permission is not
requested solely for this path. Provider documentation:
[SMS User Consent](https://developers.google.com/identity/sms-retriever/user-consent/request).

The code fills the field; confirmation stays explicit. Listening ends at verified authentication,
cancellation, screen exit or timeout. It is not used for TBC Pass challenges. The owner's English and transliterated wrapper wording was verified read-only on the phone. Actual
SMS delivery through the updated app on Samsung still needs owner testing; the emulator exercises the
production route and receivers.

## Verification

Host tests cover TBC expiry → saved password → OTP, no-cookie process restart, network failure without
re-login, rejected-password termination, forgetting both stores, OTP sender/time/duplicate/payment
rejection, consented generic codes and explicit code submission. Android Keystore tests cover bank
isolation, reconstruction and independently written legacy Credo ciphertext.

`TbcOtpDeliveryTest` drives the production TBC route with a synthetic bank transport and no-op credential/session stores. The host
sends the verified wrapper with a synthetic code and hash from `TBCSMS` through `adb emu sms send`; the field fills and the
fake bank receives exactly that code only after the Confirm action. This exercises real emulator
SMS delivery, not a direct assignment to the input. No live bank credentials or OTP were used.

Run the SMS end-to-end check after installing the debug and androidTest APKs:
`python3 scripts/test-tbc-otp-emulator.py --serial emulator-5554`. It refuses physical phones, grants
RECEIVE_SMS only to the emulator debug package, waits for the code screen, then injects one synthetic
SMS. The test is skipped in ordinary connected suites unless the host-injection flag is supplied.

Final checks (0.3.32): 997 host tests passed (four skipped); later focused checks and the observed
English/transliterated SMS wrappers passed. Four Android Keystore/compatibility tests, two saved-login
visual cases and two Settings scroll/search journeys passed. The observed TBC wrapper with synthetic
code/hash also passed real emulator SMS delivery through the production route. Release R8/lintVital,
diff and public-tree checks passed. Version 0.3.32 (44) was installed on the owner's phone with
`install -r`; actual fresh Samsung bank login/OTP delivery after upgrade remains owner-driven.
