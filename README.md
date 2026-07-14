# WHFIN

WHFIN is a native Android personal-finance tracker built around a simple idea: capture transactions
quickly, reconcile them against authoritative bank statements, and keep the user's data local and
portable.

The project is an independent prototype by **whekin**. It currently focuses on GEL accounts and Credo
Bank statement/SMS formats, while its data model is designed for multiple accounts, currencies,
transfers, conversions, debts, people, and watch-only crypto assets.

## Highlights

- Native Kotlin and Jetpack Compose UI with the WHFIN “Quiet Ledger” design system.
- Room data model with explicit migrations and versioned JSON backup/restore through Android SAF.
- XLSX statement import, deduplication, balance-chain validation, and reconciliation with pending SMS.
- Manual expense, income, transfer, conversion, debt, and balance-adjustment flows.
- Monthly category distribution and trend views with filtered transaction drill-down.
- Optional WHFIN passcode and strong biometric unlock.
- Adaptive Glance widgets with system-derived Material color and instant quick entry.
- English and Russian UI, light/dark themes, edge-to-edge layout, IME handling, and large-font checks.

## Status

WHFIN is under active development and is not ready for public distribution. The current roadmap covers
SMS diagnostics, an official read-only Open Banking feasibility gate, encrypted exports, release
signing, store policy work, and final accessibility/device QA. See [SPEC.md](SPEC.md),
[docs/roadmap.md](docs/roadmap.md), and [docs/production-readiness.md](docs/production-readiness.md).

## Build

Requirements:

- JDK 17
- Android SDK 36

```bash
./gradlew :app:assembleDebug
./gradlew test
./scripts/check-public-tree.sh
```

Screenshot-test and device-test guidance lives in [docs/testing.md](docs/testing.md). Instrumented or
destructive tests must run only on a disposable emulator, never on a data-bearing personal device.

## Private fixtures

Real statements, SMS bodies, databases, backups, device captures, account identifiers, and signing
material must never be committed. Optional private statement checks read fixtures only from explicitly
configured `WHFIN_REAL_STATEMENT` or `WHFIN_REAL_STATEMENTS_DIR` paths outside the repository.
Committed tests and previews use fictional identifiers and values.
The public-tree check rejects common secret shapes, personal paths and email addresses, real-looking
account/card literals, and tracked financial export formats.

## Development history

The repository was prepared for publication by replacing its private development history with a clean
root commit. A privacy-safe narrative of the work completed so far is kept in [HISTORY.md](HISTORY.md).

## License

Copyright © 2026 whekin. All rights reserved. No open-source license is currently granted; see
[LICENSE](LICENSE).
