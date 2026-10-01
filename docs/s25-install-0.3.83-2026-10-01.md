# S25 update to 0.3.83 — 2026-10-01

At the owner's request, WHFIN was updated in place from 0.3.82 (94) to
0.3.83 (95) on Galaxy S25 (SM-S931B) using address-scoped `adb install -r`.

This release includes both working-sheet polish passes from commits 3fbccfa,
3317f2a and d67124a: allocation/debt forms, statement review, category drafts
and search, compact filters, and receipt actions. See
[the implementation and QA report](working-sheets-polish-2026-10-01.md).

- The version was incremented before installation, following release-signing.md.
- Offline `:app:assembleRelease` passed, including R8 and lintVital. The existing
  non-fatal Glance Compose stack-trace mapping warning remains.
- The implementation was previously verified with 1310 host tests (0 failures,
  0 errors, 5 skipped), core-ui screenshot baselines, and 11 UI + 5 Room tests on
  a disposable emulator. These tests were not run on the phone.
- No active BankSyncService was present immediately before installation.
  The phone's focused app was its launcher; WHFIN was not foreground.
- The installed release and new APK both have certificate SHA-256
  `af600982905fc962f57c0507f7042c5dbfe23ada4ca677c55495ccd6752fae92`.
- The pre-update base.apk SHA-256 was
  `af829d286201a9ff4956588ac79f6d685ea72296bd3d35d8e307ced43117c614`.
- After installation, the phone's base.apk matches the built APK byte-for-byte:
  SHA-256 `531e9c64c059b9e86cd68cb6e8833ad18d09f54a291a404726f7e564b6700df0`.
- Package Manager reports versionName 0.3.83 and versionCode 95.
  lastUpdateTime is 2026-10-01 11:53:29; firstInstallTime remains
  2026-08-16 19:13:46.
- READ_SMS, RECEIVE_SMS and POST_NOTIFICATIONS remain granted; their flags
  are identical before and after the update.
- No uninstall, data clear, instrumentation, or manual app launch was performed.
  No personal database, SMS, backup content, or bank session was read.

The schema remains Room v8. Runtime visual QA and live bank synchronization on
the S25 were not performed during installation.
