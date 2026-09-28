# All-bank SMS setup and private category audit — 2026-09-28

The Bank SMS step now offers one explicit action for all supported banks. It updates the existing
global SMS preference, which atomically enables Credo and TBC. Android's SMS permission remains a
separate requirement. The action is pinned above Continue on compact screens, so owners can still
skip this optional step. Individual bank controls remain in Connections, where a shared switch can
also enable or disable every supported bank.

Category setup keeps multi-selection for optional interest packs but no longer offers a blanket
Select all action. It says that packs describe interests rather than evidence from spending. An
empty proposal list no longer claims all transactions are categorized.

A read-only review used the owner's latest S25 backup from Downloads. The temporary copy's SHA-256
matched the device file. The review found that existing categories already cover every current
merchant-preset category, while unused optional categories and uncategorized statement rows remain.
No merchant names, amounts, account details, SMS bodies, or backup contents are committed here.
This evidence points to merchant learning and transaction categorization as the next refinement;
adding more category types would not resolve those rows.

Verification: 1248 host tests, 0 failures/errors, 5 skipped; six selected instrumented
scenarios on the Pixel 9 Pro emulator passed after a compact-screen layout fix. EN light,
RU dark/font 1.5/compact, and EN/RU Connections screenshots were inspected. Release R8,
`lintVitalRelease`, and public-tree checks passed.

The signed 0.3.75 (87) release was installed on S25 over 0.3.74 with `install -r`. The signing
certificate matched, and the installed base.apk SHA-256 matched the built APK
(`c33e6ab7…f4c7b8`). `firstInstallTime` and READ_SMS, RECEIVE_SMS, and POST_NOTIFICATIONS
grants were preserved. BankSyncService was inactive before installation. No database reset,
bank sign-in, SMS import, or app launch was performed. The temporary backup copy was removed.
