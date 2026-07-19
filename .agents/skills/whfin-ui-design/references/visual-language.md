# Quiet Ledger — visual language

## Product idea

WHFIN removes bookkeeping work: bank SMS provides immediacy, statements provide truth, and remembered categorization reduces future input. The interface should feel like a precise personal ledger maintained for the user, not like a bank marketing page or an analytics dashboard.

## Character

Use the visual name **Quiet Ledger / Тихая тбилисская книга**. Combine editorial hierarchy with the compactness of a working register:

- totals read first;
- labels, rules, and columns explain the total;
- transaction rows carry most of the screen density;
- interaction is quiet until a decision is required;
- irregular real bank text, Georgian names, IBANs, and several currencies remain credible rather than decorative.

The language may recall a well-kept ledger through alignment, hairlines, warm paper, and tabular figures. Do not imitate paper texture, ruled notebooks, stamps, or skeuomorphic stationery.

## Color roles

- **Paper**: warm cream canvas; never pure white in light mode.
- **Ink**: near-black green for primary text; avoid cold neutral black.
- **Bottle**: trust, confirmed state, primary action, positive income.
- **Sage**: selected surfaces and low-priority grouping.
- **Clay**: expense, attention, pending state, and focused accent.
- **Oxide**: destructive action and errors; keep distinct from ordinary clay expense.
- **Rule**: warm low-contrast divider; the main grouping device.

Dark mode uses deep green-black canvas and mineral, desaturated surfaces. Preserve semantic contrast instead of mechanically inverting light colors.

## Typography

- Use an editorial serif only for screen titles, key totals, and rare section landmarks.
- Use a neutral sans for controls, rows, forms, and long text.
- Use tabular figures for every money amount and numeric summary.
- Keep transaction amounts and titles visually stronger than metadata, but smaller than screen totals.
- Avoid all-caps paragraphs. Short ledger labels may use uppercase with measured tracking.
- Let font scale grow; do not pin text to fixed-height containers.

## Composition

- Align screen content to a 20 dp horizontal rail on compact phones.
- Use 4/8 dp rhythm with named spacing tokens; prefer 12, 16, 20, 24, and 32 dp gaps.
- Treat a section as a heading plus rule or whitespace before reaching for a container.
- Use outlined or tonal containers only for coherent groups: month summary, one IBAN with its ledgers, permission explanation, import result, or decision block.
- Avoid card-in-card. Inside a group, separate rows with rules.
- Keep the app dock visually grounded but lighter than content. The add action must not obscure the last ledger rows.
- Treat the balance/action context header as the opening ledger row, not persistent chrome: it scrolls away with the screen and yields vertical space to the working content.
- Preserve only an opaque status-inset-height mask after that row scrolls away; content may continue edge-to-edge behind it, but must not compete visually with system icons.

## Screen signatures

### Feed

Make the monthly result the hero, then income/expense context, then search/filter tools. Keep the transaction ledger dense. Transfers are neutral; pending and debt annotations are secondary lines. A permission prompt is an inline notice, not a competing hero card. It must be dismissible, remember that choice, and leave the same control discoverable in Settings.

### Accounts

Show primary-currency net worth first, foreign balances as compact secondary figures, then available/reserve. Represent the real hierarchy as bank heading → IBAN group → currency ledger rows. Cash and wallets use the same container→balances grammar.

Account overview is a balance explanation, not monthly analytics. Compare assets, liabilities, available money, reserve, and source distribution only inside the primary currency. Until exchange rates and their timestamps exist, show other currencies as native amounts without percentages, converted totals, or donut segments.

Keep destructive account-level actions out of the primary action rail. Place them behind a clearly
labelled overflow or settings surface, followed by explicit confirmation; an incomplete adaptive row
must never make deletion the visually largest action.

### Statistics

Lead with the selected month's net result, income, and expenses. Category distribution supports rolling 1/3/6/12-month ranges; tapping a category changes a compact twelve-month trend instead of opening a decorative dashboard. Trend months are selectable and the selected month/category can open a focused transaction ledger; Back returns to the unchanged Statistics context. Keep balance adjustments in a separate Unaccounted section and exclude them from cash-flow totals and category trends. Attribute a linked GEL→foreign-currency conversion to the purchase category, but keep unsupported native-currency expenses separate until dated exchange rates exist.

### Composer

Treat the amount as the active focal field. Keep type selection explicit, account/category/date choices as compact decision rows, and the save action pinned above navigation/IME. Category selection is a full internal step, not a modal stacked over another modal.

### Working sheets

Treat filter, mapping, and compact-edit sheets as small working surfaces rather than plain stacks of
Material controls. Give them a short title plus one line of useful context, keep dense choices in
single-line horizontal rails when translations would wrap, and pin the final action area below the
scrolling content. A partially visible next choice is the preferred scroll cue; do not add decorative
arrows or a second row. Use motion and tonal emphasis only to clarify selection and continuity.

### Statements

Emphasize truth, coverage, gaps, and review status. File names are metadata and must ellipsize; they must never dominate import results. Prefer a timeline/register over repeated large cards.

### Settings

Use a compact preference list grouped by section labels. Give permission explanations enough room, but keep their action hierarchy distinct from navigation rows.

## Motion

- Use 120–220 ms for selection and small state changes.
- Use 220–300 ms for sheet/screen transitions.
- Navigate between complete opaque destination surfaces. A destination's system inset, top bar, and body must change under one layout owner; never add a `Scaffold` app-bar slot conditionally while replacing its body.
- Use a short directional shared-axis transition for hierarchy: forward enters from the right, Back returns toward the right. Preserve dock position when switching peers.
- Pair explicit destination changes with one subtle platform navigation haptic and switches with the platform on/off haptic. Do not duplicate Android's own Back-gesture feedback or vibrate for scrolling.
- Animate position or emphasis only when it explains continuity.
- Avoid staggered decoration, springy finance totals, or transitions that leave partially rendered frames for perceptible time.

## Accessibility and resilience

- Maintain at least 48 dp interactive targets even when visual rows are denser.
- Provide content descriptions for icon-only actions; decorative icons remain null.
- Never encode income/expense/status only by color.
- Test long Georgian/Russian merchant names, large amounts, negative values, IBANs, multiple currencies, and missing descriptions.
- At font scale 1.5, allow wrapping or reflow before truncating essential action labels.
