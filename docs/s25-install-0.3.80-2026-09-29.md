# S25 update to 0.3.80 — 2026-09-29

At the owner's request, WHFIN was updated in place from 0.3.79 (91) to 0.3.80 (92)
on Galaxy S25 (SM-S931B) using address-scoped `adb install -r`.

This release includes theme-aware onboarding illustrations, interactive savings charts,
everyday feedback, empty-state artwork and the About cat from commits 348374d, af650ac and dabb5d3.

- Release `:app:assembleRelease` passed, including R8/lintVital. The known non-fatal Glance
  Compose stack-trace mapping warning remains.
- No active BankSyncService was present immediately before installation.
- Old and new signing certificate SHA-256: `af600982905fc962f57c0507f7042c5dbfe23ada4ca677c55495ccd6752fae92`.
- The installed base.apk matches the built APK byte-for-byte: SHA-256
  `46d97353c64334c363f540a52a0f488a8880566382c11254b9d366f9c16d58f1`.
- First installation remains 2026-08-16 19:13:46.
- READ_SMS, RECEIVE_SMS and POST_NOTIFICATIONS remain granted before/after.
- No uninstall or data clear was performed. The app was not opened after installation;
  no personal database, SMS, backup or bank session was read.

The raw dark QA-host status-bar limitation documented in everyday-delight-2026-09-29.md
was not investigated on the phone during this installation.
