# Visual refinement — 0.3.68 (80), 2026-09-22

The requested changes are implemented: no dock dividers/underline, a month summary with visual
hierarchy, named filtered-history headers, illustrated theme choices, connection icons, and an
interactive search morph in Settings. Financial calculations, saved preferences and Room schema v8
remain unchanged.

## Behaviour

- Month figures retain their actual values and forecast/baseline context. A shared scale draws actual,
  projected and recorded-average spending; it does not claim a budget. Amount measurement and font
  scale determine whether totals sit side by side or stack.
- Theme choices retain System/Light/Dark values. Labels that cannot fit reflow into preview rows,
  rather than breaking a single word or shrinking text. Android exposes the selected radio as checked.
- Pulling from the top of Settings drives the search fraction directly. Reversing the gesture closes
  an empty search. Release settles the fraction without changing header height. Typed queries stay
  visible while scrolling. An explicit search tap requests keyboard focus; a pull does not. Closing
  during expansion cancels the pending focus request. Nested search and Back to results still work.
- Connections uses the same existing bank actions and permission policies, with icons and coherent
  groups. No bank sign-in or OTP was triggered by testing.

## Verification

- Full app host run: 1192 cases, 0 failures/errors, 5 skipped optional private fixtures. Settings were
  rechecked after refinements, including a new cancellation regression and geometry checks at
  closed, midpoint and open fractions. The partial/reversed drag is exercised before release.
- 43 core screenshot comparisons passed. Only the three changed shell references were replaced;
  four references cover the new theme choices and search morph. Frames were visually inspected.
- 16 distinct emulator scenarios passed across the main run and targeted rechecks: Settings search
  in EN light/dark and RU dark at 1.5 font, bank pages, actual history filters, populated/empty month
  summaries, the Russian setup journey with IME, and dock labels through font 2.0.
- The targeted rerun corrected test synchronization: radio selection is `checked` on Android;
  absence assertions wait for the old root scene to finish exiting. Theme selection was verified
  before capture, not inferred from a tap or a still-changing screenshot.
- Release R8/lintVital, public-tree and diff checks passed. Signed release cold start on emulator-5554
  completed without AndroidRuntime/WHFIN errors.

Commands: `:app:testDebugUnitTest`, `:core-ui:updateDebugScreenshotTest`,
`:core-ui:validateDebugScreenshotTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
`:app:assembleRelease`, and targeted `adb -s emulator-5554 shell am instrument` invocations.
No instrumented test ran on the phone.

Final screens are under `/tmp/whfin-visual-0368-settings`, `/tmp/whfin-visual-0368-connections`,
`/tmp/whfin-visual-0368-summary`, `/tmp/whfin-visual-0368-lists`; the signed release screen is
`/tmp/whfin-visual-0368-release-home.png`. Core references are tracked in the screenshot test directory.
Logs include `/tmp/whfin-visual-tests.log`, `/tmp/whfin-visual-final-qa.log`,
`/tmp/whfin-visual-targeted-final.log`, `/tmp/whfin-visual-recheck-build.log`, and
`/tmp/whfin-visual-cancel-final.log`.

## Installation

Samsung was updated from 0.3.67 to 0.3.68 with `install -r --no-incremental`. Signing certificates match;
installed APK SHA-256 is `b8c6a3d38becf9bf07fb8e88366b86b53c1d5ab0b695deabf76c86495174536c`.
The original installation timestamp and SMS/notification grants remain unchanged. No post-install
AndroidRuntime/WHFIN errors were observed. The personal database was not read or cleared.
