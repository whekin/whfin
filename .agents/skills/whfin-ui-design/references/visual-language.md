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

The language may recall a well-kept ledger through alignment, hairlines, cool mist surfaces, and tabular figures. Do not imitate paper texture, ruled notebooks, stamps, or skeuomorphic stationery.

## Color roles

The September 2026 redesign combines the mist/teal concept with restrained editorial typography.

- **Paper**: cool blue-white canvas (#EFF5F8); raised groups are nearly white.
- **Ink**: deep blue-grey (#193442).
- **Bottle**: teal (#176C70), used for primary actions and positive income.
- **Sage**: pale teal selection surfaces.
- **Clay**: coral family; use the darker text token (#AA4834) for readable small text.
- **Oxide**: distinct red for destructive actions and errors.
- **Rule**: quiet blue-grey dividers.

Dark mode uses a blue-black canvas (#101F29), layered slate surfaces, pale teal and peach.
Text/accent pairs are checked at 4.5:1 on the working surfaces. Do not copy the pale coral
from a concept image into small text. Respect the user's dynamic-colour and device-font preferences.

Category icons are outlined. Filled glyphs turn each row's marker into the loudest element and read as stock
Material beneath a custom language.

## Typography

- Use the bundled WHFIN editorial serif only for screen titles, key totals, and rare section landmarks.
  It is the default so those product-defining roles do not change across OEMs.
- Appearance may replace those editorial roles with `FontFamily.Default` for people who prefer their
  device font. Keep this choice persistent and global; do not mix both title families on one screen.
- Use a neutral sans for controls, rows, forms, and long text.
- Use tabular figures for every money amount and numeric summary. Large focal totals use the editorial
  family; transaction amounts, supporting metrics and controls use neutral sans. The amount component
  preserves the caller's type role. Set the currency symbol smaller and quieter than the digits.
- Section labels and field labels stay in sentence case. Use weight and spacing for hierarchy rather
  than automatic uppercasing; a date formatter may still choose its own localized date style.
- Separate a result from its supporting figures with one hairline, consistently across screens.
- Keep transaction amounts and titles visually stronger than metadata, but smaller than screen totals.
- Avoid all-caps paragraphs. Short ledger labels may use uppercase with measured tracking.
- Let font scale grow; do not pin text to fixed-height containers.

## Composition

- Align screen content to a 20 dp horizontal rail on compact phones.
- Use 4/8 dp rhythm with named spacing tokens; prefer 12, 16, 20, 24, and 32 dp gaps.
- Treat a section as a heading plus rule or whitespace before reaching for a container.
- Use lightly raised or tonal surfaces only for coherent groups: month summary, one IBAN with its ledgers, permission explanation, import result, or decision block.
- Avoid card-in-card. Inside a group, separate rows with rules.
- Keep the app dock visually grounded but lighter than content: use an inset rule aligned to the 20 dp
  rail and stationary destination glyphs. Show selection with a filled glyph, semibold label, and
  primary color instead of an extra line or persistent selected-item fill. The create action sits
  between the four destinations and does **not** share their shape: it is a filled disc with no
  label, because with four sections flanking it the same icon-and-label rhythm read as a fifth one.
  A control may have a surface; a destination may not. It must not obscure the last ledger rows.
- Group primary-header actions into one low-tonal rail. Keep every action's 48 dp target and use the
  amount component for the metric so the shell speaks the same numeric language as the ledger.
- Treat the balance/action context header as the opening ledger row, not persistent chrome: it scrolls away with the screen and yields vertical space to the working content.
- Preserve only an opaque status-inset-height mask after that row scrolls away; content may continue edge-to-edge behind it, but must not compete visually with system icons.

## Shell

Four destinations, always one tap away: **Home · History · Accounts · Analytics**, all at depth 0, with
the create action between them. Settings live in Home's header, not inside Accounts. There is no pager:
two of the four answer horizontal drags of their own — analytics moves through time that way — and a
pager underneath would be a second reader of the same gesture.

Back is one step, never a trail: from any root it returns to Home, and from a nested scene it pops to
the root that opened it. Tapping through the dock is browsing, not descending, so Back does not replay
the order the sections happened to be visited in. The composer belongs to the shell: it opens over
whatever section asked for it and gives that section back.

## Screen signatures

### Home and the record of transactions

Both are the same ledger screen in two modes. **Home answers "what should I do with money today", so it
opens with what can be spent — not with the month's result.** The month's net is structurally negative
before payday (salary has not arrived, rent has left), so leading with it made the loudest number on the
first screen announce an ordinary state as an alarm. Order: available now → how long it lasts → what is
already promised out of it → what needs a decision → what just happened → and the month last, compactly,
as two read facts (spent, received) rather than a derived net.

The record of transactions leads with its own context header and search/filter tools. Keep the
transaction ledger dense. Transfers are neutral; pending and debt annotations are secondary lines. A permission prompt is an inline notice, not a competing hero card. It must be dismissible, remember that choice, and leave the same control discoverable in Settings.

A parsed bank message without a resolved ledger is an Unrouted operation, not a transaction. Show it at
its real date as a muted ledger row with merchant/counterparty, amount, and an explicit routing action,
but exclude it from day/month totals, balances, categories, and statistics. Group provisional
transfers/conversions into one row; tapping opens the contextual resolver rather than generic Accounts.

Transaction details should read like a compact receipt, not a database record: lead with category or
counterparty, signed amount, date and account; keep editable status/category as flat ledger rows.
Never promote a missing description to the title, and keep destructive actions behind overflow plus
confirmation.

### Accounts

Show primary-currency net worth first, foreign balances as compact secondary figures, then available/reserve. Represent the real hierarchy as bank heading → IBAN group → currency balances. Cash and wallets use the same container→balances grammar.

An account's currencies are short numbers, not rows of prose: two or three sit side by side as one
strip of equal cells, and one collapses into the account heading itself. Fall back to stacked rows
only where a cell would have to truncate its own amount — four or more currencies, or a large font
scale. A ledger's label repeats nothing the heading above it already carries: an imported name is
written by the bank out of the bank, the currency and the account number, so what remains after
removing those three is the only part a person chose, and where nothing remains the account number
is the honest name. Two doors per account, each with its own affordance: a balance opens that
ledger's activity, a pencil opens the account editor. A chevron would promise a page and open a form.

Account overview is a balance explanation, not monthly analytics. Compare assets, liabilities, available money, reserve, and source distribution only inside the primary currency. Until exchange rates and their timestamps exist, show other currencies as native amounts without percentages, converted totals, or donut segments.

Keep destructive account-level actions out of the primary action rail. Place them behind a clearly
labelled overflow or settings surface, followed by explicit confirmation; an incomplete adaptive row
must never make deletion the visually largest action.

On secondary ledger lists, keep creation as a compact icon action in the header rather than a text
button competing with the editorial title or a FAB covering rows. When the list is empty, repeat the
action with a clear text label inside the empty state.

Account activity names the bank, currency and account number once using the shared account naming
rules. The balance is a read-only amount, without a settings icon or hidden adjustment tap target.
Keep account editing as a compact pencil beside its heading; balance repair and deletion belong in
the labelled overflow menu. An erroneous owner-entered opening is corrected at its original basis,
not recorded as a new unexplained movement today.

### Analytics

Lead with the selected period's spending, then income and net result as supporting figures. At large
font scales supporting metrics stack instead of squeezing their money columns. Selecting a trend month promotes it to the Analytics period, so the result, the difference, the categories and the drill-down refresh together. Keep the visible twelve-month window stable while selecting a month already inside it, so later months remain available for a direct return in both Analytics and Spending. The selected month/category can open a focused transaction ledger; Back returns to the unchanged Analytics context. Keep balance adjustments in a separate Unaccounted section and exclude them from cash-flow totals and category trends. Attribute a linked GEL→foreign-currency conversion to the purchase category, but keep unsupported native-currency expenses separate until dated exchange rates exist.

Answer **why the period differs** before showing any picture of it. The block states one difference
against the recorded average, names the base on the line under it, and then attributes that difference
to categories: each row is what a category spent minus its share of the base, so the rows add up to the
number above them exactly. Include every category that spent in either window, fold what is not shown
into one "Other changes" row carrying the exact remainder, and open the full list from a quiet line
rather than a framed button. Draw each contribution as a bar from a shared vertical zero — one axis and
one money scale for the whole block, length from the contribution and nothing else. The row's own number
is the contribution, not the category total.

Never call the base usual, normal or typical: WHFIN cannot show a month complete, so it is the recorded
average, and one line carries both that and the caveat. Never call spending above it an overspend —
there is no user-declared target.

For a running period, place the spending-pace block after the difference: elapsed day count, a
month-end projection, and the same recorded average over whole periods. Never project a historical
period. Reading order is result → why → pace → where it went → year chart, without dashboard tiles or
a second competing hero.

Spending is the screen of composition, reached from Analytics by one row rather than a colour bar with
a button under it. It leads with the period's expense total and the same difference sentence, then
categories, counterparties and the year chart. There is no ring: it drew the proportions the category
rows already carry, without a single name beside them and at the size of the screen. Each row draws its
own share as a hairline under its name.

### Composer

Treat the amount as the active focal field. Keep type selection explicit, account/category/date choices as compact decision rows, and the save action pinned above navigation/IME. Category selection is a full internal step, not a modal stacked over another modal.

Who was paid is asked in the same grammar as the category: a ranked rail of the likely few and a
door to the full searchable list, placed on the section label rather than at the end of the rail. The
two rails answer each other — a chosen name fills an empty category, a chosen category lifts the
names filed there — and a remembered answer is an offer, never a correction of an explicit one. The
name is stored as the identity a statement would have written, so nothing downstream needs a second
mechanism to read it. A transfer has no counterparty: both of its sides are the person's own
accounts.

A new expense also asks "For whom", defaulting to self, before Save. Keep this separate from
"Paid to": the merchant receiving payment is not necessarily the person benefiting. Selecting or
naming a person and choosing their share is an internal composer step, with Back returning to the
same expense. Draft choices must not create people or ledger rows before the expense is saved.

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

The root is five compact rows: Connections, Bookkeeping, Application, Data and security, About.
Connections uses one row per configured bank. A common provider page combines history sync, channels,
accounts, sign-in and secondary diagnostics. Keep raw push logs out of routine configuration.
Search reaches leaf settings globally and Back restores the originating query. Nested settings pages
always show the top-bar search action. Search is the first item in the catalogue's single scroll container. Once it leaves view, show a search
icon at the right of the top bar, reserving its space even when hidden so the title never reflows.
Tapping the icon returns to and focuses the field. Do not add an independent enterAlways header that
consumes the catalogue's scroll or reappears on every small change of direction.

Use a compact preference list grouped by section labels. Toggle rows are one accessible switch target:
tapping the label or the thumb changes the same value once. Use a single segmented choice for short
exclusive settings; when measured labels cannot fit, reflow to complete radio rows without shrinking text. Give permission explanations enough room, but keep their action hierarchy distinct from navigation rows.

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

- Use `WhfinMotion` springs, not durations. They come from the theme's `MotionScheme.expressive()`, so
  an interrupted movement continues from its own velocity instead of restarting a curve and WHFIN's
  transitions stay in step with its Material components. Pixel travel uses `WhfinMotion.travel()` so a
  spring stops at the pixel rather than resolving invisible fractions.
- Answer the Back gesture continuously: a custom shell must draw the pull with `whfinPredictiveBack`
  rather than committing the destination change at the end of an invisible swipe.
- Navigate between complete opaque destination surfaces. A destination's system inset, top bar, and body must change under one layout owner; never add a `Scaffold` app-bar slot conditionally while replacing its body.
- Use a short directional shared-axis transition for **hierarchy**: opening a nested scene enters from the
  direction it was pushed, Back returns the other way. An eighth of the width under a fade is enough —
  a destination's first frame is expensive and a full-width push loses a visible chunk of its travel to
  it, which reads as a stutter.
- **Roots are a change of subject, not a step: they fade through each other and do not travel.** The
  sideways shift they used to share with nested scenes is a push in miniature; it claimed a level had
  been entered when none had, and it slid the page under furniture that was sliding the other way.
- **Mount the dock once, outside anything that animates the page.** Only the page inside changes. When
  a nested scene takes the dock away, let the space it occupies grow and shrink with it rather than
  appearing in one frame, so the page above never jumps by a dock's worth of pixels. Root state
  (`SaveableStateHolder`, keyed by scene) belongs to the frame too: switching is not leaving.
- The Back pull is the exception that proves it: `whfinPredictiveBack` is applied outside the frame, so
  the gesture moves everything the app is showing, dock included. A page that insets while the
  furniture around it stays put reads as two applications.
- Judge shell motion with the animation clock held still. A 200 ms transition cannot be inspected with a
  screen recorder or a loop of screenshots — `ShellFrameTest` steps the clock and asserts what moved.
- Let a press change shape (`rememberWhfinPressShape`) rather than colour: this palette is quiet by design and a pressed tint reads as noise.
- Pair explicit destination changes with one subtle platform navigation haptic and switches with the platform on/off haptic. Do not duplicate Android's own Back-gesture feedback or vibrate for scrolling.
- Animate position or emphasis only when it explains continuity.
- Avoid staggered decoration, springy finance totals, or transitions that leave partially rendered frames for perceptible time.

## Accessibility and resilience

- Maintain at least 48 dp interactive targets even when visual rows are denser.
- Provide content descriptions for icon-only actions; decorative icons remain null.
- Never encode income/expense/status only by color.
- Test long Georgian/Russian merchant names, large amounts, negative values, IBANs, multiple currencies, and missing descriptions.
- At font scale 1.5, allow wrapping or reflow before truncating essential action labels.
