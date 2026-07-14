---
name: whfin-ui-design
description: Design, refactor, review, and visually verify Jetpack Compose UI for the WHFIN Android personal-finance app. Use for WHFIN screens, app shell, navigation, transaction composer, accounts, debts, bank statements, widgets, design tokens, product components, previews, accessibility, edge-to-edge, IME behavior, light/dark themes, large font scale, and screenshot-based visual QA.
---

# WHFIN UI Design

Build WHFIN as a dense, calm financial instrument whose hierarchy remains clear with real multilingual and multi-currency data. Preserve business behavior and use Material 3 as the behavioral and accessibility substrate.

## Required workflow

1. Read repository `AGENTS.md`, `SPEC.md`, affected UI/state code, and [visual-language.md](references/visual-language.md). Read [component-contracts.md](references/component-contracts.md) when adding or changing shared components.
2. Inspect the current screen on a device or emulator before editing. Capture both layout semantics and a screenshot; visually inspect the screenshot.
3. Identify every relevant state: loading, empty, error, unavailable, populated, selected, disabled, and destructive confirmation. Do not hide unsupported states behind a generic empty message.
4. Put reusable visual decisions in `:core-ui`. Keep Material primitives behind WHFIN components when the primitive carries product appearance. Do not wrap incidental `Text`, `Row`, or `Spacer`.
5. Keep feature state and business callbacks outside `:core-ui`. Prefer stateless screen content plus a thin state-collecting route so previews and tests require no database.
6. Apply edge-to-edge once per hierarchy. Pass scaffold/list insets through `contentPadding` and consume them. For text input, verify `adjustResize`, focus, scrolling, and the primary action with the real IME.
7. Use Material icons or intentional vector assets. Never substitute Unicode glyphs for icons.
8. Add or update previews for the states changed. Include light, dark, font scale 1.5, and at least one compact-height configuration for screen-level work.
9. Build, run relevant unit/UI tests, and exercise the real flow. Verify light/dark, font scale 1.5, system bars, IME, touch targets, truncation, and RU/EN strings.
10. Capture final screenshots and visually inspect them. Iterate until hierarchy, alignment, density, clipping, and transitions hold up with real data. Update project documentation when introducing a durable rule.

## Guardrails

- Preserve the SMS → statement reconciliation model, signed minor units, transfer grouping, debt semantics, and account hierarchy.
- Use color for meaning, not decoration. Keep category color local to its icon or small marker.
- Prefer dividers, alignment, rhythm, and contrast over nested filled cards.
- Keep routine rows compact while preserving a 48 dp minimum interactive target.
- Distinguish primary, secondary, quiet, and destructive actions by placement, fill, and color.
- Avoid gradients, glass effects, generic dashboard tiles, oversized empty space, and competing hero elements.
- Never finish on compilation alone; a change is incomplete until inspected in a real render.
- Treat the user's physical phone as data-bearing production-like hardware. Use it only for non-destructive install/upgrade and manual visual QA. Never run `connectedAndroidTest`, instrumentation, package uninstall, `pm clear`, destructive database migration, or any workflow that can reinstall/clear app data on it. Run those tests on a disposable emulator. An exceptional device test requires explicit user approval plus a verified backup/restore path first.

## Completion evidence

Report the build/test commands, device configurations exercised, screenshots produced, and any unverified state. Do not claim visual completion for a configuration that was not rendered.
