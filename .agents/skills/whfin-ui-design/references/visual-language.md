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

Dark mode uses a warm olive-umber canvas with mineral, desaturated surfaces: dark paper rather than neutral
black, so cream ink and clay stay in the same family as the light theme. Preserve semantic contrast instead
of mechanically inverting light colors.

Category icons are outlined. Filled glyphs turn each row's marker into the loudest element and read as stock
Material beneath a custom language.

## Typography

- Use the bundled WHFIN editorial serif only for screen titles, key totals, and rare section landmarks.
  It is the default so those product-defining roles do not change across OEMs.
- Appearance may replace those editorial roles with `FontFamily.Default` for people who prefer their
  device font. Keep this choice persistent and global; do not mix both title families on one screen.
- Use a neutral sans for controls, rows, forms, and long text.
- Use tabular figures for every money amount and numeric summary. Money belongs to the editorial register,
  not to the row's sans: the numeric column is the heart of a ledger and needs its own voice. Set the
  currency symbol smaller and quieter than the digits so amounts align into a column.
- Caps with measured tracking marks a landmark — a day header or a screen section. A field or subsection
  label inside a block stays sentence case; when every block is shouted, the screen telegraphs.
- A result may be closed by an accounting double rule. Ordinary separators stay single hairlines.
- Keep transaction amounts and titles visually stronger than metadata, but smaller than screen totals.
- Avoid all-caps paragraphs. Short ledger labels may use uppercase with measured tracking.
- Let font scale grow; do not pin text to fixed-height containers.

## Composition

- Align screen content to a 20 dp horizontal rail on compact phones.
- Use 4/8 dp rhythm with named spacing tokens; prefer 12, 16, 20, 24, and 32 dp gaps.
- Treat a section as a heading plus rule or whitespace before reaching for a container.
- Use outlined or tonal containers only for coherent groups: month summary, one IBAN with its ledgers, permission explanation, import result, or decision block.
- Avoid card-in-card. Inside a group, separate rows with rules.
- Keep the app dock visually grounded but lighter than content: use an inset rule aligned to the 20 dp
  rail and stationary destination glyphs. Show selection with a filled glyph, semibold label, and
  primary color instead of an extra line or persistent selected-item fill. Align the separate
  create action to the same icon-and-label rhythm, without a fill or vertical lift. Use the primary color
  so it stays discoverable without pretending to be a third destination. It must
  not obscure the last ledger rows.
- Group primary-header actions into one low-tonal rail. Keep every action's 48 dp target and use the
  amount component for the metric so the shell speaks the same numeric language as the ledger.
- Treat the balance/action context header as the opening ledger row, not persistent chrome: it scrolls away with the screen and yields vertical space to the working content.
- Preserve only an opaque status-inset-height mask after that row scrolls away; content may continue edge-to-edge behind it, but must not compete visually with system icons.

## Screen signatures

### Feed

Make the monthly result the hero, then income/expense context, then search/filter tools. Keep the transaction ledger dense. Transfers are neutral; pending and debt annotations are secondary lines. A permission prompt is an inline notice, not a competing hero card. It must be dismissible, remember that choice, and leave the same control discoverable in Settings.

A parsed bank message without a resolved ledger is an Unrouted operation, not a transaction. Show it at
its real date as a muted ledger row with merchant/counterparty, amount, and an explicit routing action,
but exclude it from day/month totals, balances, categories, and statistics. Group provisional
transfers/conversions into one row; tapping opens the contextual resolver rather than generic Accounts.

Transaction details should read like a compact receipt, not a database record: lead with category or
counterparty, signed amount, date and account; keep editable status/category as flat ledger rows.
Never promote a missing description to the title, and keep destructive actions behind overflow plus
confirmation.

### Accounts

Show primary-currency net worth first, foreign balances as compact secondary figures, then available/reserve. Represent the real hierarchy as bank heading → IBAN group → currency ledger rows. Cash and wallets use the same container→balances grammar.

Account overview is a balance explanation, not monthly analytics. Compare assets, liabilities, available money, reserve, and source distribution only inside the primary currency. Until exchange rates and their timestamps exist, show other currencies as native amounts without percentages, converted totals, or donut segments.

Keep destructive account-level actions out of the primary action rail. Place them behind a clearly
labelled overflow or settings surface, followed by explicit confirmation; an incomplete adaptive row
must never make deletion the visually largest action.

On secondary ledger lists, keep creation as a compact icon action in the header rather than a text
button competing with the editorial title or a FAB covering rows. When the list is empty, repeat the
action with a clear text label inside the empty state.

### Statistics

Lead with the selected month's net result, income, and expenses. Category distribution supports rolling 1/3/6/12-month ranges; tapping a category changes a compact twelve-month trend instead of opening a decorative dashboard. Selecting a trend month promotes it to the Statistics period, so the result, rolling category distribution, trend comparison, and transaction drill-down refresh together. The selected month/category can open a focused transaction ledger; Back returns to the unchanged Statistics context. Keep balance adjustments in a separate Unaccounted section and exclude them from cash-flow totals and category trends. Attribute a linked GEL→foreign-currency conversion to the purchase category, but keep unsupported native-currency expenses separate until dated exchange rates exist.

For the current month, place one spending-pace block after the result: show the elapsed day count,
a simple month-end expense projection, and the previous full month's expense total. Never project a
historical month. Follow it with at most three largest absolute category changes among categories
that have current-month spending; each row opens that category's current-month transaction ledger.
Keep this sequence as result → pace → drivers → category detail → year trend, without dashboard tiles
or a second competing hero.

The expense amount opens a focused Spending scene rather than expanding the overview further. That scene
leads with the selected-month expense total and its average over the three preceding complete months,
then keeps category ring, rolling twelve-month trend, and category ledger in one connected reading flow.
The ring is a summary rather than a control: category rows select the trend and the trend action opens the
filtered transaction ledger. Show at most five named ring segments plus Other, keep uncategorized expenses
inside the distribution, and keep balance adjustments outside it.

### Composer

Treat the amount as the active focal field. Keep type selection explicit, account/category/date choices as compact decision rows, and the save action pinned above navigation/IME. Category selection is a full internal step, not a modal stacked over another modal.

### Working sheets

Treat filter, mapping, and compact-edit sheets as small working surfaces rather than plain stacks of
Material controls. Give them a short title plus one line of useful context, keep dense choices in
single-line horizontal rails when translations would wrap, and pin the final action area below the
scrolling content. A partially visible next choice is the preferred scroll cue; do not add decorative
arrows or a second row. Use motion and tonal emphasis only to clarify selection and continuity.

### Decision dialogs

Confirmation is a compact ledger decision block, not a stock system modal. Use the screen canvas
surface without Material tonal-elevation tint, a short semantic marker, direct title/body
copy, and two actions with equal geometry. Oxide belongs only to irreversible or data-losing
confirmation. At large font scales, actions reflow into equal full-width rows; essential labels must
not truncate. Long exact payloads scroll inside the decision body instead of pushing actions off-screen.
Keep routine destructive actions behind overflow or a secondary surface before showing the dialog.

For privacy-sensitive sharing, open an editable safe-by-default report before the platform Sharesheet.
Reading or adding raw source text is a separate explicit action with an exact preview and confirmation;
after confirmation return to the editor so the final payload is still visible before Share.

### Statements

Emphasize truth, coverage, gaps, and review status. File names are metadata and must ellipsize; they must never dominate import results. Prefer a timeline/register over repeated large cards.

### Settings

Use a compact preference list grouped by section labels. Give permission explanations enough room, but keep their action hierarchy distinct from navigation rows.

Demo is a temporary workspace, not a preference switch. In the Personal workspace, expose `Explore demo`
as a secondary row near About with an explanatory entry sheet. While Demo is active, keep a compact
non-dismissible workspace indicator and direct exit visible across destinations; validate its final
geometry in real renders before treating the pattern as stable.

The user-facing SMS destination is Bank SMS, ordered as status and next action → needs attention → recent
activity → cards/accounts → optional history scan → troubleshooting. Keep parser diagnostics inside an
individual result instead of making the whole screen feel like a developer log.

### First run

Use one full-screen Welcome choice before the shell on a fresh untouched installation: Personal setup or
Demo workspace. Do not use a feature carousel or request permissions there. Personal setup is
bank-centred, guided but skippable, and exposes only channels that work for the chosen bank.

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
