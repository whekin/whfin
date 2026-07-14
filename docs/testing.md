# Android testing strategy

## Baseline analysis

- Dependency injection: lightweight application-owned dependencies (`WhfinApp.db`); no DI framework. UI render tests isolate stateless content and callbacks rather than replacing the product data architecture solely for tests.
- Local tests: JUnit 4; parser/importer/categorization coverage already existed.
- UI: fully Jetpack Compose, with Glance for widgets.
- UI behavior: Compose UI test APIs with Robolectric for fast host tests.
- Database: Room instrumented tests use an in-memory database and the device SQLite engine.
- Database migrations: committed Room schema assets drive `MigrationTestHelper`; tests validate v1→v2,
  v2→v3 SMS diagnostics creation/data preservation, and the complete earliest→current schema path.
- Portable backup: instrumented SQLite tests verify the explicit table/column allowlist against the
  current Room schema, every-table deterministic export→restore→export, malformed JSON rejection and
  future-format rejection without changing current data.
- End-to-end/system UI: AndroidX Test + UI Automator on a disposable emulator.
- Screenshot regression: official Compose Preview Screenshot Testing (Layoutlib) in `:core-ui`.
- Coverage: JaCoCo applied to modules containing tests.

Hilt was intentionally not introduced during this UI refactor: migrating the application/database ownership and every ViewModel would be a business-architecture change with no benefit to the stateless design-system render harness. If repositories later gain network or platform dependencies that require runtime replacement, introduce constructor-injected interfaces first and reassess Hilt then.

## Commands

```bash
./gradlew :app:testDebugUnitTest
# Run only while a disposable emulator is the sole/explicit target.
./gradlew :app:connectedDebugAndroidTest
./gradlew :core-ui:updateDebugScreenshotTest
./gradlew :core-ui:validateDebugScreenshotTest
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
```

When a personal phone is also connected, do not invoke the aggregate connected task. Install and run
the migration suite only on the disposable emulator serial:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.whekin.whfin.data.db.WhfinDatabaseMigrationTest \
  dev.whekin.whfin.test/androidx.test.runner.AndroidJUnitRunner
```

Private Credo integration checks read only explicitly configured files from `WHFIN_REAL_STATEMENT`
or `WHFIN_REAL_STATEMENTS_DIR` and skip when private fixtures are unavailable. Keep those fixtures
outside the repository; assertions intentionally avoid committing account metadata or exact statistics.

## Physical-device data safety

The user's phone contains real WHFIN data. It is valid for `adb install -r`/`android run` upgrades, launching the app, layout inspection, screenshots, and manual flow checks. Do not run Gradle connected/instrumentation tests against it: the Android test deployment flow may uninstall or clear the target package even when the tests themselves use an in-memory database.

Run `connectedDebugAndroidTest` only when a disposable emulator is the sole online device. When a
personal phone is connected too, build the test APK and invoke the selected suite with `adb -s` as
shown above. Never use `adb uninstall`, `pm clear`, destructive migration testing, or test orchestrators
on the personal phone. If a physical-device test is ever necessary, obtain explicit approval and create
and verify a restorable app-data backup before starting.

## Test locations

- Local business tests: `app/src/test/java`
- Compose/Robolectric behavior tests: `app/src/test/java/dev/whekin/whfin/ui`
- Glance/RemoteViews resource tests: `app/src/test/java/dev/whekin/whfin/widget`
- Room and journey tests: `app/src/androidTest/java`
- Backup codec/Room integration: `app/src/androidTest/java/dev/whekin/whfin/data/backup`
- App Lock Keystore verification: `app/src/androidTest/java/dev/whekin/whfin/data/security`
- Screenshot preview tests: `core-ui/src/screenshotTest/kotlin`
- Screenshot references: `core-ui/src/screenshotTestDebug/reference`

## Required UI coverage

Screen previews cover light, dark, and font scale 1.5 for Feed, Accounts, composer, Settings, Statistics, analytics-filtered transactions, and statement result states. The design-system screenshot suite validates the shared components in those configurations, including the selectable monthly chart. Device journeys remain intentionally few and cover launch/navigation plus database integration; detailed behavior stays in fast local tests.

## Last verified run (2026-07-14)

- `:app:testDebugUnitTest`: passed, including explicitly configured private Credo XLSX fixtures.
- Widget picker previews: all four XML layouts apply successfully as `RemoteViews`; 4×1 addition was visually checked from picker → loading → populated on Pixel 9 Pro API 36.1.
- `:core-ui:validateDebugScreenshotTest`: passed for light, dark, and font scale 1.5 references.
- `:app:connectedDebugAndroidTest`: 2/2 passed on Pixel 9 Pro API 36.1 (Room database and UI Automator journey).
- `:app:assembleDebug`: passed.
- Room migration suite: v1 row preservation/debt-table migration and earliest→current schema validation
  both passed against real SQLite on disposable Pixel 9 Pro API 36.1. The Room 2.8.4 test bundle requires
  serialization 1.8.1, so that compatibility override is deliberately limited to Android-test configurations.
- Statistics trend interaction and filtered transaction navigation: host tests passed; real Pixel 9 Pro API 36.1 render checked in light/dark, font scale 1.5, and RU. Transaction details and both Back paths were exercised manually.
- SMS import toggle: DataStore default/persistence and Compose switch/action tests passed. Disposable Pixel render covered on/off, permission-required, process restart, RU light, EN dark, and EN font scale 1.5; the physical phone remained upgrade/manual-QA only.
- Shell navigation regression: Settings and Bank statements were exercised repeatedly in dark 1.0 and light 1.5 renders; Statistics → filtered transactions → Back retained the chart scroll context. The Settings Compose test injects a fake `HapticFeedback` and asserts the platform toggle-off event.
- JSON backup: all four codec/integration tests passed through an explicit `emulator-5554` instrumented
  run. The Settings route, idle/working/success states and restore confirmation passed host Compose tests.
  A real SAF export produced a readable version-1 JSON file with 25 emulator records; the screen and
  system picker were rendered in dark 1.0 and light 1.5 with legible edge-to-edge system bars.
- SAF statement picker regression: the app pins `androidx.fragment:fragment` 1.8.9 because Biometric
  1.1.0 otherwise resolves Fragment 1.2.5, whose `FragmentActivity` rejects modern Activity Result request
  codes above 16 bits. On disposable Pixel API 36.1, Upload statement opened DocumentsUI and returned to
  the statements screen without an `AndroidRuntime` fatal exception.
- App Lock: host tests cover timeout/session behavior, first-code setup, matching confirmation, keypad
  callbacks and biometric preference persistence. Two Android Keystore tests passed through an explicit
  `emulator-5554` instrumented run: correct/incorrect verification and five-attempt/30-second lockout.
  Real Pixel rendering covered EN dark at font scales 1.0 and 1.5, cold start, wrong/correct code,
  protected blank screenshots and unlocked navigation. A Pixel fingerprint was enrolled: the strong-only
  system prompt succeeded with an emulator touch, and “Use WHFIN code” returned to the custom keypad
  without exposing the Android device-PIN field. With Immediate lock active, the Glance `+`
  action opened Quick expense directly without biometric/PIN and without exposing balances/history.
- SMS diagnostics: parser classification, permission disclosure and account-link UI pass host tests.
  Eleven address-scoped instrumented tests passed on `emulator-5554`: v1→v2→v3 migrations, diagnostic
  schema privacy, unknown-card repair, ambiguous-account outcome, backup compatibility and exclusion.
  A sanitized Credo SMS injected through `adb emu sms send` reached the real receiver and rendered
  `Choose an account`; the screen, empty dry-run and unavailable mapping sheet were checked in EN light,
  EN dark and font scale 1.5. The physical phone was not used for instrumentation or history import.
- Launch/quick-entry/dock polish: a dark cold start was captured before Compose could paint and showed the
  night splash rather than a white frame. The widget action produced a focused amount semantics node and a
  visible numeric IME on its first rendered state. Rapid Feed→Accounts captures verified stationary local
  selection surfaces, a 140 ms color cross-fade, and shape-clipped 48 dp press targets. Debug, release,
  host tests, and screenshot validation all passed after the change.
- Widget appearance: `UiPreferencesTest` verifies the System default and persistent WHFIN override;
  `WidgetColorModeTest` verifies the Android 12 dynamic-color gate; `SettingsScreenTest` exercises the
  visible switch; `WidgetPreviewLayoutTest` still inflates all four picker layouts as `RemoteViews`.
  A wiped Pixel 9 Pro API 36.1 AVD rendered the 1–4-cell picker previews and a live 3-cell widget in
  wallpaper-derived light, WHFIN light, and wallpaper-derived dark palettes. Text and the add action use
  paired Material roles rather than hand-picked foreground colors.
