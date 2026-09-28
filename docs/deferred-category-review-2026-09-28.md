# Deferred category review after bank history — 2026-09-28

Personal setup arms a device-local follow-up when the owner moves past Categories. The first
proposal snapshot actually shown in a category screen is remembered by category kind and icon.
Suggestions arriving later from saved bank transactions remain unseen. Opening a narrow transfer
or merchant queue does not count as seeing the category overview. Once active bank work
settles, an optional row on setup Ready and a separate Home card offer to review them. An
interrupted bank read delays the offer until that interruption is handled.

The Home card is outside **Needs a decision**. Reviewing opens the existing category intelligence
screen; **Later** sets aside the current category types. A genuinely new type can appear later,
while the same type does not nag again. Neither path creates categories, categorizes transactions,
imports SMS, or changes money. The reminder is not armed for an established user who did not enter
setup, so an upgrade alone adds no new Home card.

The reminder stores only the armed bit and seen category type keys in installation-local
SharedPreferences. Portable and Android backups exclude it; a ledger restore clears it. The
proposal query is collected only while armed and uses the same merchant, uncategorized-operation,
and bank-operation evidence as the category screen. Its first database snapshot is explicit:
loading is not treated as an empty result.

Verification: in-memory Room proposal flow, store recreation and restore reset, inactive-query
guard, Home actions, targeted EN/light and RU/dark/font 1.5 compact emulator screenshots, and
full EN/RU setup journeys passed. The complete host run had 1259 tests, 0 failures/errors, 5
skipped; release R8/lintVital and public-tree checks passed. A first host attempt hit an unrelated
Robolectric queued-work race in the existing physical-card monitor; its isolated class and the
sequential full rerun passed. No live bank sign-in or personal SMS import was run. Persistent proof
of complete per-account bank history remains a separate follow-up; a previous statement alone
does not prove it.

The signed 0.3.77 (89) release was installed on S25 over 0.3.76 with `install -r`. The certificate
matched, and the installed base.apk SHA-256 matched the built APK (`e78bfb59…9e55b0d`).
`firstInstallTime` and READ_SMS, RECEIVE_SMS, and POST_NOTIFICATIONS grants were preserved.
The previously verified backup remained in Downloads; BankSyncService was inactive before
installation. The app was not opened afterwards, and its personal data was not cleared.
