# Bank connections and saved TBC sign-in

Updated in 0.3.31. The owner reported seeing the password form again after choosing to save the TBC
session, and asked for clearer connection UI and real bank artwork. The exact reason on the owner's
phone was not observed: WHFIN was not foreground during the read-only check. No password, OTP,
session payload or financial data was read from that device.

## Reproduced and fixed

1. The login screen rendered username/password even while a saved session was present. The saved
   state now shows a continuation action; opening the password form is explicit and reversible.
2. Verified authentication was saved only after the account request succeeded. A failed account read
   therefore left a signed-in in-memory session but no saved session after closing the screen. The
   checkpoint now precedes account/history reads and is refreshed after responses can rotate cookies.
3. The visible sync button did not retry the account list after an account-read failure. It now resumes
   that read with the verified session, without another password/OTP exchange.
4. Expiry erased the explanation and reset the remember switch on a later visit. A device-local
   non-secret preference now retains the opt-in and the stable expiry/storage reason. Forgetting clears
   both; removing App Lock still disables storage and deletes saved session data.

The user-facing setting is **Remember sign-in / Запомнить вход**. The label and thumb form a single
switch target with one change and one haptic. Existing saved ciphertext implies legacy opt-in on
upgrade. Session encryption/format/key alias remain unchanged. The small preference is outside the
Android backup allowlist; the portable ledger backup cannot include it. This 0.3.31 stage saved only sessions. Since 0.3.32, the [common bank sign-in rule](bank-authentication.md) also saves opted-in credentials.

This does not extend the bank's cookie lifetime or register a trusted device. If TBC refuses the saved
session, the app must ask for a new sign-in. The UI makes that state explicit and does not promise a
permanent login. No background authentication or new authentication endpoint was introduced.

## Artwork

Bank marks are bundled locally and never fetched at runtime. They identify the bank in sign-in,
bank selection and the connection offer. WHFIN action colors and components remain its own.

- TBC: original SVG path geometry and published blue from the bank's
  [official design documentation](https://developers.tbcbank.ge/docs/design). The supplied white mark
  is represented as a VectorDrawable; its blue background is a UI container.
- Credo: the original transparent PNG linked as “Credo Logo” by the bank's
  [official site](https://credobank.ge/en), fetched from its
  [image CDN](https://imagedelivery.net/d_EE26O5eWcJDRYn-qMBOg/a960a05f-4e1e-443c-b235-92a148082c00/public).
  It is used without changing its geometry or colors. Artwork belongs to the respective banks.

## Verification

The reproduction command was `:app:testDebugUnitTest --tests '*TbcSavedLoginScreenTest' --tests
'*TbcLoginViewModelTest'`: saved-session password fields and checkpoint-after-account-read tests failed
before the fix. Follow-up regressions reproduced the lost expiry explanation and the actual retry
button path. All 13 focused tests passed after the fixes; the full host unit/Compose suite passed
before the later focused changes. No diagnostic logs were introduced.

Disposable-emulator checks cover real Android Keystore reconstruction, tampering and deletion;
RU/EN, light/dark, system font 1.5 for sheets; saved sign-in without password fields; the native OTP and
IME journey; initial balance entry; and bank selection/manual refusal. Actual bank-controlled session
expiry/reuse still needs owner-driven testing. Production bank screens keep FLAG_SECURE; screenshots
come from synthetic, non-exported QA hosts with no gateway or account database access.

Final validation: 16 emulator UI/Keystore cases passed, followed by eight final cases including the
1280×1500 compact display at system font 1.5. The sign-in action was visually checked above the real
IME. Saved and expired states, RU/EN, light/dark, and the original bank marks were inspected. Release
R8/lintVital and public-tree checks passed. The phone disconnected before installation, so 0.3.31 is
built but not installed there. Screenshots: `/tmp/whfin-tbc-session-final` and
`/tmp/whfin-bank-session-final`.
