# First run, Demo workspace, and Bank SMS

Status: SMS model, Bank SMS, Welcome choice, Personal setup, and Demo workspace implemented,
2026-07-29. The remaining physical-device step is the bounded OnePlus SMS dry-run. Canonical terms live in
[`CONTEXT.md`](../CONTEXT.md).

## Product intent

The first experience should let a person understand a populated WHFIN before committing data, then lead
them into the bank-specific setup that provides the most automation. Demo is a temporary isolated
workspace, not a prominent global preference. SMS setup should begin working after explicit consent even
when account routing is still incomplete.

## First run

A fresh untouched installation shows one full-screen **Welcome choice** before the application shell:

- primary: `Set up my finances`;
- secondary: `Explore demo`;
- no carousel and no permission prompts;
- system Back closes the application.

Either choice permanently completes Welcome choice for that installation. Choosing Personal setup and
then adding nothing must not make the gate reappear. Data-bearing existing installations must never see a
new first-run gate after upgrade. Runtime choice flags are deliberately excluded from backup.

The signal for "already welcomed" is the presence of a ledger, read before the database is opened.
It must not be an install timestamp: `lastUpdateTime > firstInstallTime` is true for every sideloaded
build after the first and survives a data wipe, so the gate became permanently unreachable on any
device that had once been updated. An installation whose database file already exists is adopted
silently; an empty one — however many times its package was updated — still shows Welcome choice.

Choosing Personal setup records a resumable pending setup surface. Process restarts return there until the
person deliberately continues to Feed or Accounts. Choosing Demo completes Welcome only after the isolated
fixture has been installed successfully.

## Personal setup

Personal setup is one resumable, outcome-oriented setup rather than a feature carousel. It is bank-centred:
the current build explains that the guided route is tailored to Credo and shows only channels that
actually work for it. Unsupported TBC/BOG channels are not advertised as coming soon.

For the current Credo dogfood build, the recommended guided-but-skippable sequence is:

1. Enable future Credo SMS monitoring and request the shared `RECEIVE_SMS` + `READ_SMS` permissions.
   Granting them advances directly to MyCredo; denial stays on the same explainable setup step.
2. Connect MyCredo, let a new exact four-digit login SMS fill the local code surface, and explicitly
   confirm it. The first successful login automatically starts the one-off full-history walk instead
   of presenting it as a second action the person must discover.
3. During that walk, statement import reconciles already-known SMS evidence and the bounded inbox read
   infers safe card mappings without copying raw messages into Room. A failed account remains on the
   Credo result surface for retry instead of advancing as if setup succeeded.
4. After a successful pass, resolve Unrouted SMS first and statement reconciliation issues second. Each
   queue is skipped when empty; when both are clear, setup returns to a concise ready state.
5. Optionally add cash, an unconnected bank account, or a deposit, then continue to Home.

Completed steps become status rows and can be continued later from Credo Bank setup. Manual account
creation and skipping into the empty Personal workspace remain available. Statement-file import and
restore are collapsed behind one secondary fallback rather than competing with the guided route.

Future TBC and BOG support should use the same Bank setup grammar while exposing only the channels each
bank truly supports.

The implemented Credo surface links directly to the existing MyCredo/OTP flow, Bank SMS, statement XLSX
import, portable restore, and manual Accounts entry. Its pinned action follows the next recommended
incomplete step while `Continue without setup` remains explicit.

Back follows the visible hierarchy. At the setup root it exits the app; Credo, Bank SMS, statements,
backup, and App Lock return to the setup overview or their real caller. Inside the application shell,
secondary destinations keep a caller stack, so paths such as Accounts → Settings → Statements unwind in
that order instead of routing every tool back through Settings. A nested composer step consumes Back
before the composer itself; a dirty composer still shows its discard decision.

## Demo workspace

In the Personal workspace, Settings shows a compact `Explore demo` row near About instead of a large
notice or switch. Tapping it opens a short explanation that synthetic and personal data are isolated,
then an explicit `Open demo` action.

While Demo workspace is active:

- every application screen shows a compact, non-dismissible workspace strip with `Use my data`;
- the strip remains above primary and secondary destinations as well as full-screen working dialogs;
- a fresh user exits to Personal setup;
- an established user exits directly to their Personal Feed;
- an unsaved Demo form closes without a discard confirmation;
- `Reset demo now` exists only in Demo Settings.

A **Demo visit** survives process restarts. Explicitly returning to Personal ends the visit; the next
entry starts from the canonical fixture rather than retaining old synthetic edits.

The implemented entry lives beside About rather than at the top of Settings. The explanation sheet keeps
its action pinned while its copy scrolls at large font scales. Demo Settings alone exposes the destructive
reset row. The shared workspace frame owns the status-bar inset and is reused by composer, category filter,
debt ledger, and account-details dialogs so `Use my data` remains available even with an unsaved form.
Verified at EN/light/font 1.0 and RU/dark/font 1.5 on a disposable emulator, including first-run and
established exit destinations, dirty-composer exit without confirmation, and canonical fixture restore
after a saved synthetic edit.

## SMS ingestion model

SMS monitoring, routing, and import are separate:

1. The user explicitly enables monitoring and grants `RECEIVE_SMS`.
2. Every future supported-bank candidate receives a local structured outcome even when no card mapping
   exists. Raw body remains memory-only.
3. A parsed message without enough account information becomes an **Unrouted operation**.
4. Resolving every missing ledger confirms the operation the user explicitly reviewed, or attaches the
   SMS evidence to an already confirmed statement transaction. A remembered card route also imports
   compatible queued messages automatically; those unreviewed rows remain pending.

The current setup gate that refuses to enable monitoring before the first card mapping is removed.
Nothing is guessed: incomplete routing delays ledger mutation, not monitoring.

### Feed projection

A parsed Unrouted operation appears at its actual date among Feed rows:

- merchant/counterparty, amount, currency, and an explicit `Choose account`/`Choose accounts` status;
- muted treatment distinct from a routed pending transaction;
- excluded from balance, day/month totals, category distribution, and statistics;
- tap opens its Routing resolver.

Unrecognized formats, OTP, rejected messages, and technical errors do not masquerade as financial rows.
They remain in Bank SMS.

Transfers and conversions use the existing grouped-operation grammar. Before routing they appear as one
provisional grouped row; the resolver selects every missing `from`/`to` ledger, then creates the normal
`TransferGroup` and its legs atomically. If a compatible ledger is missing, account creation or Bank
setup returns to the same resolver rather than abandoning context in Accounts.

## Bank SMS surface

The user-facing destination is `Bank SMS`, not `SMS diagnostics`.

Its hierarchy is:

1. monitoring status and one dominant next action;
2. `Needs attention` for unresolved card/account routing;
3. recent processing activity;
4. `Cards and accounts`, collapsed or visually secondary when healthy;
5. optional `Check recent SMS`;
6. parser details and safe failure sharing inside an individual result or troubleshooting area.

## Implementation order

1. Separate SMS monitoring/routing/import and persist Unrouted operations without mutating the ledger.
2. Project them into Feed and add the contextual Routing resolver, including grouped transfers and
   statement-first reconciliation.
3. Rebuild SMS diagnostics as Bank SMS.
4. Add Welcome choice and bank-centred Personal setup on top of the finished Credo/SMS flow. **Done.**
5. Replace the Settings demo switch with the new entry, workspace strip, exit destinations, and Demo
   visit reset policy. **Done.**
6. Run the bounded OnePlus SMS dry-run manually before any real-message import.

Every UI slice requires light/dark, RU/EN, font scale 1.5, compact-height, disposable-emulator behavior,
and data-preserving `install -r` only on the physical OnePlus.
