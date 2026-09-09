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
