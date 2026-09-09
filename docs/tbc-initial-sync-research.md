# TBC first synchronization without a manual statement

Checked 2026-09-09. This is a bounded public-source investigation; no authenticated
bank request, credential, cookie replay or device registration was used. The
inspected connector sources are pinned to ZenPlugins commit
`c7af1ee865ae40482694f572e8205babcb8aaf15`, already inspected for the history
implementation. New web searches found no additional relevant mobile protocol
evidence; an attempted web-tool reload of the raw sources returned cache misses.

## Finding

The existing mobile session is sufficient for the implemented history flow, but
the inspected sources do **not** establish a safe automatic initial balance for
ordinary checking/card accounts. No mobile XLSX download method was found. This
is an evidence limit, not a claim that the bank lacks such a method.
Source: [mobile fetch implementation](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts).

`GET /products/api/v1/cards` models currency accounts with a field named
`balance`; the associated card separately has `blockedAmount`. Dashboard savings
have `amount`. The converter passes these figures to ZenMoney as current account
balances. Neither the model nor the converter defines whether those figures are
booked balances, include overdraft/holds, or have a snapshot time shared with
history. Merely naming a field `balance` is insufficient evidence for a WHFIN
book anchor. Adding all card holds is also unsupported: cards and currency
ledgers are different scopes.
Sources: [account/card models](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts),
[account conversion](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/converters.ts).

The ordinary PFM history response has movement IDs and signed amounts but no
opening, closing, running balance, total row count or snapshot token. Its fetch
function stops at an empty response or a requested date boundary; it does not
prove lifetime completeness or bind account balance to the returned page set.
Therefore `opening = current balance - sum(history)` would combine two unproven
inputs. Reading balance twice with equal results would reduce a race but would
not establish balance semantics or that no equal-and-opposite operations moved
between reads.
Sources: [history request/pagination](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[history model](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).

There is a mobile `/deposits/api/v1/statements/{id}` route and a deposit statement
model containing `balance`, but its movements are deposit amount, withdrawn
deposit amount and interest. It is a different product contract and is not
evidence of checking/card statement support.
Sources: [deposit fetch](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/fetchApi.ts),
[deposit model](https://github.com/zenmoney/ZenPlugins/blob/c7af1ee865ae40482694f572e8205babcb8aaf15/src/plugins/tbc-ge/models.ts).

## Recommended implementation path

Keep the existing validated XLSX opening requirement while improving the common
connection and synchronization entry points. Explain that subsequent TBC history
is automatic after login/session restore; only first initialization still needs
the statement. Do not label this fully automatic setup until the missing path is
verified.

The strongest route to removing that manual step is automatic statement export,
using the already observed **web** export/status/download flow. It preserves the
statement parser's opening and balance-chain verification. Web authentication is
on `ribgwauth`, mobile authentication on `rmbgwauth`; native mobile code forwards
cookies explicitly. Their interchangeability is not established. Add a web
session only after an owner-authorized interactive test verifies its login and
cookie/XSRF lifecycle; do not silently forward mobile cookies to the other host.
The known first-party endpoint evidence and exact remaining checks are recorded
in [TBC connector research](tbc-connector-research.md).

Alternatively, obtain evidence of a mobile statement endpoint or a documented
booked balance with a matching complete history cut-off. Verify it against a
fresh owner-provided statement, including a pending card hold, multiple currency
ledgers and a movement at the sync boundary. Only then design a distinct API
snapshot anchor with its own provenance; never synthesize a file-proven opening
or per-row balances. An API-derived snapshot is not statement coverage evidence
for removing unmatched SMS.

The current contract and implementation limitations remain in
[TBC history sync](tbc-history-sync.md).

Implementation follow-up: the owner requested manual balance entry as an alternative to export.
Version 0.3.29 implements an explicitly provisional USER_OPENING from that entered booked balance;
it does not infer the amount from the unverified mobile balance field. See [bank API sync](bank-api-sync.md).
