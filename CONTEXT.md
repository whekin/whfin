# WHFIN Product Context

WHFIN is a personal-finance workspace that can also present an isolated product preview without mixing
synthetic and personal financial data.

## Language

**Personal workspace**:
The user’s actual financial space, whether it is still empty or already contains their accounts and history.
It is always separate from the Demo workspace.
_Avoid_: Live mode, real mode, production database

**Demo workspace**:
A temporary, fully synthetic financial space used to explore WHFIN with representative populated data.
Entering or leaving it never changes the Personal workspace.
_Avoid_: Demo mode, sample account, test data toggle

**Demo data**:
Synthetic, mutable content inside the Demo workspace that carries no user-owned value. Unsaved Demo data
may be discarded without a data-loss confirmation.
_Avoid_: Sample personal data, user data

**Demo visit**:
The period from entering the Demo workspace until explicitly returning to the Personal workspace. Demo
data survives application restarts during a visit; a later visit starts from the canonical fixture.
_Avoid_: Demo account lifecycle, persistent demo book

**Welcome choice**:
A single first-run decision shown before the application workspace, offering Personal setup or the Demo
workspace without requesting permissions. Either explicit choice completes it permanently for that
installation; an empty Personal workspace does not make it reappear.
_Avoid_: Onboarding carousel, feature tour, permission onboarding

**Personal setup**:
A compact first-action surface reached from the Welcome choice or when leaving the Demo workspace. It
starts a real data source or lets the user deliberately continue to the still-empty Personal workspace.
_Avoid_: Second onboarding, tutorial step, empty Feed

### SMS ingestion

**SMS monitoring**:
The explicitly enabled observation of future supported-bank transaction messages. It may record a
structured local outcome before the message can be routed, but never stores the raw message body.
_Avoid_: SMS import toggle, automatic transaction import

**SMS routing**:
The remembered association that identifies which Personal workspace ledger a supported-bank message
belongs to. An unresolved message waits for this decision instead of being guessed or dropped.
_Avoid_: Card setup gate, account guess

**SMS import**:
The creation of a financial transaction from a monitored message after SMS routing is resolved. Automatic
routing and an explicit one-tap route both create an ordinary active ledger row with SMS provenance.
It is not a review task: a later statement silently reconciles, enriches, and if necessary corrects it.
_Avoid_: SMS monitoring, message capture

**Unrouted operation**:
A parsed supported-bank message that has enough financial meaning to appear in the Feed but does not yet
belong to a ledger. It never affects balances or statistics; resolving its routing turns it into, or
attaches it to, a real transaction.
_Avoid_: Pending transaction, diagnostic row, uncategorized transaction

**Routing resolver**:
The contextual decision flow opened from an Unrouted operation to choose every missing ledger side.
Creating a missing ledger may temporarily open Bank setup or account creation, then returns to the same
resolution. Resolving a card remembers the route and backfills all compatible queued messages. For a
grouped movement, the resolver derives eligible ledger pairs from the operation itself; a candidate pair
is applied in one tap and is never saved as a separate scenario.
_Avoid_: Go to Accounts, card mapping form, generic account settings

**Bank SMS**:
The user-facing home for SMS monitoring status, unresolved routing decisions, remembered cards and
recent processing activity. Parser diagnostics are details of an individual result, not the identity
of this surface.
_Avoid_: SMS diagnostics, parser log, card-mapping screen

### Bank support

**Bank setup**:
A bank-centred, guided but skippable surface that groups the data channels WHFIN actually supports for
one bank, such as a connection, SMS monitoring, or a statement file. Completed channels become statuses;
unsupported banks and unavailable channels are absent.
_Avoid_: Import-method picker, source setup, coming-soon bank

### Accounts

**Fund role**:
The owner’s decision about whether a ledger’s money is Available for ordinary spending or held as Reserve.
It affects the Available/Reserve reading but never changes the bank product or total net worth.
_Avoid_: Account type, deposit type, savings mode

**Bank product**:
The contract represented by a bank ledger, such as a Current account, Demand deposit, or Term deposit.
It describes the bank’s product and never decides whether the owner treats its balance as Available or Reserve.
_Avoid_: Reserve account, savings purpose, available deposit

**Primary card**:
The single payment instrument the owner treats as their everyday default across the Personal workspace.
It may be physical or virtual; when it is physical, its linked GEL ledger owns the grocery balance warning.
_Avoid_: Main account, default bank, primary ledger

### Savings

**Savings plan**:
The owner’s monthly intention for how much money to move from Available funds into Reserve in one
currency. It describes a habit from an effective month onward and never creates a future transaction.
_Avoid_: Scheduled transfer, recurring payment, savings goal

**Savings pace**:
The net owner-controlled movement across the Available↔Reserve boundary during a calendar month.
Passive income such as deposit interest changes the Reserve balance but does not satisfy the plan.
_Avoid_: Reserve balance change, account growth, investment return

**Savings goal**:
An optional desired Reserve balance and optional date attached to a Savings plan. Reaching it is an
observation, not an automatic close, transfer, or withdrawal.
_Avoid_: Monthly target, mandatory deadline
