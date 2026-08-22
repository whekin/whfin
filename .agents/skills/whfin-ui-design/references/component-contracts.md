# WHFIN component contracts

## Token ownership

Keep colors, typography, spacing, shapes, sizes, elevation, and motion in `:core-ui`. Expose them through `WhfinTheme`, composition locals, or stable token objects. Feature code must not introduce a new recurring dp/color/shape value without first deciding whether it is a token.

## Product components

- `WhfinAppShell`: edge-to-edge top region, content host, dock, and optional floating action.
- `WhfinTopBar`: large/compact title modes and accessible navigation/action slots.
- `WhfinContextHeader`: compact primary-screen top app bar that pairs one current metric with up to three screen-specific actions. The metric uses `WhfinAmount`, including the quieter currency symbol, and aligns to the 20 dp content rail. Actions share one low-tonal rail instead of reading as unrelated toolbar buttons; each action still owns a 48 dp target. Feed and Accounts use Material `enterAlways` behavior: the bar leaves on downward scrolling and returns as soon as the user scrolls upward. Contextual selection replaces it with a pinned bar so batch actions and the current selection count remain available. It owns the status-bar inset and must be passed through the destination's single `Scaffold` owner.
- `WhfinStatusBarProtection`: an opaque background mask exactly as tall as the status-bar inset. Use it only on long reading surfaces whose heading intentionally remains a list item; primary Feed and Accounts use `WhfinContextHeader` as their system-inset owner instead.
- `WhfinDock`: one grounded, full-width primary rail with two stable destinations and a separate create action. Destinations use 22 dp outline/filled glyph pairs, restrained labels, and 48 dp-or-larger targets. Selection cross-fades to the filled glyph, primary color, and semibold label without an extra line, pill, persistent fill, or movement. The center create action is an unfilled icon-and-short-label button aligned to the same 64 dp rhythm. Its custom plus uses a 2.5 dp rounded stroke so its optical weight matches the destination glyphs. It uses the primary color but remains a button rather than a third navigation state, and keeps a 48 dp-or-larger target without obscuring ledger rows.
- `WhfinButton`: primary, secondary, quiet, and destructive variants with consistent enabled/loading behavior.
- `WhfinIconButton`: standard and quiet variants; never smaller than the minimum target.
  `WhfinContextHeader` supplies the prominent icon size to all of its actions automatically, keeping
  Feed, Accounts, previews, and selection mode visually consistent without per-screen flags.
- `WhfinBackButton`: the single borderless circular treatment for hierarchical Back navigation. Use a close icon only for dismissing a modal/form; do not restyle Back per feature.
- `WhfinSectionHeader`: optional eyebrow, title, supporting text, and trailing action.
- `WhfinLedgerGroup`: one outlined/tonal grouping surface with internal rules; do not nest it.
- `WhfinLedgerRow`: icon/marker, title, metadata, amount/status, click semantics, and optional divider.
- `WhfinNotice`: info, attention, error, permission, and unavailable states with one dominant action at most. A persistent optional proposal may add one quiet, accessible dismiss action; dismissal policy and persistence stay in the feature layer.
- `WhfinWorkspaceStrip`: a compact, non-dismissible workspace identity with one quiet direct action.
  It uses a full-width tonal ledger row plus a bottom rule, not a floating banner or card. The strip
  does not own system insets; the app-level workspace frame places it below the status bar and reuses
  that same frame inside full-screen dialogs so workspace identity and exit do not disappear in forms.
- `WhfinSwitch`: Material-backed on/off control with a stable 48 dp target and explicit accessibility label. Persisted feature policy stays outside `:core-ui`; an OS permission may block an enabled preference without silently changing the user's choice.
- `WhfinHaptics`: restrained action semantics, not custom vibration waveforms. Use a subtle segment tick for in-app destination changes and platform toggle-on/off effects for switches; do not add feedback to passive scrolling or duplicate the system Back gesture.
- `WhfinBackGesture` / `Modifier.whfinPredictiveBack`: the shell's answer to a Back swipe. `rememberWhfinBackGesture(enabled, onBack)` registers a `PredictiveBackHandler`; the modifier insets the page to 92%, nudges it along the drag, rounds it to 28 dp and lifts it, reading the progress in the draw phase. Apply it to everything the app is showing — workspace strip and dock included — not to the page alone. `onBack` runs on commit only; an abandoned pull settles by itself.
- `rememberWhfinPressShape`: the press affordance for a quiet palette. A pressed control tightens its corner to 40% and springs back; a pressed tint would read as noise against this canvas. Corner values live in `WhfinSizes` (`buttonCorner`, `pillCorner`) so they can animate.
- `WhfinFilterPill`: a choice pill. Pass `centered = true` with a weight when several mutually exclusive answers divide one row; a scrolling rail is for sets that genuinely do not fit.
- `WhfinStatePane`: loading, empty, error, and unavailable presentation with compact guidance and optional retry/action.
- `WhfinFilterBar`: horizontally resilient filters and search affordance.
- `WhfinChoiceRail`: a single horizontal scrolling line for mutually exclusive or compact multi-select
  choices whose translated labels cannot safely fit. Do not use `FlowRow` for these controls: wrapping
  changes their reading order and makes the sheet height unstable. Keep a partial next item visible as
  a scroll cue and preserve 48 dp targets.
- `WhfinField`: a persistent label above a softly tonal input surface, with a quiet bottom focus accent
  and shared supporting/error behavior. Keep Material text-field semantics and IME integration, but do
  not expose default outlined/floating-label fields in feature UI.
- `WhfinCodeDots` + `WhfinNumericKeypad`: secure four-digit entry shared by App Lock and bank OTP.
  Use circular 68 dp targets, a real Backspace icon, clipped pressed states and keyboard-tap haptics.
  The feature owns code lifetime and submission policy; OTP must stay in memory, clear when its challenge
  leaves the screen, and require explicit user submission rather than automatic SMS confirmation.
- `WhfinFormSheet`: scrollable, IME-safe input sheet with explicit primary/secondary action hierarchy.
- `WhfinFullScreenForm`: edge-to-edge complex editor with pinned action area and IME-safe content.
- `WhfinConfirmDialog`: the single decision surface for delete, archive, reset, and discard confirmation.
  It uses a semantic ledger marker, the screen canvas without tonal-elevation tint, equal action
  geometry, and reflows actions at large font scales. Feature code owns strings and side effects; do
  not expose Material `AlertDialog`. Long exact previews (for example a privacy-sensitive source
  message) keep title and actions visible while the body scrolls inside a bounded region.
- `WhfinActionMenu`: the shared compact overflow surface. It uses the screen canvas, a quiet rule,
  and no Material tonal tint; feature code owns menu items and action semantics.
- `WhfinAmount`: editorial-serif tabular money typography and semantic color without embedding formatting
  policy. It receives an already formatted string plus the currency symbol to set quieter, and reads the
  editorial family from the theme so `Device font` applies to figures too. The bundled serif has a single
  weight, so amounts must not request a bold weight.
- `WhfinFieldLabel`: sentence-case label for a field or subsection. `WhfinSectionLabel` (caps with tracking)
  is reserved for landmarks — a day header or a screen section.
- `WhfinTotalRule`: the accounting double rule that closes a result block. Ordinary row separators stay
  single `HorizontalDivider`s.
- `WhfinDistributionBar`: compact proportional comparison for values already expressed in the same currency or unit; never use it to imply an exchange rate.
- `WhfinDonutChart`: a restrained, non-interactive proportional summary for category composition. The
  adjacent ledger rows own selection and navigation so tiny arcs never become inaccessible touch targets;
  the ring exposes one complete accessibility description and may dim non-selected segments.
- `WhfinMonthlyBarChart`: compact selectable comparison of up to twelve ordered periods. The selected period uses emphasis, every bar exposes an accessible label/value, and feature code owns money/date formatting, selection, and filtering. Rolling ranges preserve 48 dp targets and may scroll. A named calendar year is the deliberate dense exception: all twelve bars fit at once, keep a tall tap region, and the screen must also expose previous/next period arrows for accessible navigation.

Interactive surfaces own their click semantics: use the clickable `Surface` overload or clip the indication to the same shape before `clickable`. Circular controls must never produce square pressed states. Full-bleed ledger rows use an edge-to-edge rectangular pressed state while their content and dividers remain on the shared horizontal rail.

Destination chrome and destination content must share one layout owner. Do not conditionally add a Scaffold app bar while simultaneously swapping its body: the body can observe the previous frame's inner padding. Animate the complete opaque destination surface instead, keeping status/navigation inset ownership inside that destination.

## Boundaries

Keep Room entities, repositories, app resources, navigation routes, and feature-specific strings out of `:core-ui`. Pass text, icons, state, and callbacks into shared components. Category data adapters and money formatting remain in the app layer because they encode product/domain policy.

Use `WhfinConfirmDialog` for consequential confirmation. Use Material sheets, fields, dialogs, and
accessibility behavior internally, styled through WHFIN components. Direct Material usage is acceptable
for invisible layout primitives or one-off behavior that has no product appearance.

## State pattern

Expose a stable screen state with one of `Loading`, `Empty`, `Error`, `Unavailable`, or `Content`. If a screen can retain old content while refreshing, represent refresh separately and keep content visible. Route composables collect flows; content composables accept plain immutable state and callbacks.

## Preview matrix

For each key screen, provide representative preview data for empty and populated states. Add error/unavailable when supported. Render at compact phone light, compact phone dark, font scale 1.5, and compact height. Component previews cover enabled, disabled, destructive, and long-text cases.
