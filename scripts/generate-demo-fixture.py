#!/usr/bin/env python3
"""Generates the public demo fixture.

The demo workspace has to look like a real year of money without containing any: every name, IBAN,
card mask, address and amount here is invented. The scenario is deliberately opinionated so the UI
has something to show in each state:

- deposits hold the money and the everyday ledgers stay thin, so a card top-up is visible;
- foreign currency appears both as an FX card charge and as an explicit conversion;
- one watch-only wallet holds the same ticker on two chains;
- the last month carries pending drafts, a review item and open debts.

Run:  python3 scripts/generate-demo-fixture.py
"""

from __future__ import annotations

import json
import re
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/whfin-demo-v7.json"

# Read from the source of truth: a hand-typed number here silently drifts behind the schema, and the
# restore then claims to be a backup of a database version that no longer exists.
_DB_SOURCE = (ROOT / "app/src/main/java/dev/whekin/whfin/data/db/WhfinDatabase.kt").read_text(encoding="utf-8")
_DB_MATCH = re.search(r"WHFIN_DATABASE_VERSION\s*=\s*(\d+)", _DB_SOURCE)
if not _DB_MATCH:
    raise SystemExit("Could not read WHFIN_DATABASE_VERSION from WhfinDatabase.kt")
DATABASE_VERSION = int(_DB_MATCH.group(1))

# The fixture is anchored so assertions stay deterministic; the installer shifts it to today.
ANCHOR = sys.argv[2] if len(sys.argv) > 2 else "2026-07-31"
if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", ANCHOR):
    raise SystemExit("Anchor must use YYYY-MM-DD.")
ANCHOR_DATE = date.fromisoformat(ANCHOR)

EXPORTED_AT = f"{ANCHOR}T18:00:00Z"
LAST_MONTH = (ANCHOR_DATE.year, ANCHOR_DATE.month)
MONTHS = 12

GEL, USD, EUR = "GEL", "USD", "EUR"


def month_start(index: int) -> datetime:
    """index 0 is the oldest month of the fixture."""
    year, month = LAST_MONTH
    total = year * 12 + (month - 1) - (MONTHS - 1 - index)
    return datetime(total // 12, total % 12 + 1, 1, 10, 0, tzinfo=timezone.utc)


def at(index: int, day: int, hour: int = 10) -> int:
    # February exists: a day past 28 would roll into the next month and invent a 13th one.
    if not 1 <= day <= 28:
        raise ValueError(f"day {day} would leave its month")
    base = month_start(index)
    return int((base.replace(hour=hour) + timedelta(days=day - 1)).timestamp() * 1000)


class Fixture:
    def __init__(self) -> None:
        self.groups: list[dict] = []
        self.addresses: list[dict] = []
        self.assets: list[dict] = []
        self.accounts: list[dict] = []
        self.instruments: list[dict] = []
        self.links: list[dict] = []
        self.sources: list[dict] = []
        self.transfer_groups: list[dict] = []
        self.categories: list[dict] = []
        self.merchants: list[dict] = []
        self.aliases: list[dict] = []
        self.people: list[dict] = []
        self.transactions: list[dict] = []
        self.allocations: list[dict] = []
        self.debt_cases: list[dict] = []
        self.debt_events: list[dict] = []
        self.imports: list[dict] = []
        self.issues: list[dict] = []
        self._tx_id = 0
        self._group_id = 0

    # --- structure -------------------------------------------------------

    def group(self, gid: int, name: str, gtype: str, provider: str | None, order: int) -> int:
        self.groups.append({
            "id": gid, "name": name, "type": gtype, "provider": provider,
            "isArchived": 0, "sortOrder": order,
        })
        return gid

    def account(self, aid: int, name: str, atype: str, group: int | None, currency: str,
                iban: str | None = None, address: int | None = None, asset: int | None = None,
                goal: int | None = None, mode: str | None = None, order: int = 0) -> int:
        self.accounts.append({
            "id": aid, "name": name, "type": atype, "groupId": group, "currency": currency,
            "iban": iban, "walletAddressId": address, "cryptoAssetId": asset,
            "savingsGoalMinor": goal, "savingsMode": mode, "isArchived": 0, "sortOrder": order,
        })
        return aid

    # --- money -----------------------------------------------------------

    # Plausible fixed rates: the demo must not ask a real bank for a real day.
    DEMO_GEL_PER_UNIT = {"USD": 2.72, "EUR": 2.95}

    def tx(self, account: int, minor: int, currency: str, when: int, *, category: int | None = None,
           merchant: int | None = None, counterparty: str | None = None, note: str | None = None,
           status: str = "CONFIRMED", source: str = "STATEMENT", transfer_group: int | None = None,
           is_transfer: bool = False, orig_minor: int | None = None, orig_currency: str | None = None,
           key: str | None = None, posted: int | None = None, iban: str | None = None) -> int:
        self._tx_id += 1
        self.transactions.append({
            "id": self._tx_id, "accountId": account, "amountMinor": minor, "currency": currency,
            "origAmountMinor": orig_minor, "origCurrency": orig_currency,
            "occurredAt": when, "postedAt": posted, "merchantId": merchant,
            "rawCounterparty": counterparty, "counterpartyIban": iban, "categoryId": category,
            "note": note, "status": status, "source": source,
            "transferGroupId": transfer_group, "isTransfer": 1 if is_transfer else 0,
            "balanceAfterMinor": None, "externalKey": key or f"demo:{self._tx_id}",
            # A foreign row carries the lari it was worth on its own day, exactly as a backfill
            # would have booked it, so the demo never reaches for a historical quote.
            "gelValueMinor": None if currency == GEL else round(minor * self.DEMO_GEL_PER_UNIT[currency]),
            "gelRateOn": None if currency == GEL else datetime.fromtimestamp(
                when / 1000, timezone.utc,
            ).date().isoformat(),
            "createdAt": when + 3_600_000,
        })
        return self._tx_id

    def move(self, kind: str, note: str, when: int, src: int, src_minor: int, src_currency: str,
             dst: int, dst_minor: int, dst_currency: str) -> int:
        """Both legs of a transfer or conversion, always created together."""
        self._group_id += 1
        gid = self._group_id
        self.transfer_groups.append({"id": gid, "type": kind, "note": note, "createdAt": when})
        self.tx(src, -src_minor, src_currency, when, note=note, transfer_group=gid,
                is_transfer=True, key=f"demo:move:{gid}:out")
        self.tx(dst, dst_minor, dst_currency, when, note=note, transfer_group=gid,
                is_transfer=True, key=f"demo:move:{gid}:in")
        return gid


f = Fixture()

# --- containers ----------------------------------------------------------

CREDO = f.group(1, "Credo Demo", "BANK", "Credo", 0)
ATLAS = f.group(2, "Atlas Bank", "BANK", "Atlas", 1)
TRON_WALLET = f.group(3, "Tron wallet", "WALLET", "tron:mainnet", 2)
EVM_WALLET = f.group(4, "Ethereum wallet", "WALLET", "eip155:1", 3)

# Synthetic but well-formed: base58check of an all-zero payload, and repeated hex nibbles.
f.addresses.append({"id": 1, "groupId": TRON_WALLET, "chainId": "tron:mainnet",
                    "address": "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", "label": None})
f.addresses.append({"id": 2, "groupId": EVM_WALLET, "chainId": "eip155:1",
                    "address": "0x00112233445566778899aabbccddeeff00112233", "label": None})

f.assets.append({"id": 1, "chainId": "tron:mainnet", "contractAddress": None,
                 "symbol": "TRX", "name": "Tronix", "decimals": 6})
f.assets.append({"id": 2, "chainId": "tron:mainnet",
                 "contractAddress": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                 "symbol": "USDT", "name": "Tether USD", "decimals": 6})
f.assets.append({"id": 3, "chainId": "eip155:1",
                 "contractAddress": "0xdac17f958d2ee523a2206206994597c13d831ec7",
                 "symbol": "USDT", "name": "Tether USD", "decimals": 6})

CREDO_IBAN = "GE00CD0000000000000001"
CREDO_DEPOSIT_IBAN = "GE00CD0000000000000002"
ATLAS_IBAN = "GE00AT0000000000000001"

EVERYDAY_GEL = f.account(1, "Everyday", "BANK", CREDO, GEL, iban=CREDO_IBAN, order=0)
EVERYDAY_USD = f.account(2, "Everyday", "BANK", CREDO, USD, iban=CREDO_IBAN, order=1)
DEPOSIT_GEL = f.account(3, "Term deposit", "SAVINGS", CREDO, GEL, iban=CREDO_DEPOSIT_IBAN,
                        mode="TERM_DEPOSIT", order=2)
DEPOSIT_USD = f.account(4, "Term deposit", "SAVINGS", CREDO, USD, iban=CREDO_DEPOSIT_IBAN,
                        mode="TERM_DEPOSIT", order=3)
ATLAS_GEL = f.account(5, "Everyday", "BANK", ATLAS, GEL, iban=ATLAS_IBAN, order=4)
TRAVEL_EUR = f.account(6, "Travel", "BANK", ATLAS, EUR, iban=ATLAS_IBAN, order=5)
CASH_GEL = f.account(7, "Pocket money", "CASH", None, GEL, order=6)
WALLET_TRX = f.account(8, "TRX", "CRYPTO", TRON_WALLET, "TRX", address=1, asset=1, order=7)
WALLET_USDT_TRON = f.account(9, "USDT", "CRYPTO", TRON_WALLET, "USDT", address=1, asset=2, order=8)
WALLET_USDT_EVM = f.account(10, "USDT", "CRYPTO", EVM_WALLET, "USDT", address=2, asset=3, order=9)

# Cards carry no balance of their own: they point at the thin everyday ledgers.
f.instruments.append({"id": 1, "groupId": CREDO, "type": "PHYSICAL_CARD", "last4": "0001",
                      "label": "Everyday card", "isArchived": 0})
f.instruments.append({"id": 2, "groupId": CREDO, "type": "VIRTUAL_CARD", "last4": "0002",
                      "label": "Online card", "isArchived": 0})
f.instruments.append({"id": 3, "groupId": ATLAS, "type": "PHYSICAL_CARD", "last4": "0003",
                      "label": "Atlas card", "isArchived": 0})
for instrument, account in ((1, EVERYDAY_GEL), (1, EVERYDAY_USD), (2, EVERYDAY_GEL),
                            (2, EVERYDAY_USD), (3, ATLAS_GEL), (3, TRAVEL_EUR)):
    f.links.append({"instrumentId": instrument, "accountId": account})

for sid, (account, label) in enumerate((
    (EVERYDAY_GEL, "Demo everyday GEL"),
    (EVERYDAY_USD, "Demo everyday USD"),
    (DEPOSIT_GEL, "Demo deposit GEL"),
    (ATLAS_GEL, "Atlas everyday GEL"),
), start=1):
    f.sources.append({"id": sid, "groupId": CREDO if account != ATLAS_GEL else ATLAS,
                      "type": "ACCOUNT", "accountId": account, "instrumentId": None, "label": label})

# --- reference data ------------------------------------------------------

CATEGORIES = [
    (1, "unaccounted", "EXPENSE", "HelpOutline", -6381922, 1, 999),
    (2, "Groceries", "EXPENSE", "ShoppingCart", -11570593, 0, 0),
    (3, "Eating out", "EXPENSE", "Restaurant", -3839921, 0, 1),
    (4, "Rent", "EXPENSE", "Home", -9932133, 0, 2),
    (5, "Utilities", "EXPENSE", "Bolt", -11168118, 0, 3),
    (6, "Transport", "EXPENSE", "DirectionsBus", -10516056, 0, 4),
    (7, "Subscriptions", "EXPENSE", "Subscriptions", -7115620, 0, 5),
    (8, "Health", "EXPENSE", "MedicalServices", -4694948, 0, 6),
    (9, "Gifts", "EXPENSE", "CardGiftcard", -3631810, 0, 7),
    (10, "Home", "EXPENSE", "Chair", -8875181, 0, 8),
    (11, "Tech", "EXPENSE", "Devices", -9018202, 0, 9),
    (12, "Travel", "EXPENSE", "Flight", -10516056, 0, 10),
    (13, "Other", "EXPENSE", "Category", -8419447, 0, 11),
    (14, "Salary", "INCOME", "Payments", -10515612, 0, 0),
    (15, "Side income", "INCOME", "Work", -8480171, 0, 1),
    (16, "Interest", "INCOME", "Percent", -12808303, 0, 2),
]
for cid, name, kind, icon, color, system, order in CATEGORIES:
    f.categories.append({"id": cid, "name": name, "parentId": None, "kind": kind, "icon": icon,
                         "color": color, "isSystem": system, "sortOrder": order})

GROCERIES, EATING, RENT, UTILITIES, TRANSPORT = 2, 3, 4, 5, 6
SUBSCRIPTIONS, HEALTH, GIFTS, HOME, TECH, TRAVEL_CAT, OTHER = 7, 8, 9, 10, 11, 12, 13
SALARY, SIDE, INTEREST = 14, 15, 16

MERCHANTS = [
    (1, "juniper market", "Juniper Market", GROCERIES),
    (2, "sunroom grocer", "Sunroom Grocer", GROCERIES),
    (3, "copper table", "Copper Table", EATING),
    (4, "lime transit", "Lime Transit", TRANSPORT),
    (5, "city energy demo", "City Energy", UTILITIES),
    (6, "quiet stream", "Quiet Stream", SUBSCRIPTIONS),
    (7, "demo landlord", "Demo Landlord", RENT),
    (8, "north studio payroll", "North Studio", SALARY),
    (9, "paper plane client", "Paper Plane Client", SIDE),
    (10, "willow pharmacy", "Willow Pharmacy", HEALTH),
    (11, "clay home store", "Clay Home Store", HOME),
    (12, "small bookshop", "Small Bookshop", OTHER),
    (13, "marigold flowers", "Marigold Flowers", GIFTS),
    (14, "device workshop", "Device Workshop", TECH),
    (15, "courtyard coffee", "Courtyard Coffee", EATING),
    (16, "northwind hosting", "Northwind Hosting", SUBSCRIPTIONS),
    (17, "harbour books online", "Harbour Books Online", OTHER),
    (18, "seaside apartments", "Seaside Apartments", TRAVEL_CAT),
    (19, "alpine rail", "Alpine Rail", TRAVEL_CAT),
    (20, "old town bistro", "Old Town Bistro", EATING),
]
for mid, key, name, category in MERCHANTS:
    f.merchants.append({"id": mid, "normalizedKey": key, "displayName": name, "categoryId": category})
for aid, (merchant, pattern) in enumerate((
    (1, "juniper market demo"), (3, "copper table tbilisi"),
    (5, "city energy ltd"), (16, "northwind hosting bv"),
), start=1):
    f.aliases.append({"id": aid, "merchantId": merchant, "pattern": pattern})

f.people.append({"id": 1, "name": "Nino", "role": "FRIEND", "color": -3839921, "isArchived": 0})
f.people.append({"id": 2, "name": "Luka", "role": "FAMILY", "color": -11570593, "isArchived": 0})
f.people.append({"id": 3, "name": "Maya", "role": "COLLEAGUE", "color": -9932133, "isArchived": 0})

# --- opening balances ----------------------------------------------------

OPENING = [
    (EVERYDAY_GEL, GEL, 42_000),
    (EVERYDAY_USD, USD, 4_500),
    (DEPOSIT_GEL, GEL, 200_000),
    (DEPOSIT_USD, USD, 120_000),
    (ATLAS_GEL, GEL, 62_000),
    (TRAVEL_EUR, EUR, 15_000),
    (CASH_GEL, GEL, 9_000),
]
opening_at = int((month_start(0) - timedelta(days=1)).timestamp() * 1000)
for account, currency, minor in OPENING:
    f.tx(account, minor, currency, opening_at, category=1, note="Demo opening balance",
         source="ADJUSTMENT", is_transfer=True, key=f"demo:opening:{account}")

# --- the year ------------------------------------------------------------

GROCERY_RUN = [
    (3, 1, 6_450), (10, 2, 3_820), (17, 1, 7_910), (24, 2, 4_260),
]
EATING_RUN = [(8, 15, 1_270), (19, 3, 4_480), (27, 15, 1_860)]

for m in range(MONTHS):
    salary_note = None
    # Income lands on the thin everyday ledger…
    f.tx(EVERYDAY_GEL, 420_000, GEL, at(m, 5), category=SALARY, merchant=8,
         counterparty="North Studio", note=salary_note, key=f"demo:salary:{m}")
    # …and most of it leaves for the deposit the same week.
    f.move("SAVINGS", "To term deposit", at(m, 7), EVERYDAY_GEL, 255_000, GEL, DEPOSIT_GEL, 255_000, GEL)

    f.tx(EVERYDAY_GEL, -125_000, GEL, at(m, 6), category=RENT, merchant=7,
         counterparty="Demo Landlord", key=f"demo:rent:{m}")
    f.tx(EVERYDAY_GEL, -8_500 - (m % 4) * 1_150, GEL, at(m, 12), category=UTILITIES, merchant=5,
         counterparty="City Energy", key=f"demo:utilities:{m}")
    f.tx(EVERYDAY_GEL, -2_490, GEL, at(m, 15), category=SUBSCRIPTIONS, merchant=6,
         counterparty="Quiet Stream", key=f"demo:stream:{m}")

    # An FX card charge: the bank already converted, so the ledger row is in GEL and keeps the original.
    f.tx(EVERYDAY_GEL, -3_510, GEL, at(m, 16), category=SUBSCRIPTIONS, merchant=16,
         counterparty="Northwind Hosting", orig_minor=1_299, orig_currency=USD,
         key=f"demo:hosting:{m}", posted=at(m, 17))

    # The card runs low before the second half of the month, so the deposit tops it up.
    f.move("SAVINGS", "Top up before purchases", at(m, 18), DEPOSIT_GEL, 70_000, GEL,
           EVERYDAY_GEL, 70_000, GEL)

    for day, merchant, minor in GROCERY_RUN:
        f.tx(EVERYDAY_GEL, -minor - (m * 37) % 900, GEL, at(m, day), category=GROCERIES,
             merchant=merchant, counterparty=dict(MERCHANTS_BY_ID := {i: n for i, _, n, _ in MERCHANTS})[merchant],
             key=f"demo:groceries:{m}:{day}")
    for day, merchant, minor in EATING_RUN:
        f.tx(EVERYDAY_GEL, -minor - (m * 23) % 700, GEL, at(m, day), category=EATING,
             merchant=merchant, counterparty=MERCHANTS_BY_ID[merchant],
             key=f"demo:eating:{m}:{day}")

    f.tx(EVERYDAY_GEL, -1_500 - (m % 3) * 200, GEL, at(m, 9), category=TRANSPORT, merchant=4,
         counterparty="Lime Transit", key=f"demo:transport:{m}")

    if m % 2 == 0:
        f.tx(EVERYDAY_GEL, -4_600 - (m * 61) % 1_800, GEL, at(m, 21), category=HEALTH, merchant=10,
             counterparty="Willow Pharmacy", key=f"demo:pharmacy:{m}")
    if m % 3 == 1:
        f.tx(ATLAS_GEL, -12_800 - (m * 91) % 2_600, GEL, at(m, 22), category=HOME, merchant=11,
             counterparty="Clay Home Store", key=f"demo:home:{m}")
    if m % 4 == 2:
        f.tx(EVERYDAY_GEL, -8_900, GEL, at(m, 23), category=TECH, merchant=14,
             counterparty="Device Workshop", key=f"demo:tech:{m}")

    # Cash comes out of the everyday ledger, never out of the deposit.
    if m % 2 == 1:
        f.move("TRANSFER", "Cash withdrawal", at(m, 20), EVERYDAY_GEL, 20_000, GEL, CASH_GEL, 20_000, GEL)
        f.tx(CASH_GEL, -3_400 - (m * 53) % 900, GEL, at(m, 25), category=GROCERIES, merchant=2,
             counterparty="Sunroom Grocer", source="MANUAL", status="MANUAL",
             key=f"demo:cash-groceries:{m}")
        f.tx(CASH_GEL, -12_500 - (m * 71) % 1_500, GEL, at(m, 27), category=EATING, merchant=20,
             counterparty="Old Town Bistro", source="MANUAL", status="MANUAL",
             key=f"demo:cash-eating:{m}")

    # The deposit pays interest where the money actually sits.
    f.tx(DEPOSIT_GEL, 2_100 + m * 55, GEL, at(m, 28), category=INTEREST,
         note="Deposit interest", key=f"demo:interest:{m}")

    if m % 5 == 3:
        f.tx(ATLAS_GEL, 65_000, GEL, at(m, 14), category=SIDE, merchant=9,
             counterparty="Paper Plane Client", key=f"demo:side:{m}")

# Explicit conversions: lari into dollars, then straight into the dollar deposit.
for index, m in enumerate((1, 5, 9)):
    f.move("CONVERSION", "Currency exchange", at(m, 11),
           EVERYDAY_GEL, 135_000, GEL, EVERYDAY_USD, 50_000, USD)
    f.move("SAVINGS", "To dollar deposit", at(m, 13),
           EVERYDAY_USD, 45_000, USD, DEPOSIT_USD, 45_000, USD)

# A few purchases straight from the dollar ledger.
for index, (m, day, minor, merchant) in enumerate(((2, 19, 2_450, 17), (6, 8, 5_990, 17), (10, 26, 3_150, 14))):
    f.tx(EVERYDAY_USD, -minor, USD, at(m, day), category=OTHER if merchant == 17 else TECH,
         merchant=merchant, counterparty=MERCHANTS_BY_ID[merchant], key=f"demo:usd-buy:{index}")

# One travel month: lari to euro on the second bank, then euro spending.
TRAVEL_MONTH = 8
f.move("CONVERSION", "Currency exchange", at(TRAVEL_MONTH, 4),
       ATLAS_GEL, 90_000, GEL, TRAVEL_EUR, 29_500, EUR)
for index, (day, minor, merchant, category) in enumerate((
    (6, 12_000, 18, TRAVEL_CAT), (7, 4_250, 20, EATING), (8, 3_180, 19, TRAVEL_CAT),
    (9, 2_640, 20, EATING), (10, 5_500, 18, TRAVEL_CAT),
)):
    f.tx(TRAVEL_EUR, -minor, EUR, at(TRAVEL_MONTH, day), category=category, merchant=merchant,
         counterparty=MERCHANTS_BY_ID[merchant], key=f"demo:travel:{index}")

# The current month still has drafts waiting for a statement.
LAST = MONTHS - 1
pending_one = f.tx(EVERYDAY_GEL, -6_840, GEL, at(LAST, 27, hour=13), category=TECH, merchant=14,
                   counterparty="Device Workshop", status="PENDING", source="SMS",
                   key="demo:pending:1")
pending_two = f.tx(EVERYDAY_GEL, -1_270, GEL, at(LAST, 27, hour=16), category=EATING, merchant=15,
                   counterparty="Courtyard Coffee", status="PENDING", source="SMS",
                   key="demo:pending:2")

# Shared spending: a dinner split with a friend, and a gift bought together.
dinner = f.tx(EVERYDAY_GEL, -9_600, GEL, at(LAST, 12, hour=20), category=EATING, merchant=3,
              counterparty="Copper Table", key="demo:shared-dinner")
gift = f.tx(EVERYDAY_GEL, -14_000, GEL, at(LAST - 1, 18), category=GIFTS, merchant=13,
            counterparty="Marigold Flowers", key="demo:shared-gift")
f.allocations.append({"id": 1, "transactionId": dinner, "amountMinor": -4_800, "categoryId": EATING,
                      "personId": 1, "purpose": "SHARED", "note": None})
f.allocations.append({"id": 2, "transactionId": gift, "amountMinor": -14_000, "categoryId": GIFTS,
                      "personId": 2, "purpose": "GIFT", "note": None})

# --- debts ---------------------------------------------------------------

f.debt_cases.append({"id": 1, "personId": 1, "direction": "THEY_OWE_ME", "originalAmountMinor": 32_000,
                     "currency": GEL, "openedAt": at(LAST - 2, 9), "status": "OPEN",
                     "closedAt": None, "note": "Weekend trip"})
f.debt_cases.append({"id": 2, "personId": 3, "direction": "I_OWE_THEM", "originalAmountMinor": 18_000,
                     "currency": GEL, "openedAt": at(LAST - 1, 6), "status": "OPEN",
                     "closedAt": None, "note": "Concert ticket"})
f.debt_cases.append({"id": 3, "personId": 2, "direction": "THEY_OWE_ME", "originalAmountMinor": 25_000,
                     "currency": GEL, "openedAt": at(LAST - 4, 15), "status": "CLOSED",
                     "closedAt": at(LAST - 3, 2), "note": "Repaired laptop"})
f.debt_events.append({"id": 1, "debtCaseId": 1, "kind": "OPENED", "actualAmountMinor": None,
                      "actualCurrency": None, "accountId": None, "transactionId": None,
                      "debtValueMinor": 0, "closesCase": 0, "occurredAt": at(LAST - 2, 9), "note": None})
f.debt_events.append({"id": 2, "debtCaseId": 1, "kind": "SETTLEMENT", "actualAmountMinor": 12_000,
                      "actualCurrency": GEL, "accountId": CASH_GEL, "transactionId": None,
                      "debtValueMinor": 12_000, "closesCase": 0, "occurredAt": at(LAST - 1, 3), "note": None})
f.debt_events.append({"id": 3, "debtCaseId": 2, "kind": "OPENED", "actualAmountMinor": None,
                      "actualCurrency": None, "accountId": None, "transactionId": None,
                      "debtValueMinor": 0, "closesCase": 0, "occurredAt": at(LAST - 1, 6), "note": None})
f.debt_events.append({"id": 4, "debtCaseId": 3, "kind": "OPENED", "actualAmountMinor": None,
                      "actualCurrency": None, "accountId": None, "transactionId": None,
                      "debtValueMinor": 0, "closesCase": 0, "occurredAt": at(LAST - 4, 15), "note": None})
f.debt_events.append({"id": 5, "debtCaseId": 3, "kind": "SETTLEMENT", "actualAmountMinor": 25_000,
                      "actualCurrency": GEL, "accountId": EVERYDAY_GEL, "transactionId": None,
                      "debtValueMinor": 25_000, "closesCase": 1, "occurredAt": at(LAST - 3, 2), "note": None})

# --- statement history ---------------------------------------------------

EPOCH_DAY = datetime(1970, 1, 1, tzinfo=timezone.utc)


def epoch_day(index: int, day: int) -> int:
    return (month_start(index).replace(hour=0) + timedelta(days=day - 1) - EPOCH_DAY).days


f.imports.append({"id": 1, "accountId": EVERYDAY_GEL, "sourceId": 1,
                  "fileName": "demo-everyday-gel.xlsx", "origin": "FILE",
                  "periodFrom": epoch_day(0, 1), "periodTo": epoch_day(5, 28),
                  "openingBalanceMinor": 42_000, "closingBalanceMinor": 51_800,
                  "totalRows": 78, "inserted": 74, "duplicates": 4, "reconciled": 3,
                  "reviewCount": 0, "importedAt": at(6, 2)})
f.imports.append({"id": 2, "accountId": EVERYDAY_GEL, "sourceId": 1,
                  "fileName": "MYCREDO_STATEMENT_GEL.xlsx", "origin": "CREDO_SYNC",
                  "periodFrom": epoch_day(6, 1), "periodTo": epoch_day(LAST, 28),
                  "openingBalanceMinor": 51_800, "closingBalanceMinor": 60_400,
                  "totalRows": 82, "inserted": 79, "duplicates": 3, "reconciled": 4,
                  "reviewCount": 1, "importedAt": at(LAST, 28, hour=9)})
f.imports.append({"id": 3, "accountId": EVERYDAY_USD, "sourceId": 2,
                  "fileName": "MYCREDO_STATEMENT_USD.xlsx", "origin": "CREDO_SYNC",
                  "periodFrom": epoch_day(0, 1), "periodTo": epoch_day(LAST, 28),
                  "openingBalanceMinor": 4_500, "closingBalanceMinor": 8_900,
                  "totalRows": 12, "inserted": 12, "duplicates": 0, "reconciled": 0,
                  "reviewCount": 0, "importedAt": at(LAST, 28, hour=9)})
f.imports.append({"id": 4, "accountId": DEPOSIT_GEL, "sourceId": 3,
                  "fileName": "demo-deposit-gel.xlsx", "origin": "FILE",
                  "periodFrom": epoch_day(0, 1), "periodTo": epoch_day(LAST, 28),
                  "openingBalanceMinor": 200_000, "closingBalanceMinor": 2_420_000,
                  "totalRows": 36, "inserted": 36, "duplicates": 0, "reconciled": 0,
                  "reviewCount": 0, "importedAt": at(LAST, 28, hour=9)})
f.issues.append({"id": 1, "accountId": EVERYDAY_GEL, "transactionId": pending_one, "importId": 2,
                 "state": "OPEN", "createdAt": at(LAST, 28, hour=9)})

# --- write ---------------------------------------------------------------

tables = {
    "financial_groups": f.groups,
    "wallet_addresses": f.addresses,
    "crypto_assets": f.assets,
    "accounts": f.accounts,
    "payment_instruments": f.instruments,
    "instrument_account_links": f.links,
    "transfer_groups": f.transfer_groups,
    "statement_sources": f.sources,
    "categories": f.categories,
    "merchants": f.merchants,
    "merchant_aliases": f.aliases,
    "people": f.people,
    "transactions": sorted(f.transactions, key=lambda row: row["id"]),
    "transaction_allocations": f.allocations,
    "debt_cases": f.debt_cases,
    "debt_events": f.debt_events,
    "statement_imports": f.imports,
    "reconciliation_issues": f.issues,
}

# --- validation ----------------------------------------------------------

# Restore writes these strings straight into enum columns, so a typo here becomes a crash on read.
ENUMS = {
    ("financial_groups", "type"): {"BANK", "WALLET"},
    ("accounts", "type"): {"BANK", "CASH", "SAVINGS", "CRYPTO", "PERSON"},
    ("accounts", "savingsMode"): {"FLEXIBLE_RESERVE", "GOAL", "TERM_DEPOSIT"},
    ("payment_instruments", "type"): {"PHYSICAL_CARD", "VIRTUAL_CARD"},
    ("statement_sources", "type"): {"ACCOUNT", "CARD"},
    ("statement_imports", "origin"): {"FILE", "CREDO_SYNC"},
    ("transfer_groups", "type"): {
        "TRANSFER", "CONVERSION", "CARD_TOPUP", "SAVINGS", "CRYPTO_SWAP", "CRYPTO_BRIDGE",
    },
    ("categories", "kind"): {"EXPENSE", "INCOME"},
    ("transactions", "status"): {"PENDING", "CONFIRMED", "MANUAL"},
    ("transactions", "source"): {"SMS", "STATEMENT", "MANUAL", "ADJUSTMENT", "CRYPTO"},
    ("people", "role"): {"PARTNER", "FAMILY", "FRIEND", "COLLEAGUE", "OTHER"},
    ("transaction_allocations", "purpose"): {"PERSONAL", "SHARED", "GIFT", "LOAN", "REPAYMENT"},
    ("debt_cases", "direction"): {"THEY_OWE_ME", "I_OWE_THEM"},
    ("debt_cases", "status"): {"OPEN", "CLOSED"},
    ("debt_events", "kind"): {"OPENED", "SETTLEMENT", "ADJUSTMENT", "CLOSED"},
    ("reconciliation_issues", "state"): {"OPEN", "KEPT"},
}


def validate(tables: dict[str, list[dict]]) -> None:
    for (table, column), allowed in ENUMS.items():
        for row in tables[table]:
            value = row.get(column)
            if value is not None and value not in allowed:
                raise SystemExit(f"{table}.{column}: unknown enum value {value!r}")

    ids = {name: {row["id"] for row in rows} for name, rows in tables.items() if rows and "id" in rows[0]}
    for row in tables["accounts"]:
        assert row["groupId"] is None or row["groupId"] in ids["financial_groups"]
        assert row["walletAddressId"] is None or row["walletAddressId"] in ids["wallet_addresses"]
        assert row["cryptoAssetId"] is None or row["cryptoAssetId"] in ids["crypto_assets"]
        assert row["iban"] is None or row["iban"].startswith("GE00"), "public fixtures use the GE00 checksum"
    for row in tables["transactions"]:
        assert row["accountId"] in ids["accounts"]
        assert row["categoryId"] is None or row["categoryId"] in ids["categories"]
        assert row["merchantId"] is None or row["merchantId"] in ids["merchants"]
        assert row["transferGroupId"] is None or row["transferGroupId"] in ids["transfer_groups"]
    for row in tables["transactions"]:
        booked = row["gelValueMinor"] is not None
        assert booked == (row["currency"] != "GEL"), "only foreign rows carry a booked lari value"
    keys = [row["externalKey"] for row in tables["transactions"]]
    assert len(keys) == len(set(keys)), "external keys must stay unique"
    for row in tables["transaction_allocations"]:
        assert row["transactionId"] in ids["transactions"]
        assert row["personId"] is None or row["personId"] in ids["people"]
    for row in tables["debt_events"]:
        assert row["debtCaseId"] in ids["debt_cases"]
    for row in tables["payment_instruments"]:
        assert len(row["last4"]) == 4 and row["last4"].startswith("000"), "cards stay in the 000x range"
    # Both legs of every group must exist, or the feed would show a one-sided transfer.
    legs: dict[int, int] = {}
    for row in tables["transactions"]:
        if row["transferGroupId"] is not None:
            legs[row["transferGroupId"]] = legs.get(row["transferGroupId"], 0) + 1
    for group in tables["transfer_groups"]:
        assert legs.get(group["id"]) == 2, f"transfer group {group['id']} is not a pair"


validate(tables)

document = {
    "format": "whfin-backup",
    "schemaVersion": 1,
    "exportedAt": EXPORTED_AT,
    "appVersion": "0.1.0-demo (1)",
    "databaseVersion": DATABASE_VERSION,
    "primaryCurrency": GEL,
    "tables": tables,
}

OUT.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# --- report --------------------------------------------------------------

balances: dict[int, int] = {}
for row in f.transactions:
    balances[row["accountId"]] = balances.get(row["accountId"], 0) + row["amountMinor"]
names = {row["id"]: (row["name"], row["currency"]) for row in f.accounts}
print(f"rows: {sum(len(v) for v in tables.values())}, transactions: {len(f.transactions)}")
months = {datetime.fromtimestamp(r["occurredAt"] / 1000, timezone.utc).strftime("%Y-%m")
          for r in f.transactions if r["source"] != "ADJUSTMENT"}
print(f"months: {len(months)}  transfer groups: {len(f.transfer_groups)}")
for account, total in sorted(balances.items()):
    name, currency = names[account]
    print(f"  {account:>2} {name:<14} {total / 100:>12,.2f} {currency}")
