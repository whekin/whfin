# WHFIN UI design system

## Quiet Ledger

WHFIN uses the visual language **Quiet Ledger / «Тихая тбилисская книга»**. It treats the app as a precise personal ledger maintained automatically for the user: totals lead, rules and alignment explain them, and dense rows carry the daily work.

Material 3 remains the behavioral layer for accessibility, text fields, dialogs, sheets, dates, focus, and system integration. WHFIN owns product appearance above it.

## Architecture

The `:core-ui` Android library owns:

- light/dark color roles and extended semantic colors;
- bundled Noto Serif typography for deterministic editorial headings and tabular money figures;
- spacing, size, shape, and motion tokens;
- primary/secondary/quiet/destructive actions, including a lower-emphasis outlined destructive variant;
- contextual metric headers, icon actions, filters, ledger groups and rows;
- same-unit distribution bars used by balance explanations;
- notices and loading/empty/error/unavailable state panes;
- shared IME-safe form sheets;
- design-system previews and screenshot-test references.

The `:app` module owns domain formatting, localized text, Room entities, navigation state, category adapters, feature state, and callbacks. Feature screens may use Material primitives for behavior or incidental layout, but recurring product appearance belongs in `:core-ui`.

The reusable agent workflow and full visual guidance live in [`.agents/skills/whfin-ui-design`](../.agents/skills/whfin-ui-design/SKILL.md).

Permission prompts outside Settings are optional proposals, not gates. The Feed SMS notice has a 48 dp close action and persists “do not show again” in app preferences. Settings owns a separate persistent SMS-import switch: turning it off leaves the Android permission unchanged but gates the broadcast receiver before message extraction, parsing, or transaction import; turning it on requests permission only when necessary. Existing installs default on so an upgrade cannot silently disable automation.

The app shell navigates between complete opaque destination scenes. A secondary destination owns its top bar and body in the same layout tree, so a recomposition cannot momentarily combine the previous screen's inset geometry with the next screen's content. Forward/Back use a 220 ms directional shared-axis transition; peer Feed/Accounts switching keeps the dock fixed. The grounded, full-width dock aligns its top rule to the 20 dp ledger rail. Its outlined 22 dp destination glyphs never move, while a short 24 dp rule and color cross-fade identify the selected peer without a pill. Labels remain one line at font scale 1.5. The separate center action is a flat, filled 48 dp primary circle, lifted only 4 dp inside a reserved 64 dp slot; it is neither a third navigation state nor a floating FAB, and uses no outline, notch, or shadow. It opens the transaction composer and returns to Feed if necessary. Add requests are consumed after delivery, so returning from Accounts cannot replay an earlier request and reopen Expense. The action remains contained inside the dock rather than obscuring ledger rows. Each transient pressed state is clipped to the same product shape. Explicit dock, open, and in-app Back actions use a subtle system-respecting navigation haptic, while shared switches use platform toggle on/off feedback. Android system Back is not given an extra app haptic because the OS already owns that gesture.

## Visual rules

- Paper and green-black ink form the canvas; bottle green means trust/confirmation, clay means expense/attention, oxide is reserved for destructive/error states.
- Dark mode is dark paper, not neutral black: canvas and containers carry a warm olive-umber cast so cream
  ink and clay stay in the same family as the light theme.
- Category color stays local to a marker or icon. That marker is a circle with a quiet tonal fill and no
  border — the same shape in ledger rows, statistics rows, and composer tiles.
- Category icons are **outlined**. Filled glyphs made a colored blob the loudest element of every row and
  read as stock Material under a custom visual language; contours belong to the same family as hairline
  rules. Icon names in the database are unchanged, so the set can be swapped without a migration.
- Money is set in the editorial serif with tabular figures through `WhfinAmount`, never in the row's sans.
  The numeric column is the heart of a ledger and must have its own voice; the currency symbol is set
  smaller and quieter so digits align into a column. `Device font` switches these figures too, because
  the family is read from the theme.
- Caps with tracking is a landmark (a day, a screen section), not a label. Inside a block, field and
  subsection labels use `WhfinFieldLabel` in sentence case.
- `WhfinTotalRule` — the accounting double rule — closes a result. Ordinary separators stay single.
- Prefer a section heading plus whitespace/rule before adding a container.
- One coherent group may have one outline or tonal surface; never create card-in-card hierarchies.
- Keep routine rows dense with at least 48 dp interaction targets.
- File names, IBANs, Georgian merchant text, and large amounts must truncate or wrap without taking over the hierarchy.
- Use Material/vector icons, never Unicode characters as icon substitutes.
- Use the shared borderless circular `WhfinBackButton` for every hierarchical Back action. Modal/form dismissal remains a close action and must not masquerade as Back.

## Edge-to-edge and IME

Both activities call `enableEdgeToEdge()` and disable navigation-bar contrast enforcement. The manifest uses `adjustResize` for keyboard activities. The launch theme has explicit day/night and Android 12+ splash backgrounds, so the first system-owned frame matches the phone theme before Compose exists. App Lock startup keeps that neutral themed surface until its DataStore value is known; a disabled or invalid lock state routes directly to the ledger even if the session object's conservative default is locked. On primary Feed and Accounts destinations, `WhfinContextHeader` is the destination `Scaffold` top bar and owns the status-bar inset. Its metric aligns to the 20 dp content rail and uses `WhfinAmount`, including the quieter currency mark; screen actions share one low-tonal rail while retaining individual 48 dp targets. It uses Material 3 `enterAlways`: it leaves on downward scrolling and returns on the first upward gesture, without requiring a return to the list start. Selection mode swaps in the same product bar without a scroll behavior, keeping its count and batch actions pinned; Android Back exits selection before leaving the destination. Statistics keeps its back/title row inside the list because the screen is a long reading surface rather than a primary command surface, and uses the exact-status-inset `WhfinStatusBarProtection` mask. A screen must never apply both inset owners. The shell owns the navigation inset and screens consume scaffold padding. Full-screen composer applies IME padding once at the root, focuses the amount on first creation, explicitly shows the numeric IME, and keeps the save action above it. Editing an existing transaction does not steal focus. Widget quick-entry follows the same first-frame focus contract; neither path uses a timed delay. Full-screen dialogs set `decorFitsSystemWindows = false` and call `WhfinDialogSystemBars`, because a dialog owns a separate window and does not inherit the activity's light/dark system-icon appearance.

## Context headers and balance overview

Feed and Accounts open with a compact context header: the current GEL total anchors the left side and screen-specific actions occupy the right side. Feed exposes only search and one filter action; Settings remains discoverable from Accounts rather than competing with ledger work. The filter owns transaction type, multi-category selection and sort order in one fully expanded sheet, with draft changes applied explicitly. Quick filter chips are intentionally absent from the reading surface. Accounts exposes add, Account overview, and settings. The bar leaves the viewport while scrolling down and returns immediately on upward intent. During transaction selection it stays fixed; selecting only pending rows replaces the generic status action with a direct Confirm action. Every icon action remains a 48 dp touch target.

The Feed month block leads with the month's own result: a signed GEL net figure, then income and expenses
as signed context values below it. Directional arrows were removed because "down" read both as *money
arrived* and as *value fell*; a sign on a tabular figure carries the same meaning without that ambiguity.
Day headers print a spend total only when the day actually has expenses, and fall back to a single foreign
currency total instead of a meaningless `0.00 ₾`. They also carry the weekday (`SAT, 26 JUL`), because
transactions are remembered as "on Saturday"; the year appears only for past years.

Appearance separates brightness, palette, and editorial typography. System/Light/Dark is persisted in
DataStore and can override device brightness, while System colors independently applies Android 12+
wallpaper-derived Material roles to the app canvas, surfaces and semantic accents. System colors defaults
to **off**: the Quiet Ledger palette is product identity and must be what a first run shows, so
wallpaper-derived color stays an explicit opt-in. WHFIN bundles Noto
Serif for consistent screen titles and large totals across OEMs; the independent Device font switch
replaces those roles with Android's default family for people who prefer their system typography. Body,
row, form, and control text remains neutral system sans in both modes. Edge-to-edge system icon contrast
follows the effective app brightness. The widget always uses the system Glance palette and exposes no
competing WHFIN palette override.

Accounts and Account overview keep Room loading distinct from an empty ledger. Accounts publishes its
account and debt rows as one ready snapshot; until that snapshot exists, the context total is a dash and the
body is a neutral loading state. `No accounts yet` is shown only after Room has confirmed an empty result, so
a populated database cannot flash the onboarding state during navigation or cold collection.

Cash follows the same source → account → currency grammar as a bank. Its name is optional (`Cash` remains
the fallback), so a ledger may become “Pocket money” or “Rainy day”; it may also be Everyday or Reserve.
WHFIN still rejects a second active Cash ledger in the same currency.

Currency rows only open their own transaction ledger. Container-wide edit, bank mapping, balance actions,
and destructive removal live behind the account header settings action. The source header opens a full-screen
overview: bank/Cash details, account aliases, IBAN/card metadata, currencies, and statement coverage. Currency
codes are not repeated in the account header because the ledger rows immediately below already own them.

The Accounts list carries only what distinguishes a row. A source with exactly one account container has no
repeated container row: the source heading already names it, and every currency row still opens Account
activity. A container row appears when a source holds several accounts, and identifies them by the IBAN tail
rather than the full number — the full IBAN belongs to bank details and the source overview. Card masks are
account-wide, so they never repeat inside each currency row; a currency row shows only its purpose
(Reserve/Deposit/Goal) or its own alias. The `N accounts` counter appears only when the count is greater
than one.

Debt creation remains a scrollable WHFIN form. Direction and currency choices may scroll horizontally, but
Money movement is a vertical ledger choice (`No movement` plus every matching account), so long aliases and
font scale never wrap inside tiny chips or hide sources off-screen.

About keeps authorship actionable: the `whekin` row opens the public GitHub profile. Five taps on Version
reveal a harmless in-memory Quiet Ledger note; this never exposes debug controls or private data.

Feed selection starts with a long press and keeps the ledger spatially stable: summary/search controls give
way to a compact selected-count header, selected rows receive one continuous tonal surface and a check icon,
and subsequent taps toggle rows. Batch status is quiet; batch delete uses the destructive semantic color and
an explicit balance-impact confirmation. A transfer or conversion is one visible selection and always updates
or deletes both persisted legs.

Feed filtering stays a short, intrinsic-height sheet: type and sort use compact controls, while category
selection shows the three most-used eligible categories plus More. The ranking comes from real transaction
usage and keeps already-selected categories visible. More replaces the sheet with a dedicated full-screen,
lazy category list; the ordinary sheet never composes every category or reserves nearly the full display.

Transaction details prioritize the amount and four routine facts. Status and category become the action when
they are editable instead of being repeated below as separate rows. Bank/source metadata is collapsed by
default, and infrequent edit/debt/delete actions share one horizontally resilient 48 dp action rail. Editable
summary rows do not add trailing pencil icons that disturb the value column; they close with one quiet
chevron after the value, because a row with no affordance at all was read as a static database record. The
sheet uses lazy content so long bank descriptions do not turn scrolling into a full-column remeasure.

Confirming a pending draft is the most repeated decision in the two-layer SMS/statement model, so it is a
single filled action at the head of that rail rather than a trip through the status sheet. It is the only
filled action in the sheet and appears only while the transaction is actually pending; the status row still
opens the full picker for the remaining transitions.

The composer keeps exactly one control per decision. Currency next to the amount is a label, not a second
picker: source → currency is chosen in the money-source row, and the previous amount-side control silently
cycled the ledgers of the same source. The empty amount uses a dimmed hint rather than a semantic accent, so
`0.00` never reads as income on an expense screen. Category is one mechanism too — the ranked suggestion row
with More, using the same `CategorySuggester` amount/currency ranking as widget quick entry, instead of a
separate "choose category" row stacked above a "recent" row. Creating a category inside the full-screen
picker is a compact header action; the wide bottom button it replaced looked like the screen's primary action
and pushed a short grid into half a screen of emptiness.

Account activity is the single owner of one currency ledger and its account-container actions. Edit account,
bank details, balance adjustment and delete live beside the balance in a wrapping two-column action area;
Accounts no longer opens an intermediate settings sheet. The activity ledger reuses the same transaction
details, category/status editing, manual composer, debt and delete paths as Feed. Small sheets use
`skipPartiallyExpanded`, so their primary actions are reachable on first presentation without an extra swipe.

Account overview explains the current balance rather than pretending to be analytics. Assets, liabilities, available funds, reserve, and source distribution are calculated only in GEL. Other currencies remain separate native amounts until WHFIN has exchange rates with provenance and timestamps; they are never mixed into the GEL net worth or distribution percentages.

Monthly Statistics opens from the Feed's month summary rather than adding another dock destination or header icon. It combines a selected-month net result, rolling 1/3/6/12-month category distribution, and a twelve-month bar trend. Selecting a category highlights the row and changes the year graph; the all-expenses scope remains one tap away. Each trend month has a selectable 48 dp target and updates the amount/comparison below the chart. A bar fills
most of its slot so the chart reads as a chart rather than a row of tally marks, the selected month widens and
takes a tonal slot, and a month with no expenses draws a quiet baseline rule instead of a near-invisible stub.
The chart carries no instructional caption: selection and swipe are evident from the emphasized month and the
amount that follows it. “View transactions” opens a focused ledger filtered by that trend month and the active category, while Back restores the unchanged Statistics context. The drill-down queries the full month rather than Feed's bounded recent-history window. Transfers and debt allocations are excluded. Linked automatic conversion funding is attributed to the purchase category in GEL, while unconverted foreign expenses stay in native-currency rows. System Unaccounted adjustments are visible in a separate section but excluded from income, expenses, category shares, and trends.

## Widget loading contract

Glance widget metadata uses `@layout/glance_default_loading_layout` for `initialLayout`. Picker previews may mirror the WHFIN composition, but must remain valid `RemoteViews`: only supported layout/view classes, vector assets for icon actions, and no generic `<View>` dividers or Unicode icon substitutes. This prevents launchers such as ColorOS from briefly showing “Error loading widget” before the first Glance composition arrives.

Widget appearance always uses **System colors**. Glance's wallpaper-derived Material roles own `widgetBackground`, `onSurface`, `onSurfaceVariant`, `outline`, `primaryContainer`, and `onPrimaryContainer`; WHFIN never samples wallpaper pixels or invents translucent overlays. These paired roles keep labels and the add action readable while following both wallpaper tone and system light/dark mode. The app has no widget palette override. Native preview layouts use a neutral day/night approximation. ColorOS uses the required static branded `previewImage` instead, because a bitmap catalog preview cannot react to wallpaper changes; runtime color remains authoritative.

In multi-cell variants the add action retains a 48 dp interactive slot, but its colored surface is inset to a 40 dp circle. The visible action must not merge with the full-height widget bar; the 4 dp surround keeps it distinct without reducing accessibility. The standalone 1-cell variant remains a 46 dp filled circle. Quick expense owns its amount keypad so OEM numeric keyboard toolbars cannot consume the compact entry surface; the system text IME is still used for the optional description. Calculator operations live in one six-action rail above a strict three-column numeric grid; the final row is decimal, zero, and Backspace. Do not reproduce that layout with nested weighted rows because their column widths drift.

## Verification matrix

Every material UI change should be checked on a real render in:

- light and dark mode;
- font scale 1.0 and 1.5;
- keyboard closed/open with the final field focused;
- populated and empty content, plus relevant loading/error/unavailable states;
- compact phone height, including the last scroll item and pinned actions.

Host-side screenshot references are stored under `core-ui/src/screenshotTestDebug/reference`. Real-device baseline/final captures are kept under ignored `local/ui-baseline` and `local/ui-final` during development.

## Verified render (2026-07-14)

The complete shell and primary journeys were rendered on a Pixel 9 Pro API 36.1 AVD with private Credo fixtures. Feed, Accounts, Settings, statement import/history, transaction details, expense/income/transfer/debt composers, and the debt ledger were inspected in light mode. Feed and composer were checked in dark mode; Feed and composer were also checked at font scale 1.5. The scrolling Feed and Accounts context headers were verified both at rest and fully outside the viewport; the status-inset protection remained legible in light, dark, and font scale 1.5 renders. With font scale 1.5, both the numeric amount field and the final Note field were focused with the IME open: the scrolling content, focused field, pinned Save action, status bar, and navigation bar remained visible and legible.

The compact Feed/Accounts context headers and Account overview were additionally rendered with synthetic multi-account QA data on the same disposable AVD. Account overview was checked populated and empty in light mode, populated in dark mode, and populated at font scale 1.5. Search was checked with automatic focus and the text IME open. The final APK was installed on the physical OnePlus only as an in-place upgrade; no connected tests or data-clearing commands were run there.

The Feed → Accounts transition was recorded frame-by-frame at font scale 1.5 with populated Room data.
It shows a coherent loading surface followed directly by the full Cash/deposit hierarchy; the previous false
`No accounts yet` frame and false zero balance are absent.

Monthly Statistics was rendered in RU and EN on the same Pixel 9 Pro AVD: populated light/dark, font scale 1.5 at the top and final scroll position, category drill-down, system back, and a scrolling title/status-bar boundary. The host suite covers range/category interaction plus transfers, debts, adjustments, FX-funded purchases, and unsupported native-currency expenses.

The selectable year trend and its filtered ledger were additionally exercised end to end: changing the selected month updates the exact amount and comparison, zero months disable drill-down, and non-zero months open a full-month/category query. The filtered row opens transaction details; both the in-app Back action and Android system Back restore the same Statistics month, category, selected bar, and scroll context. Final renders were checked in light and dark mode, at font scale 1.5, and after an in-place EN→RU locale change.

The dismissible Feed SMS proposal was rendered in dark mode and in light mode at font scale 1.5. Its close action was exercised through the accessibility node, the app was force-stopped and relaunched to verify persistent dismissal, and Settings was reopened to confirm that SMS permission remains available there.

The Settings SMS switch was rendered enabled, disabled, and permission-blocked; its state persisted through process restart, and the disabled state removed the Feed proposal. Re-enabling launched the Android permission dialog, while disabling after permission grant kept the OS grant intact. Final Settings renders were inspected in RU light mode, EN dark mode, and EN at font scale 1.5.

The editorial typography contract was rendered as paired Compose screenshot references: bundled WHFIN
serif and Android device font. The Appearance switch defaults to the bundled family, updates the whole
composition immediately, persists in DataStore, and was checked in Settings in light/dark and at font
scale 1.5 before an in-place OnePlus upgrade.

Backup & export uses one primary export action and an outlined destructive restore action so the rare
replacement flow cannot compete visually with routine backup. The screen permanently warns that JSON
is unencrypted; restore requires a separate Material confirmation dialog. It was rendered in RU dark
mode and light mode at font scale 1.5, including the SAF picker and exported-success state. At 1.5 the
full explanatory copy remains available by scrolling and all actions retain 48 dp minimum targets.

The shell transition regression was checked on the Pixel AVD by repeatedly opening Settings, returning to Feed, entering Bank statements, and returning through both app and Android Back. Settings stayed a single coherent top-bar/body surface in dark mode at font scale 1.0 and light mode at 1.5. The nested Statistics → filtered transactions transition was also exercised at font scale 1.5; Back kept the underlying Statistics composition alive and restored the same chart position instead of jumping to the top.

Secure four-digit entry uses one WHFIN code component in `:core-ui`: four quiet dots above a 3×4 numeric keypad with circular 68 dp targets, clipped pressed states, keyboard-tap haptics and a real Backspace icon. App Lock may place biometrics in the empty lower-left slot; Credo OTP keeps it empty, requires explicit Confirm, clears on Resend or when the challenge leaves the screen, and never opens the system IME.

The widget appearance contract was rendered on a wiped Pixel 9 Pro API 36.1 AVD. All four picker previews inflated in light mode, and the 3-cell runtime widget followed the wallpaper palette and system dark mode without losing contrast.

## App Lock

App Lock uses the same Quiet Ledger palette but removes all financial context. The WHFIN code is entered
through four explicit dots and a 3×4 circular keypad with 68 dp targets; backspace and biometric entry use
vector icons rather than glyphs. Code creation and confirmation reuse the same geometry, while the locked
gate is composed instead of the financial app tree. Strong biometric remains Android-owned system UI and
returns to the WHFIN keypad when cancelled. `FLAG_SECURE` makes the recent-apps card and locked-window
screenshots blank. Widget quick-entry is deliberately outside this gate: it is a fast capture surface and
does not render existing balances or history.

The settings, code-creation keypad and locked semantics tree were verified in EN dark at font scale 1.0;
the keypad also fit at 1.5 with system bars visible. Wrong and correct code states, process-death cold start,
five-attempt storage behavior and direct widget quick-entry were exercised on the disposable Pixel AVD.
The enrolled-fingerprint prompt was exercised in both directions: success unlocks the ledger, while its
“Use WHFIN code” action reveals the custom keypad without ever displaying a device-credential field.

The dark cold-launch path was captured immediately after process start and remained bottle-green from the
system splash through the first Compose frame. Widget `+` opened quick-entry with the amount field focused
and the numeric IME already visible. Feed/Accounts switching was captured in rapid intermediate frames:
the destination content changes immediately, while only the color emphasis of the two stationary dock items
cross-fades for 140 ms; there is no persistent selected-item fill, and pressed states stay clipped to their
item geometry.

## Demo and developer modes

Demo mode is a public Settings control, not a hidden debug shortcut. It opens the same product UI against
an isolated `whfin-demo.db` populated with rich synthetic data; the Feed and Accounts context header says
“Demo data” so the mode cannot be mistaken for a personal ledger. The Settings notice explains the boundary,
offers a confirmed demo-only reset, and marks live Credo/SMS surfaces unavailable. Backup/restore is also
disabled there because its personal-data meaning would otherwise be ambiguous. Widgets, incoming SMS, and
quick entry deliberately keep using the personal database. Changing the mode restarts the foreground task:
this intentionally discards Activity-scoped ViewModels so no screen can continue observing the database
that was active before the switch. The restart preserves the already unlocked foreground App Lock session.

Five taps on Version still reveal the Quiet Ledger Easter egg. Its switch now persists a device-local
Developer mode and reveals an experimental section in Settings. Developer mode is distinct from public Demo
mode: it may expose diagnostics later, but it never selects a data source or relaxes privacy boundaries.

## Simplification pass (2026-07-28)

Rendered on a disposable Pixel 9 Pro API 36.1 AVD in demo mode. Verified in dark EN, light EN, and light
RU at font scale 1.5: Feed month result, day headers, ledger rows with circular category markers, pending
transaction details with one-tap Confirm and chevron affordances, the composer with a single currency
control and one category mechanism, the full-screen category picker with header creation, Accounts with
collapsed single-account sources, Account overview, and the Statistics year trend. One-tap Confirm was
exercised end to end: the pending row lost its `Pending` annotation without a second sheet.

`:app:testDebugUnitTest` passes, including two new details-sheet tests (confirm in one tap, no Confirm
action once confirmed). The monthly-chart screenshot references were regenerated for the wider bars and
zero-month baseline in light, dark, and font scale 1.5.

## Aesthetic pass (2026-07-29)

Five moves, in order of visual weight:

1. **Money got its own voice.** `WhfinAmount` sets every figure in the bundled editorial serif with
   tabular figures and quietens the currency symbol (0.82 em, 80% alpha). Before this, an amount was the
   same sans as the merchant name and differed only by weight, so the ledger had no numeric column.
   Because the serif has one weight, amounts never request SemiBold — presence comes from size and color.
2. **Category icons are outlined.** The 24 filled Material glyphs were the loudest, most stock-looking
   element on every screen.
3. **Dark mode warmed up.** The canvas moved from `#101612` to `#14160F` with olive-umber containers.
4. **Caps became a landmark again.** `WhfinFieldLabel` carries field/subsection labels in sentence case;
   `ACTIONS` above the details rail was dropped entirely.
5. **`WhfinTotalRule`** closes the Feed month block and the Statistics month result, and day headers now
   name the weekday.

Verified on a disposable Pixel 9 Pro API 36.1 AVD in demo mode: Feed, Accounts, Statistics in light and
dark EN, plus Accounts in light RU at font scale 1.5 — serif digits are wider than sans, and neither the
two-column summary nor the currency rows truncate. `:app:testDebugUnitTest` passes; five dark screenshot
references were regenerated for the warm palette and the chart.
