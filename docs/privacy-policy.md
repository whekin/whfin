# WHFIN Privacy Policy

Effective date: July 14, 2026

> Release status: the policy below reflects WHFIN 0.1.0. Before public distribution, publish this
> document at a stable public URL and add the developer support contact used by the app-store listing.

WHFIN is an independent personal-finance application developed by whekin. It is
designed to process financial information locally and to minimize collection of personal data.

## Data stored by WHFIN

WHFIN stores accounts, balances, transactions, categories, merchant rules, people, allocations,
debts, statement import metadata and application preferences in the app's private storage on the
Android device.

WHFIN 0.1.0 does not provide a WHFIN cloud account, does not include advertising or analytics SDKs,
and does not operate an application server that receives this financial data.

## SMS permission

SMS access is optional and can be disabled in WHFIN or revoked in Android Settings. When enabled,
WHFIN examines incoming Credo messages on the device and converts recognized transaction messages
into pending financial records. A separate user action can read up to 90 days of recent messages and
shows a dry-run summary before importing anything. OTP codes, rejected payments and unrelated messages
are ignored. WHFIN stores only structured outcomes and masked parsed fields for diagnostics; raw SMS
bodies and OTP codes are not stored, exported or uploaded. WHFIN does not send SMS content to a server.

## Statement files

WHFIN reads a bank statement only after the user selects it through Android's system file picker.
The statement is parsed locally to create and reconcile financial records. WHFIN does not scan other
files or upload the selected statement to a WHFIN server.

## Android backup and device transfer

WHFIN explicitly limits Android backup to its financial database and non-secret UI/widget preferences.
Cloud backup is permitted only when Android reports client-side encryption support; Android may also
transfer the same data directly during device migration. Backup storage and restoration are controlled
by Android and the user's device/account settings. WHFIN cannot inspect the user's Android backup.

Bank credentials, OTP codes and Android Keystore keys are not part of this backup policy. Future
banking tokens must be stored separately and excluded from backup.

## App Lock and widget quick entry

App Lock is optional. When enabled, WHFIN accepts a four-digit app code and, if the user chooses,
a strong biometric through Android's system biometric prompt. WHFIN does not request the phone's
screen-lock PIN. The app code itself is not stored: WHFIN keeps a salted verification result and uses
a non-exportable HMAC key in Android Keystore. After repeated failures, code entry is temporarily
blocked. These App Lock records and the Keystore key are excluded from Android and JSON backups.
App Lock hides financial content and recent-app snapshots, but it does not encrypt the Room database.

The home-screen widget does not display account balances. Its quick-expense action intentionally opens
without App Lock so a user can capture an expense immediately; that surface receives the selected
source/currency and can add a record, but does not expose transaction history or existing balances.

## Sharing and sale of data

WHFIN 0.1.0 does not sell financial or personal data and does not share it with advertisers, data
brokers or analytics providers.

## Data export and deletion

WHFIN can export a versioned JSON backup after the user chooses a destination through Android's
system file picker. The file contains the user's WHFIN financial records and is not encrypted, so it
must be treated as sensitive. It excludes raw SMS, OTP codes, app permissions, credentials, tokens and
Android Keystore keys. A user-selected backup can replace the local WHFIN database only after a
separate confirmation. The file remains under the user's control and is not uploaded to a WHFIN server.

Uninstalling WHFIN removes its local app data; Android may retain an encrypted system backup according
to the user's Android backup settings. A JSON file previously exported outside app-private storage is
not removed by uninstalling the app.

## Experimental MyCredo connection and future Open Banking

WHFIN 0.1.0 contains an optional private, unsupported MyCredo connector. When the user explicitly
connects, the username, password and OTP are sent directly from the device to Credo's current MyCredo
service. WHFIN has no intermediary server. The connector can only request account metadata and XLSX
statements; it contains no payment action. Access and refresh tokens remain in memory for the current
foreground session and are not backed up or exported.

The user may opt in to remembering the MyCredo password after enabling WHFIN App Lock. The password is
encrypted on the device with an Android Keystore AES-GCM key. Its ciphertext, username and key are
excluded from Android cloud/device-transfer backup and WHFIN JSON export. Choosing “Forget MyCredo
login” removes the saved ciphertext and username. OTP codes are never stored. Because this protocol is
unsupported, Credo may change or block it without notice.

Official Credo Open Banking synchronization is not active. Before it is enabled, WHFIN will require
explicit consent, document the data exchanged with Credo or an authorised provider, describe token
retention, and update this policy.

## Changes and contact

Material changes will update the effective date and the in-app privacy summary. Before public release,
the stable policy URL and developer contact must be added here and to the store listing. Until then,
non-sensitive questions can use the repository issue tracker; sensitive reports must use the private
reporting process described in `SECURITY.md`.
