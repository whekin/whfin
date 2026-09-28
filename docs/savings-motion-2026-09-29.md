# Interactive savings charts — 2026-09-29

## Behavior

Forecast and historical balance charts accept taps and horizontal drags. A guide and halo identify
the chosen point. Forecast selection shares its exact month with the existing accessible scrubber;
historical balance selection shares its month with the previous/next controls and exact reading.
Vertical dragging remains available to the surrounding scroll container. The whole plot is a touch
surface, so selecting a month does not require hitting its small painted dot.

The forecast initially selects the last contribution on/before a chosen deadline, limited to the
visible horizon. A new deadline updates that selection. The separate deadline reading keeps the exact
chosen day: a date between contributions does not produce an invented fractional contribution.
Explicit horizon and scrubber choices still work. Selection survives saved-state recreation.

Changes to the forecast's pace, balance or goal animate only normalized drawing coordinates with
`WhfinMotion.quick`. An interrupted transition retargets from the current value; no idle animation
or entrance from a fictitious zero balance is used. Exact amounts, dates and accessibility state
change immediately with the source projection. All pixels use the active Material palette. Actual
history is solid; forecast is dashed, including in monochromatic dynamic palettes.

When settled, the chart uses the original Double-to-pixel calculation and fractional dp plot height.
This preserves the existing static screenshot references. The initial implementation rounded the
Canvas height and normalized coordinates earlier, which changed antialiasing at fractional densities;
that discrepancy was fixed without regenerating references.

No financial arithmetic, database schema, plan persistence, bank requests or money movements changed.
The screen still saves only through its existing explicit Save action.

## Evidence

- Full `:app:testDebugUnitTest`: 1273 tests, zero failures/errors, 5 skipped.

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- `:core-ui:validateDebugScreenshotTest`: all 43 existing references passed unchanged.
- `:app:assembleRelease`: R8/lintVital passed. AGP emitted its existing non-fatal Glance Compose
  stack-trace mapping warning.
- Final address-scoped `SavingsChartMotionTest` + `SavingsMotionVisualTest`: all 6 cases passed.
  Selected/dragged forecast and editor/IME captures were visually inspected in all four configurations.

- Targeted host tests cover touch/drag and accessibility selection, saved-state restoration,
  deadline dates between contributions, live editor calculations and exact projection arithmetic.
- Device pixel tests cover a mid-animation frame, a second edit while moving, settlement identical
  to static rendering, no continuing movement and animation duration scale zero.
- Emulator QA uses a debug-only, emulator-guarded synthetic harness with the production projection
  panel and editor. Configurations: EN/light and RU/dark/font 1.5/1200×1920, brand and Material You.
  Scenarios include tap, horizontal drag, amount edit and real numeric IME with a visible Save action.
- Screenshot artifacts: ignored `artifacts/savings-motion-2026-09-29/savings-motion-qa/`.

The physical phone was not installed or modified. Real bank and personal data were not read.
