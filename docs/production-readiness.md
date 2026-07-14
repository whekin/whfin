# WHFIN production readiness

This checklist tracks the difference between a good prototype and a build safe to distribute.

## Product trust

- [x] Settings exposes Privacy & Data and About WHFIN.
- [x] Installed version name/code are read from package metadata rather than duplicated in UI strings.
- [x] Author, independent-project context and restrained "made with love" attribution are visible.
- [x] In-app copy accurately distinguishes local processing from Android's encrypted system backup.
- [x] A source privacy policy exists in `docs/privacy-policy.md`.
- [ ] Publish the privacy policy at a stable HTTPS URL and add a real developer support contact.
- [ ] Add complete generated third-party notices before external distribution; the current About copy
  is a summary, not a substitute for licence texts.

## Data safety and security

- [x] Android backup scope is explicit: database plus non-secret UI/widget DataStore only; cloud backup
  requires encryption capability and D2D transfer uses the same allowlist.
- [x] Remove destructive migration fallback; preserve data through explicit v1→v2 and v2→v3 migrations,
  and require a migration plus schema/data test for every future change from the current DB v3.
- [x] Add versioned JSON export/restore through Android SAF with an explicit sensitive-file warning,
  destructive restore confirmation and deterministic full-schema round-trip tests.
- [ ] Add an encrypted `.whfin-backup` export option on top of the portable JSON format.
- [x] Add WHFIN-code/strong-biometric App Lock, protected recent-apps snapshots and an explicit
  capture-only widget policy. Quick entry intentionally bypasses App Lock; it cannot read history/balances.
- [ ] Store future Open Banking tokens with Android Keystore and keep them outside backup scope.
- [ ] Complete the Google Play Data safety form and the restricted SMS permission declaration.

## Release engineering

- [x] Sanitize the public source tree and previews, replace private development history with one clean
  root commit, preserve a privacy-safe milestone narrative, and add an automated public-tree check.
- [x] Keep the original private Git history in a verified bundle outside the repository.
- [ ] Choose an open-source license if broader reuse is intended. Until then the public source remains
  visible under an explicit all-rights-reserved notice.
- [ ] Define debug/internal/release application IDs and environment configuration if a public build is made.
- [ ] Store release signing material outside the repository and document key recovery/rotation ownership.
- [ ] Increment `versionCode` monotonically and maintain human release notes for every distributed build.
- [x] Verify the current minified release build and Room/KSP behavior under R8 (`:app:assembleRelease`).
- [ ] Define CI retention for the release mapping file and other symbolication artifacts.
- [ ] Decide crash reporting deliberately. Do not add analytics/crash SDKs without consent, disclosure,
  data minimisation and a privacy-policy update.

## Store and device quality

- [ ] Finalize app name, short/long description, feature graphic, screenshots and public support details.
- [ ] Verify launcher icon and all four widget previews across Pixel and OnePlus/ColorOS launchers.
- [ ] Complete release QA in EN/RU, light/dark, font scale 1.5, gesture/3-button navigation, IME,
  offline mode, denied permissions, lockout/unavailable biometrics and backup restore.
- [ ] Run Play pre-launch report and address accessibility, stability and policy findings.

Every completed product milestone gets its own commit. Physical user devices remain production-like:
upgrade install and manual QA only; destructive and instrumented tests run on disposable emulators.
