# Everyday feedback and the Tbilisi cat — 2026-09-29

## Changes

1. Assigning a merchant category in transaction details or Smart categories can show a short
   acknowledgement. It starts only after the existing rule write succeeds, not at the picker tap.
   A check fades back to the category glyph; localized text explains that the next match will be
   automatic. The caller gives the text at least four seconds and respects Android's recommended
   accessibility timeout. No merchant means no remembered-rule promise in transaction details.
   Smart categories pins the acknowledgement above the queue so scrolling cannot hide it.
2. Home's existing sync indicator crossfades into a check only when every reported bank is inactive
   and COMPLETE. A missing result, confirmation, interrupted run or error cannot become a green
   success. Attention has its own glyph and accessible label. Clicking still opens the existing
   actual sync results, with retry/confirmation handled by existing routes.
3. The manual transfer form shows a one-shot downward dot between source/destination selectors once
   both distinct accounts are chosen. Changing/swapping the pair restarts that cue; amount edits do
   not. The existing swap control now has an EN/RU accessibility label. This illustrates direction,
   not bank submission, and does not move money itself.
4. Confirmed empty History, open Debts and no-Reserve Savings get distinct abstract vector scenes.
   Filtered/search empty results do not get the empty-history drawing. The shared state pane accepts
   artwork only for Empty/Unavailable; loading and error states remain undecorated. Short screens
   and large fonts use smaller artwork, while existing actions remain available.
5. A long press on the illustration in About calls a small cat. It peeks out, nudges a satellite
   shape and hides. Repeated triggers while playing are ignored. The scene has a localized
   accessibility long-click action and playing description, respects system motion/haptics, and
   stays entirely separate from the five Version taps that reveal developer mode.

All new fills/details use the active Material palette, including wallpaper colours and dark mode.
The cat, transfer cue and empty artwork finish; no idle animation was added. Sync retains its existing
rotation only during active bank work. No schema, money calculation, permission request or network
operation was added. Category acknowledgement does not change the categorization policy.

## Verification

- Full `:app:testDebugUnitTest`: 1275 tests, zero failures/errors, 5 skipped.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and all 43 existing
  `:core-ui:validateDebugScreenshotTest` references passed.
- `:app:assembleRelease`: R8/lintVital passed. AGP retains its known non-fatal Glance Compose
  stack-trace mapping warning.
- Three pixel/motion scenarios and both complete EN/RU visual routes passed (5 instrumented cases).

The emulator-only debug harness uses synthetic data and production About, transfer, debt, savings,
transaction detail and sync components. Empty History uses the same stateless pane as the real list.
It opens no real banks and persists no fixture transactions.

Pixel tests exercise cat reveal/rest, repeated long presses, duration scale zero and transfer
movement/restart for a changed pair. Host checks cover mixed sync outcomes and the distinction between
selecting a category and receiving a successful-persistence acknowledgement.

Visual configurations: EN/light/brand and RU/dark/Material You/font 1.5 at 1200×1920. Captures include
all new scenes, transfer swap, sync success/result/attention and the revealed cat. Artifacts are ignored
under `artifacts/delight-2026-09-29/delight-qa/`.

The first visual test expected bank-name text in the results sheet; the existing bank identity is a
logo, so the assertion was corrected to the real status title and result text. The synthetic dark harness now uses an explicit system-bar style and a window background from its
Material palette, rather than the device's independent night-mode default. The production screen inset
ownership was not changed.

No physical phone installation or personal-data access was performed.

## Remaining QA limitation

The extra dark About replay check passed, but visual inspection still shows a light launch-colour
strip behind the status icons in this raw debug Activity host, despite matching native night mode,
explicit bar styles and a theme-backed window background. Body layouts and interactions were inspected;
this harness does **not** certify end-to-end dark status-bar contrast. Production inset ownership was
not modified, and the physical phone was not tested. This is recorded rather than claiming the strip
was fixed. The revealed cat was separately inspected from a clock-controlled component capture.
