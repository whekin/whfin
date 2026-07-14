# WHFIN UI design system

## Quiet Ledger

WHFIN uses the visual language **Quiet Ledger / «Тихая тбилисская книга»**. It treats the app as a precise personal ledger maintained automatically for the user: totals lead, rules and alignment explain them, and dense rows carry the daily work.

Material 3 remains the behavioral layer for accessibility, text fields, dialogs, sheets, dates, focus, and system integration. WHFIN owns product appearance above it.

## Architecture

The `:core-ui` Android library owns:

- light/dark color roles and extended semantic colors;
- typography with editorial headings and tabular money figures;
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

The app shell navigates between complete opaque destination scenes. A secondary destination owns its top bar and body in the same layout tree, so a recomposition cannot momentarily combine the previous screen's inset geometry with the next screen's content. Forward/Back use a 220 ms directional shared-axis transition; peer Feed/Accounts switching keeps the dock fixed. Dock items never move: the old and new destinations locally cross-fade their own container/content colors for 140 ms, so selection cannot trail behind an already-switched screen. Each ripple is clipped to the same 48 dp product shape. Explicit dock, open, and in-app Back actions use a subtle system-respecting navigation haptic, while shared switches use platform toggle on/off feedback. Android system Back is not given an extra app haptic because the OS already owns that gesture.

## Visual rules

- Paper and green-black ink form the canvas; bottle green means trust/confirmation, clay means expense/attention, oxide is reserved for destructive/error states.
- Category color stays local to a marker or icon.
- Prefer a section heading plus whitespace/rule before adding a container.
- One coherent group may have one outline or tonal surface; never create card-in-card hierarchies.
- Keep routine rows dense with at least 48 dp interaction targets.
- File names, IBANs, Georgian merchant text, and large amounts must truncate or wrap without taking over the hierarchy.
- Use Material/vector icons, never Unicode characters as icon substitutes.
- Use the shared borderless circular `WhfinBackButton` for every hierarchical Back action. Modal/form dismissal remains a close action and must not masquerade as Back.

## Edge-to-edge and IME

Both activities call `enableEdgeToEdge()` and disable navigation-bar contrast enforcement. The manifest uses `adjustResize` for keyboard activities. The launch theme has explicit day/night and Android 12+ splash backgrounds, so the first system-owned frame matches the phone theme before Compose exists. App Lock startup keeps that neutral themed surface until its DataStore value is known; a disabled or invalid lock state routes directly to the ledger even if the session object's conservative default is locked. On primary Feed and Accounts destinations, `WhfinContextHeader` is the first scroll item and owns the initial status-bar inset; it scrolls away and lets following content continue edge-to-edge. Statistics follows the same rule with its back/title row, because the screen is a long reading surface rather than a modal task. A fixed `WhfinStatusBarProtection` mask covers only the status-bar inset so scrolling ledger content never competes with system icons; it is not an app bar. Other secondary destinations use the shell top bar instead. A screen must never apply both. The shell owns the navigation inset and screens consume scaffold padding. Full-screen composer applies IME padding once at the root, focuses the amount on first creation, explicitly shows the numeric IME, and keeps the save action above it. Editing an existing transaction does not steal focus. Widget quick-entry follows the same first-frame focus contract; neither path uses a timed delay. Full-screen dialogs set `decorFitsSystemWindows = false` and call `WhfinDialogSystemBars`, because a dialog owns a separate window and does not inherit the activity's light/dark system-icon appearance.

## Context headers and balance overview

Feed and Accounts open with a compact scrolling context header: the current GEL total anchors the left side and screen-specific actions occupy the right side. Feed exposes search, type/sort filtering, and settings; Accounts exposes add, Account overview, and settings. The row leaves the viewport with the rest of the ledger, removing decorative title space without permanently consuming it; every icon action remains a 48 dp touch target.

Cash is a currency ledger rather than a user-named container. Add/Edit Cash therefore asks only for the
currency, stores the canonical model name `Cash`, and rejects a second active Cash ledger in that currency.

Feed selection starts with a long press and keeps the ledger spatially stable: summary/search controls give
way to a compact selected-count header, selected rows receive one continuous tonal surface and a check icon,
and subsequent taps toggle rows. Batch status is quiet; batch delete uses the destructive semantic color and
an explicit balance-impact confirmation. A transfer or conversion is one visible selection and always updates
or deletes both persisted legs.

Account overview explains the current balance rather than pretending to be analytics. Assets, liabilities, available funds, reserve, and source distribution are calculated only in GEL. Other currencies remain separate native amounts until WHFIN has exchange rates with provenance and timestamps; they are never mixed into the GEL net worth or distribution percentages.

Monthly Statistics opens from the Feed's month summary rather than adding another dock destination or header icon. It combines a selected-month net result, rolling 1/3/6/12-month category distribution, and a twelve-month bar trend. Selecting a category highlights the row and changes the year graph; the all-expenses scope remains one tap away. Each trend month has a selectable 48 dp target and updates the amount/comparison below the chart. “View transactions” opens a focused ledger filtered by that trend month and the active category, while Back restores the unchanged Statistics context. The drill-down queries the full month rather than Feed's bounded recent-history window. Transfers and debt allocations are excluded. Linked automatic conversion funding is attributed to the purchase category in GEL, while unconverted foreign expenses stay in native-currency rows. System Unaccounted adjustments are visible in a separate section but excluded from income, expenses, category shares, and trends.

## Widget loading contract

Glance widget metadata uses `@layout/glance_default_loading_layout` for `initialLayout`. Picker previews may mirror the WHFIN composition, but must remain valid `RemoteViews`: only supported layout/view classes, vector assets for icon actions, and no generic `<View>` dividers or Unicode icon substitutes. This prevents launchers such as ColorOS from briefly showing “Error loading widget” before the first Glance composition arrives.

Widget appearance defaults to **System colors** on Android 12 and newer. Glance's wallpaper-derived Material roles own `widgetBackground`, `onSurface`, `onSurfaceVariant`, `outline`, `primaryContainer`, and `onPrimaryContainer`; WHFIN never samples wallpaper pixels or invents translucent overlays. These paired roles keep labels and the add action readable while following both wallpaper tone and system light/dark mode. Settings → Appearance can switch to the Quiet Ledger day/night schemes. Pre-Android 12 devices use that same WHFIN fallback because platform dynamic colors are unavailable. Changing the preference updates every installed 1–4-cell receiver without recreating the widget. Native preview layouts use a neutral day/night approximation. ColorOS uses the required static branded `previewImage` instead, because a bitmap catalog preview cannot react to wallpaper changes; runtime color remains authoritative.

In multi-cell variants the add action retains a 48 dp interactive slot, but its colored surface is inset to 40 dp. The visible action must not merge with the full-height widget bar; the 4 dp surround keeps it distinct without reducing accessibility. The standalone 1-cell variant remains a 46 dp filled action.

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

Monthly Statistics was rendered in RU and EN on the same Pixel 9 Pro AVD: populated light/dark, font scale 1.5 at the top and final scroll position, category drill-down, system back, and a scrolling title/status-bar boundary. The host suite covers range/category interaction plus transfers, debts, adjustments, FX-funded purchases, and unsupported native-currency expenses.

The selectable year trend and its filtered ledger were additionally exercised end to end: changing the selected month updates the exact amount and comparison, zero months disable drill-down, and non-zero months open a full-month/category query. The filtered row opens transaction details; both the in-app Back action and Android system Back restore the same Statistics month, category, selected bar, and scroll context. Final renders were checked in light and dark mode, at font scale 1.5, and after an in-place EN→RU locale change.

The dismissible Feed SMS proposal was rendered in dark mode and in light mode at font scale 1.5. Its close action was exercised through the accessibility node, the app was force-stopped and relaunched to verify persistent dismissal, and Settings was reopened to confirm that SMS permission remains available there.

The Settings SMS switch was rendered enabled, disabled, and permission-blocked; its state persisted through process restart, and the disabled state removed the Feed proposal. Re-enabling launched the Android permission dialog, while disabling after permission grant kept the OS grant intact. Final Settings renders were inspected in RU light mode, EN dark mode, and EN at font scale 1.5.

Backup & export uses one primary export action and an outlined destructive restore action so the rare
replacement flow cannot compete visually with routine backup. The screen permanently warns that JSON
is unencrypted; restore requires a separate Material confirmation dialog. It was rendered in RU dark
mode and light mode at font scale 1.5, including the SAF picker and exported-success state. At 1.5 the
full explanatory copy remains available by scrolling and all actions retain 48 dp minimum targets.

The shell transition regression was checked on the Pixel AVD by repeatedly opening Settings, returning to Feed, entering Bank statements, and returning through both app and Android Back. Settings stayed a single coherent top-bar/body surface in dark mode at font scale 1.0 and light mode at 1.5. The nested Statistics → filtered transactions transition was also exercised at font scale 1.5; Back kept the underlying Statistics composition alive and restored the same chart position instead of jumping to the top.

The widget appearance contract was rendered on a wiped Pixel 9 Pro API 36.1 AVD. All four picker previews inflated in light mode, the 3-cell runtime widget followed the blue wallpaper palette, switched immediately to the WHFIN light palette from Settings, returned to System colors, and then adopted the system dark palette without losing contrast. The settings row and update path were also covered by Robolectric preferences/Compose tests.

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
the destination content changes immediately, while only the two stationary dock items cross-fade for 140 ms;
pressed states stay clipped to their item geometry.
