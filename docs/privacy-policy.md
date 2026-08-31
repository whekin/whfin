# WHFIN Privacy Policy

Effective date: August 4, 2026

> Release status: the policy below reflects WHFIN 0.1.0. Before public distribution, publish this
> document at a stable public URL and add the developer support contact used by the app-store listing.

WHFIN is an independent personal-finance application developed by whekin. It is
designed to process financial information locally and to minimize collection of personal data.

## Data stored by WHFIN

WHFIN stores accounts, balances, transactions, categories, merchant rules, people, allocations,
debts, statement import metadata and application preferences in the app's private storage on the
Android device.

WHFIN 0.2.0 does not provide a WHFIN cloud account, does not include advertising or analytics SDKs,
and does not operate an application server that receives this financial data.

## SMS permission

SMS access is optional and can be disabled in WHFIN or revoked in Android Settings. When enabled,
WHFIN examines incoming Credo messages on the device. A routed transaction becomes an active local
ledger record immediately; a message without enough routing information remains a visible structured
operation outside balances until resolved. A separate user action can read up to 90 days of recent
messages and shows a dry-run summary before importing anything. OTP codes, rejected payments and
unrelated messages are ignored. WHFIN stores only structured outcomes and masked parsed fields for
diagnostics; raw SMS bodies and OTP codes are not stored, exported or uploaded. WHFIN does not send SMS
content to a server.

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

The code and the lock screen are separate choices. Setting a code does not put a lock screen in front
of the ledger; the delay that does is a second, optional setting. Whenever a code exists, WHFIN asks
for it again — code or biometric — before exporting a backup, restoring one, using the saved MyCredo
login, or changing App Lock itself, and it keeps that answer for at most sixty seconds within the same
flow. With no code set, nothing is asked, because there is nothing to verify against.

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

## Experimental MyCredo connection

WHFIN 0.1.0 contains an optional private, unsupported MyCredo connector. When the user explicitly
connects, the username, password and OTP are sent directly from the device to Credo's current MyCredo
service. WHFIN has no intermediary server. The connector can only request account metadata and XLSX
statements; it contains no payment action. This describes WHFIN's implementation and does not turn the
user's bank login into a bank-issued read-only credential. Access and refresh tokens remain in memory
for the current foreground session and are not backed up or exported.

The user may opt in to remembering the MyCredo login while WHFIN App Lock is active. Username and
password are stored together in an AES-256-GCM ciphertext protected by a non-exportable Android
Keystore key; the username is not stored separately in plaintext preferences. The ciphertext and key
are excluded from Android cloud/device-transfer backup and WHFIN JSON export. Choosing “Forget MyCredo
login”, or opening the connector after App Lock has been disabled, removes the saved credentials. OTP
codes are never stored. WHFIN disables cleartext network traffic and the connector accepts only the
expected Credo HTTPS host. Because this protocol is unsupported, Credo may change or block it without
notice.

WHFIN does not currently integrate with official bank APIs, and such access is outside the active
product roadmap. If it is reconsidered after a public launch, it will require separate consent,
security review and updated disclosures.

## Watch-only crypto balances

WHFIN can track public blockchain addresses that the user enters. WHFIN never asks for, receives or
stores a seed phrase or a private key, and it cannot sign or send a transaction.

Refreshing a balance is a manual, foreground action. It sends the entered public address to the
blockchain endpoint configured in Settings → Privacy policy → Crypto endpoints. That endpoint, and any
network in between, therefore learns that this device is interested in that address, together with the
device IP address. The defaults are public community endpoints that require no account and no API key:
`https://ethereum-rpc.publicnode.com` for Ethereum and `https://api.trongrid.io` for Tron. Both are
operated by third parties under their own privacy terms, and either can be replaced with a self-hosted
or alternative HTTPS endpoint.

Only the address and, for tokens, the contract address are sent. No other WHFIN data is transmitted.
The resulting balance is stored locally with the moment it was read; it is excluded from the portable
JSON export because a single refresh reproduces it exactly.

## Exchange rates

WHFIN converts the headline total into one currency using quotes it fetches itself. Fiat rates come
from the National Bank of Georgia (`nbg.gov.ge`); prices for supported chain assets come from
CoinGecko (`api.coingecko.com`) in US dollars and reach the lari through the same NBG quote.

These requests contain no personal data and no wallet address. The full published rate list is
requested, and the crypto asset list is a fixed build constant rather than the assets actually held,
so a rate request does not reveal what this ledger contains. Quotes are read when they are older than
a few hours while the app is open, and the resulting snapshot is stored locally and excluded from the
portable JSON export.

Converted totals are a reading, never a record: every account keeps its own currency, past
transactions are never re-priced, and volatile assets are excluded from income and expense analysis.

## Changes and contact

Material changes will update the effective date and the in-app privacy summary. Before public release,
the stable policy URL and developer contact must be added here and to the store listing. Until then,
non-sensitive questions can use the repository issue tracker; sensitive reports must use the private
reporting process described in `SECURITY.md`.
