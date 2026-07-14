# WHFIN development history

This is a privacy-safe summary of the milestones that preceded the public repository. The original
local commit history was intentionally not published because early development used private financial
fixtures and device captures.

## Foundation

- Created a native Android project using Kotlin, Jetpack Compose, Room, KSP, and DataStore.
- Designed a multi-account, multi-currency ledger model with financial groups, payment instruments,
  statement sources, people and allocations, transfer groups, debts, and watch-only crypto assets.
- Established explicit Room migrations, schema exports, and migration tests.

## Import and reconciliation

- Implemented a dependency-free XLSX reader and Credo statement parser.
- Added atomic statement import, external-key deduplication, balance-chain validation, coverage history,
  gap detection, and a review queue for unmatched records.
- Built Credo SMS parsing and reception for supported transaction shapes, with pending transactions and
  statement reconciliation. Diagnostics and historical inbox review remain roadmap items.
- Added local merchant categorization presets and persistent merchant-to-category learning.

## Product flows

- Built ledger-style Feed and Accounts surfaces with search, filtering, sorting, multi-currency account
  structure, bank details, statement import, and account overview.
- Added full-screen composers for expenses, income, transfers, conversions, debts, repayments, and
  balance corrections.
- Added people/debt allocation semantics without distorting account balances or expense statistics.
- Added monthly income/expense/net views, category distribution, yearly trends, selectable bars, and
  filtered transaction drill-down.

## Design system and quality

- Extracted a shared `:core-ui` design-system module around the “Quiet Ledger” visual language.
- Standardized color, typography, spacing, shape, motion, actions, notices, ledger groups, state panes,
  and IME-safe form surfaces while retaining Material 3 behavior and accessibility foundations.
- Implemented edge-to-edge layouts, coherent navigation transitions, shaped touch feedback, haptics,
  light/dark themes, large-font previews, and screenshot tests.
- Added adaptive Glance widgets with system-derived Material colors and capture-only quick entry.

## Data safety and production groundwork

- Added deterministic, versioned JSON export/restore with a strict table allowlist and explicit warnings.
- Added a custom four-digit WHFIN code, optional strong biometrics, lockout behavior, and protected
  recent-app snapshots. Widget quick entry intentionally remains capture-only and does not expose data.
- Added explicit Android backup rules, privacy/about screens, source privacy documentation, release
  checks, and a production-readiness roadmap.

Future milestones should once again be represented by focused commits in the public history.
