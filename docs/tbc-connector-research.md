# TBC statement connector research

Observed 2026-09-09. This note describes TBC's public retail web client, not a
supported public integration API. No authenticated requests were made for this
research. User-supplied network examples corroborate parts of the flow but are
not a successful connector test. Credentials, tokens and personal account/order
identifiers are deliberately absent.

## Verified in public first-party client code

The current deployment config sets the account API origin to
`https://ribgw.tbconline.ge` and authentication origin to
`https://ribgwauth.tbconline.ge`.
Source: [deployment configuration](https://tbconline.ge/tbcrd/environments/config.json).

Authentication starts with **GET `https://ribgwauth.tbconline.ge/v1/users/current`**.
The login service returns this request directly; the user service handles HTTP 401
by exposing the response body's `redirectUrl`. API URL definitions compose
`USERS` from the authentication origin plus `/v1/users`.
Source: [shared client module, `getCurrentUser` and `ApiUrls`](https://tbconline.ge/tbcrd/9220.512312bb49af3344.js).

The login component calls `getCurrentUser`, reloads on success, and uses
`error.redirectUrl` on failure. It creates a POST form inside the authentication
iframe: the form action is the redirect URL without the query, and query
parameters become hidden fields. It sets `ui_locales` and optionally
`device_trust_id` from the `trustedRegistrationId` cookie. The iframe reports
messages including `login-success`, `login-failed` and `session-expired`.
This establishes an interactive authentication handoff, not a demonstrated
username/password HTTP login recipe.
Source: [login module, `getIframeUrl`, `parseQueryParams`, `rewriteIframeContent`](https://tbconline.ge/tbcrd/2258.7593941b4880c92e.js).

The public shared module names the XSRF cookie `XSRF-TOKEN-RIB` and header
`X-XSRF-TOKEN`. Their issuance, renewal and exact interceptor behavior have not
been verified here; these names alone do not establish sufficient authentication.
Source: [shared client constants](https://tbconline.ge/tbcrd/9220.512312bb49af3344.js).

The statement service uses these endpoints under `https://ribgw.tbconline.ge`:

| Method | Path | Client behavior |
| --- | --- | --- |
| GET | `/accounts/api/v1/statements/check-formats` | Query fields `AccountIds`, `StartDate`, `EndDate`, `Status`; dates formatted `YYYY-MM-DD`. |
| POST | `/accounts/api/v1/statements/{format}/export` | Creates export. Excel request converts singular `accountId` into `accountIds: [accountId]`. |
| GET | `/accounts/api/v1/statements/{id}/status` | Polls export operation status. |
| GET | `/accounts/api/v1/statements/orders` | Fetches existing export orders. |
| GET | `/accounts/api/v1/statements/{id}/download` | Requests a binary blob and full HTTP response; takes filename from `Content-Disposition`, saves response body. |

Sources: [endpoint definitions](https://tbconline.ge/tbcrd/9220.512312bb49af3344.js),
[statement service module 96443](https://tbconline.ge/tbcrd/722.322d24c6148947b1.js).

The web client polls every 2 seconds for at most 10 calls. It checks
`result.operationStatus` against `Statuses.DONE`, whose actual wire value is
`SUCCEED`; on success it downloads `result.operationCommandId`. The frontend
also refreshes the orders list when this polling finishes. These are observed
frontend choices, not a server guarantee that generation finishes within 20
seconds.
Sources: [polling implementation](https://tbconline.ge/tbcrd/722.322d24c6148947b1.js),
[status enum](https://tbconline.ge/tbcrd/9220.512312bb49af3344.js).

## Evidence supplied by the owner

The owner's trace shows `POST .../statements/xlsx/export` with dates, `status:
"All"`, `equivalent: false` and `accountIds`, returning an envelope with
`result.operationStatus: "DRAFT"` and `result.operationCommandId`. Their orders
response contains completed Excel, PDF and CSV exports, including account IBAN,
currency and requested period. The last supplied request was OPTIONS to the
download route; the public statement service independently confirms that the
actual download uses GET. Raw traces are not reproduced in the repository.

## Remaining validation before automatic synchronization

- Complete an owner-driven interactive bank login and observe the redirect,
  callback and cookie lifecycle. Passwords and OTPs belong in the bank's UI.
- Verify whether the intended Android browser/WebView integration can maintain
  that session and complete the iframe handoff, including expiry and re-login.
- In the authorized session, verify account discovery, export creation, status
  failure/timeout behavior and an actual XLSX response, including headers.
- Verify XSRF renewal and any required application/session headers from the live
  client. Do not hardcode previously captured token or session values.

The available sources are enough to identify the protocol skeleton and the
previously missing download method. They do not prove unattended authentication
or a working automatic connector. File-based XLSX import can ship independently.

## Mobile protocol comparison: ZenPlugins source

Inspected 2026-09-09 at ZenPlugins revision
`c7af1ee865ae40482694f572e8205babcb8aaf15`. This is primary evidence of what
that independent connector implements, **not bank documentation or proof that
these endpoints currently accept a WHFIN session**. No authenticated call was
made. The previous sections describe TBC's own web client; this section describes
ZenPlugins' mobile flow.

Its authentication host is `rmbgwauth.tbconline.ge`, distinct from web
`ribgwauth.tbconline.ge`. It posts JSON to `/v1/auth/loginWithPassword`, optionally
`/v1/auth/certifyLogin`, and can later use `/v1/auth/easyLogin` after device
registration. Login includes Base64-encoded JSON device information/data and a
device ID. OTP certification carries the login `transactionId`, signature type,
`otpId` and response. The response model exposes `success`,
`secondPhaseRequired`, `changePasswordRequired` and `userSelectionRequired`, so
receiving a response does not alone establish a completed login. This source
also implements device registration and trust/untrust operations; those are
separate mutations, not necessary assumptions for a first interactive login.
Sources: [mobile fetch functions](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[authentication models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).

The connector extracts `set-cookie` into an array, explicitly joins it into a
`Cookie` request header, checks the session using GET
`https://rmbgwauth.tbconline.ge/v2/usermanagement/userinfo`, and sends the same
cookie array to data APIs on `rmbgw.tbconline.ge`. This is explicit native HTTP
forwarding; the helper does not establish browser cookie Domain/Path scope or
compatibility with either `ribgw` web host. The inspected mobile functions have
**no XLSX export/download implementation**. They retrieve JSON history; therefore
mobile authentication must not be treated as proven authorization for the web
XLSX endpoint.
Sources: [cookie helper](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/utils.ts),
[fetch functions](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[synchronization entry point](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/index.ts).

Account discovery uses GET `/products/api/v1/cards` and
`/dashboard/api/v1/cards-and-accounts` on `rmbgw.tbconline.ge`. The product model
has an IBAN plus currency-specific accounts containing `id`, `coreAccountId`,
`balance` and `currency`. Its converter uses **`account.id`**, not
`coreAccountId`, for history requests, and maps `account.balance` as the current
balance. Dashboard entries instead have `id`, `iban`, `amount` and `currency`.
Sources: [fetch functions](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[account models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts),
[account conversion](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/converters.ts).

History uses POST `https://rmbgw.tbconline.ge/pfm/api/v1/transactions/history`.
The request specifies `coreAccountIds: [{currency, iban, id, type: "200"}]`,
`pageSize: 100`, `pageType: "History"`, `isChildCardRequest: false`,
`showBlockedTransactions: false`, and continuation values `lastSortColKey` /
`lastBlockedMovementDate`. It receives an array of `{date, transactions}` groups;
`date` is treated as epoch milliseconds. The plugin advances cursors from
transaction IDs/blocked dates and stops at empty results or its date boundary.
This is a pagination example, not evidence that a repeated day safely implies
end-of-history for another implementation.
Source: [fetchHistoryV2](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts).

The modeled history row has `transactionId`, `accountId`, `entryType`,
`movementId`, `transactionDate`, `localTime`, `title`, `subTitle`, `amount`,
`currency`, `categoryCode`, `subCategoryCode`, `transactionSubtype`,
`transactionStatus`, `isDebit`, blocked movement/card/IBAN fields and optional UI
capability flags. The model comments say ordinary rows have `movementId`, while
blocked rows have zero transaction ID and null movement/account IDs; ordinary
row dates come from the outer group or parsed POS description. The converter
uses **`movementId` as row identity**, retains signed `amount` directly, and uses
`transactionId` plus title as transfer grouping hints. These IDs are not proven
to equal the XLSX Transaction ID.
Sources: [TransactionRecordV2 model](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts),
[transaction conversion](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/converters.ts).

There is no per-row balance, statement opening/closing balance, or structured
counterparty IBAN in that account-history model. Account balances are separate
snapshots; deposit statements have a different model with a balance. Cross-bank
transfer recognition and CSV/XLSX deduplication therefore need validation against
actual owner-authorized mobile responses before replacing the richer file
import. Whether mobile cookies work with web exports, or whether an equivalent
mobile XLSX route exists, remains unknown.
Source: [history and deposit models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).
