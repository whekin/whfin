# Onboarding illustrations — 2026-09-29

## Product change

Welcome, category setup (overview and suggestions), and the final balance-review step now use
three related abstract vector compositions: gathering, sorting and balance. The existing copy,
optional setup choices, bank progress and review rules remain authoritative. The final illustration
has no success/check badge and does not claim that balances or bank history are reconciled.

`WhfinIllustration` in `:core-ui` owns the geometry, palette and entrance motion. Feature code chooses
the composition and available height. All fills and details use `MaterialTheme.colorScheme`, including
primary/secondary/tertiary containers and their paired foregrounds. This follows Material You,
light/dark mode and live palette changes without bitmap variants or baked-in brand colours.

One finite `WhfinMotion.screen` animation assembles the shapes on entry. It uses Compose's duration
scale, has no idle loop, does not delay actions, and does not replay when the palette changes.
Animation progress is read during drawing rather than recomposing the screen every frame. The
appeared flag is saveable, and previews show the settled composition. Artwork is decorative with
empty accessibility semantics. No new libraries, assets, bank requests or ledger writes are needed.

Welcome scales artwork to its available viewport (88–220 dp). Setup artwork uses 112 dp, reducing to
64 dp below 700 dp screen height or at font scale 1.3+. Text remains scrollable and actions stay pinned.

The design-language contract now explicitly permits these finite decorative moments in onboarding.
Easter eggs and changes to analytical charts are outside this iteration.

## Verification

- Existing category/setup host checks: 37 tests passed.
- Full `:app:testDebugUnitTest`: 1269 tests, zero failures/errors, 5 skipped.
- `:core-ui:validateDebugScreenshotTest`: passed.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Final `:app:assembleRelease`: R8 and lintVital passed. AGP emitted its existing non-fatal Glance
  Compose stack-trace mapping warning.
- Device pixel test verifies entrance movement, identical frames after settling, immediate live
  recolouring and no movement replay on a palette change.
- Full existing personal setup journeys passed in EN/light and RU/dark/font 1.5.
- Five final emulator visual scenarios passed: EN/light with brand and Material You palettes,
  RU/dark/font 1.5 at 1200×1920 with both palettes, and system animations disabled. All twelve final
  screen captures (Welcome, categories, Ready × four configurations) were visually inspected.
- A dedicated debug-only, emulator-guarded harness renders production stateless screens with
  synthetic data. It does not reset or enter the personal ledger.

QA captures belong under ignored `artifacts/illustrations-2026-09-29/illustrations-qa/`.
The physical phone was not used. No release was installed.

## QA corrections

Visual inspection caught circles using the Canvas default centre inside a translated group; all
local circles now use `Offset.Zero`. Screenshot capture waits for presentation after accessibility
updates, which can otherwise return the preceding screen. The first paused-clock test harness was
reworked to mount its host before pausing the clock; the corrected device pixel test passed.

One combined final device run met an Android startup ANR while R8 was consuming resources. No UI
assertion ran to completion in that attempt; the five final visual scenarios passed when rerun after the build.
