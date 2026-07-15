#!/usr/bin/env node

import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

// Public fixture only: keep IBANs on the invalid GE00 checksum and cards in the 000x range.
// Never copy rows, names, identifiers, or amounts from a physical device or private statement.

const outputPath = resolve(process.argv[2] ?? "app/src/main/assets/whfin-demo-v4.json");
const anchor = process.argv[3] ?? "2026-07-15";

if (!/^\d{4}-\d{2}-\d{2}$/.test(anchor)) {
  throw new Error("Anchor must use YYYY-MM-DD.");
}

const anchorDate = new Date(`${anchor}T12:00:00.000Z`);
if (Number.isNaN(anchorDate.valueOf())) throw new Error("Anchor is not a valid date.");

const tables = {
  financial_groups: [],
  wallet_addresses: [],
  crypto_assets: [],
  accounts: [],
  payment_instruments: [],
  instrument_account_links: [],
  transfer_groups: [],
  statement_sources: [],
  categories: [],
  merchants: [],
  merchant_aliases: [],
  people: [],
  transactions: [],
  transaction_allocations: [],
  debt_cases: [],
  debt_events: [],
  statement_imports: [],
  reconciliation_issues: [],
};

const monthDate = (offset, day, hour = 12) => {
  const value = new Date(Date.UTC(
    anchorDate.getUTCFullYear(),
    anchorDate.getUTCMonth() + offset,
    day,
    hour,
  ));
  return value.valueOf();
};
const epochDay = (offset, day) => Math.floor(monthDate(offset, day, 0) / 86_400_000);
const monthKey = (offset) => new Date(monthDate(offset, 1)).toISOString().slice(0, 7);

tables.financial_groups.push(
  { id: 1, name: "Credo Demo", type: "BANK", provider: "Credo", isArchived: 0, sortOrder: 0 },
  { id: 2, name: "Atlas Bank", type: "BANK", provider: "Atlas", isArchived: 0, sortOrder: 1 },
);

tables.accounts.push(
  { id: 1, name: "Everyday", type: "BANK", groupId: 1, currency: "GEL", iban: "GE00CD0000000000000001", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 0 },
  { id: 2, name: "Everyday", type: "BANK", groupId: 1, currency: "USD", iban: "GE00CD0000000000000001", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 1 },
  { id: 3, name: "Hot deposit", type: "SAVINGS", groupId: 1, currency: "GEL", iban: "GE00CD0000000000000002", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: "FLEXIBLE_RESERVE", isArchived: 0, sortOrder: 2 },
  { id: 4, name: "Mountain fund", type: "SAVINGS", groupId: 1, currency: "GEL", iban: "GE00CD0000000000000003", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: 1500000, savingsMode: "GOAL", isArchived: 0, sortOrder: 3 },
  { id: 5, name: "Everyday", type: "BANK", groupId: 2, currency: "GEL", iban: "GE00AT0000000000000001", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 4 },
  { id: 6, name: "Travel", type: "BANK", groupId: 2, currency: "EUR", iban: "GE00AT0000000000000001", walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 5 },
  { id: 7, name: "Pocket money", type: "CASH", groupId: null, currency: "GEL", iban: null, walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 6 },
  { id: 8, name: "Travel cash", type: "CASH", groupId: null, currency: "EUR", iban: null, walletAddressId: null, cryptoAssetId: null, savingsGoalMinor: null, savingsMode: null, isArchived: 0, sortOrder: 7 },
);

tables.payment_instruments.push(
  { id: 1, groupId: 1, type: "PHYSICAL_CARD", last4: "0001", label: "Everyday card", isArchived: 0 },
  { id: 2, groupId: 1, type: "VIRTUAL_CARD", last4: "0002", label: "Online card", isArchived: 0 },
  { id: 3, groupId: 2, type: "PHYSICAL_CARD", last4: "0003", label: "Travel card", isArchived: 0 },
);
tables.instrument_account_links.push(
  { instrumentId: 1, accountId: 1 },
  { instrumentId: 1, accountId: 2 },
  { instrumentId: 2, accountId: 1 },
  { instrumentId: 2, accountId: 2 },
  { instrumentId: 3, accountId: 5 },
  { instrumentId: 3, accountId: 6 },
);

tables.statement_sources.push(
  { id: 1, groupId: 1, type: "ACCOUNT", accountId: 1, instrumentId: null, label: "Demo everyday GEL" },
  { id: 2, groupId: 1, type: "ACCOUNT", accountId: 2, instrumentId: null, label: "Demo everyday USD" },
  { id: 3, groupId: 1, type: "ACCOUNT", accountId: 3, instrumentId: null, label: "Demo hot deposit" },
  { id: 4, groupId: 2, type: "ACCOUNT", accountId: 5, instrumentId: null, label: "Demo Atlas GEL" },
  { id: 5, groupId: 2, type: "CARD", accountId: null, instrumentId: 3, label: "Atlas card •0003" },
);

const categoryRows = [
  [1, "unaccounted", "EXPENSE", "HelpOutline", 0xff9e9e9e, 999, 1],
  [2, "Groceries", "EXPENSE", "ShoppingCart", 0xff4f725f, 0, 0],
  [3, "Eating out", "EXPENSE", "Restaurant", 0xffc5684f, 1, 0],
  [4, "Rent", "EXPENSE", "Home", 0xff68729b, 2, 0],
  [5, "Utilities", "EXPENSE", "Bolt", 0xff55968a, 3, 0],
  [6, "Transport", "EXPENSE", "DirectionsBus", 0xff5f89a8, 4, 0],
  [7, "Subscriptions", "EXPENSE", "Subscriptions", 0xff936c9c, 5, 0],
  [8, "Health", "EXPENSE", "MedicalServices", 0xffb85c5c, 6, 0],
  [9, "Gifts", "EXPENSE", "CardGiftcard", 0xffc8953e, 7, 0],
  [10, "Home", "EXPENSE", "Chair", 0xff789353, 8, 0],
  [11, "Tech", "EXPENSE", "Devices", 0xff7664a6, 9, 0],
  [12, "Other", "EXPENSE", "Category", 0xff7f8789, 10, 0],
  [13, "Salary", "INCOME", "Payments", 0xff5f8b64, 0, 0],
  [14, "Side income", "INCOME", "Work", 0xff7e9a55, 1, 0],
  [15, "Interest", "INCOME", "Percent", 0xff3c8f91, 2, 0],
];
for (const [id, name, kind, icon, color, sortOrder, isSystem] of categoryRows) {
  tables.categories.push({ id, name, parentId: null, kind, icon, color: color | 0, isSystem, sortOrder });
}

const merchantRows = [
  [1, "juniper market", "Juniper Market", 2],
  [2, "sunroom grocer", "Sunroom Grocer", 2],
  [3, "copper table", "Copper Table", 3],
  [4, "lime transit", "Lime Transit", 6],
  [5, "city energy demo", "City Energy", 5],
  [6, "quiet stream", "Quiet Stream", 7],
  [7, "demo landlord", "Demo Landlord", 4],
  [8, "north studio payroll", "North Studio", 13],
  [9, "paper plane client", "Paper Plane Client", 14],
  [10, "willow pharmacy", "Willow Pharmacy", 8],
  [11, "clay home store", "Clay Home Store", 10],
  [12, "small bookshop", "Small Bookshop", 12],
  [13, "marigold flowers", "Marigold Flowers", 9],
  [14, "device workshop", "Device Workshop", 11],
  [15, "courtyard coffee", "Courtyard Coffee", 3],
];
for (const [id, normalizedKey, displayName, categoryId] of merchantRows) {
  tables.merchants.push({ id, normalizedKey, displayName, categoryId });
}
tables.merchant_aliases.push(
  { id: 1, merchantId: 1, pattern: "juniper market demo" },
  { id: 2, merchantId: 3, pattern: "copper table old town" },
  { id: 3, merchantId: 8, pattern: "north studio salary" },
  { id: 4, merchantId: 15, pattern: "courtyard coffee demo" },
);

tables.people.push(
  { id: 1, name: "Nino", role: "FRIEND", color: 0xffc5684f | 0, isArchived: 0 },
  { id: 2, name: "Luka", role: "FAMILY", color: 0xff4f725f | 0, isArchived: 0 },
  { id: 3, name: "Maya", role: "COLLEAGUE", color: 0xff68729b | 0, isArchived: 0 },
);

let transactionId = 0;
let transferGroupId = 0;
const addTransaction = ({
  accountId,
  amountMinor,
  currency,
  occurredAt,
  merchantId = null,
  rawCounterparty = null,
  categoryId = null,
  note = null,
  status = "CONFIRMED",
  source = "STATEMENT",
  groupId = null,
  isTransfer = 0,
  origAmountMinor = null,
  origCurrency = null,
  externalKey,
}) => {
  transactionId += 1;
  tables.transactions.push({
    id: transactionId,
    accountId,
    amountMinor,
    currency,
    origAmountMinor,
    origCurrency,
    occurredAt,
    postedAt: source === "STATEMENT" ? occurredAt + 86_400_000 : null,
    merchantId,
    rawCounterparty,
    counterpartyIban: null,
    categoryId,
    note,
    status,
    source,
    transferGroupId: groupId,
    isTransfer,
    balanceAfterMinor: null,
    externalKey,
    createdAt: occurredAt + 3_600_000,
  });
  return transactionId;
};
const addTransfer = ({ type, note, occurredAt, fromAccount, fromAmount, fromCurrency, toAccount, toAmount, toCurrency, key }) => {
  transferGroupId += 1;
  tables.transfer_groups.push({ id: transferGroupId, type, note, createdAt: occurredAt });
  addTransaction({ accountId: fromAccount, amountMinor: -fromAmount, currency: fromCurrency, occurredAt, rawCounterparty: note, groupId: transferGroupId, isTransfer: 1, externalKey: `${key}:out` });
  addTransaction({ accountId: toAccount, amountMinor: toAmount, currency: toCurrency, occurredAt: occurredAt + 30_000, rawCounterparty: note, groupId: transferGroupId, isTransfer: 1, externalKey: `${key}:in` });
};

const openingBalances = [
  [1, 265000, "GEL"],
  [2, 32000, "USD"],
  [3, 210000, "GEL"],
  [4, 400000, "GEL"],
  [5, 125000, "GEL"],
  [6, 18000, "EUR"],
  [7, 9000, "GEL"],
  [8, 6500, "EUR"],
];
for (const [accountId, amountMinor, currency] of openingBalances) {
  addTransaction({
    accountId,
    amountMinor,
    currency,
    occurredAt: monthDate(-18, 1),
    categoryId: 1,
    note: "Demo opening balance",
    status: "CONFIRMED",
    source: "ADJUSTMENT",
    externalKey: `demo:opening:${accountId}`,
  });
}

const groceries = [52000, 61000, 57000, 66000, 72000, 69000, 75000, 81000, 78000, 84000, 91000, 86000];
const dining = [21000, 26000, 19000, 34000, 28000, 32000, 39000, 35000, 41000, 36000, 45000, 42000];
const utilities = [18000, 19500, 21000, 23500, 26000, 29000, 31000, 27500, 24000, 22500, 20500, 19000];
const transport = [11000, 12500, 12000, 13500, 14500, 16000, 17500, 15500, 15000, 16500, 18000, 17000];
const extras = [12000, 18000, 9500, 24000, 14000, 32000, 17000, 27000, 15000, 36000, 21000, 28000];

for (let index = 0; index < 12; index += 1) {
  const offset = index - 11;
  const key = monthKey(offset);
  addTransaction({ accountId: 1, amountMinor: 480000, currency: "GEL", occurredAt: monthDate(offset, 2, 9), merchantId: 8, rawCounterparty: "NORTH STUDIO PAYROLL", categoryId: 13, externalKey: `demo:${key}:salary` });
  addTransaction({ accountId: 1, amountMinor: -145000, currency: "GEL", occurredAt: monthDate(offset, 3, 10), merchantId: 7, rawCounterparty: "DEMO LANDLORD", categoryId: 4, externalKey: `demo:${key}:rent` });
  addTransaction({ accountId: 1, amountMinor: -groceries[index], currency: "GEL", occurredAt: monthDate(offset, 4, 18), merchantId: index % 3 === 0 ? 2 : 1, rawCounterparty: index % 3 === 0 ? "SUNROOM GROCER" : "JUNIPER MARKET", categoryId: 2, externalKey: `demo:${key}:groceries` });
  addTransaction({ accountId: 1, amountMinor: -transport[index], currency: "GEL", occurredAt: monthDate(offset, 6, 8), merchantId: 4, rawCounterparty: "LIME TRANSIT", categoryId: 6, externalKey: `demo:${key}:transport` });
  const diningId = addTransaction({ accountId: 1, amountMinor: -dining[index], currency: "GEL", occurredAt: monthDate(offset, 8, 20), merchantId: 3, rawCounterparty: "COPPER TABLE", categoryId: 3, externalKey: `demo:${key}:dining` });
  addTransaction({ accountId: 1, amountMinor: -utilities[index], currency: "GEL", occurredAt: monthDate(offset, 10, 11), merchantId: 5, rawCounterparty: "CITY ENERGY DEMO", categoryId: 5, externalKey: `demo:${key}:utilities` });
  addTransaction({ accountId: 1, amountMinor: -4900, currency: "GEL", occurredAt: monthDate(offset, 12, 7), merchantId: 6, rawCounterparty: "QUIET STREAM", categoryId: 7, externalKey: `demo:${key}:subscription` });

  const extraCategory = [8, 9, 10, 11][index % 4];
  const extraMerchant = { 8: 10, 9: 13, 10: 11, 11: 14 }[extraCategory];
  addTransaction({ accountId: 1, amountMinor: -extras[index], currency: "GEL", occurredAt: monthDate(offset, 14, 16), merchantId: extraMerchant, rawCounterparty: tables.merchants[extraMerchant - 1].displayName.toUpperCase(), categoryId: extraCategory, externalKey: `demo:${key}:extra` });

  addTransfer({ type: "SAVINGS", note: "To Hot deposit", occurredAt: monthDate(offset, 11, 14), fromAccount: 1, fromAmount: 65000 + index * 1500, fromCurrency: "GEL", toAccount: 3, toAmount: 65000 + index * 1500, toCurrency: "GEL", key: `demo:${key}:saving` });

  if (index % 3 === 1) {
    addTransaction({ accountId: 5, amountMinor: 78000 + index * 2500, currency: "GEL", occurredAt: monthDate(offset, 17, 13), merchantId: 9, rawCounterparty: "PAPER PLANE CLIENT", categoryId: 14, externalKey: `demo:${key}:side-income` });
  }

  if (index === 11) {
    tables.transaction_allocations.push(
      { id: 1, transactionId: diningId, amountMinor: -21000, categoryId: 3, personId: null, purpose: "PERSONAL", note: null },
      { id: 2, transactionId: diningId, amountMinor: -21000, categoryId: 3, personId: 1, purpose: "SHARED", note: "Dinner together" },
    );
  }
}

const anchorKey = anchor.slice(0, 7);
addTransfer({ type: "TRANSFER", note: "Cash withdrawal", occurredAt: monthDate(0, 13, 17), fromAccount: 1, fromAmount: 20000, fromCurrency: "GEL", toAccount: 7, toAmount: 20000, toCurrency: "GEL", key: `demo:${anchorKey}:cash` });
addTransfer({ type: "CONVERSION", note: "Trip money", occurredAt: monthDate(0, 9, 15), fromAccount: 1, fromAmount: 27500, fromCurrency: "GEL", toAccount: 2, toAmount: 10000, toCurrency: "USD", key: `demo:${anchorKey}:conversion` });
addTransfer({ type: "TRANSFER", note: "Mountain fund", occurredAt: monthDate(0, 7, 12), fromAccount: 3, fromAmount: 150000, fromCurrency: "GEL", toAccount: 4, toAmount: 150000, toCurrency: "GEL", key: `demo:${anchorKey}:goal` });

addTransaction({ accountId: 7, amountMinor: -1850, currency: "GEL", occurredAt: monthDate(0, 15, 8), rawCounterparty: "Morning bakery", categoryId: 3, note: "Breakfast", status: "MANUAL", source: "MANUAL", externalKey: `demo:${anchorKey}:cash-breakfast` });
const pendingCoffeeId = addTransaction({ accountId: 1, amountMinor: -1270, currency: "GEL", occurredAt: monthDate(0, 15, 9), merchantId: 15, rawCounterparty: "COURTYARD COFFEE", categoryId: 3, status: "PENDING", source: "SMS", externalKey: `demo:${anchorKey}:pending-coffee` });
addTransaction({ accountId: 1, amountMinor: -6840, currency: "GEL", origAmountMinor: -2499, origCurrency: "USD", occurredAt: monthDate(0, 15, 10), merchantId: 14, rawCounterparty: "DEVICE WORKSHOP", categoryId: 11, status: "PENDING", source: "SMS", externalKey: `demo:${anchorKey}:pending-fx` });
addTransaction({ accountId: 7, amountMinor: -4200, currency: "GEL", occurredAt: monthDate(0, 15, 12), merchantId: 12, rawCounterparty: "SMALL BOOKSHOP", categoryId: 12, note: "Notebook", status: "MANUAL", source: "MANUAL", externalKey: `demo:${anchorKey}:notebook` });
addTransaction({ accountId: 3, amountMinor: 1850, currency: "GEL", occurredAt: monthDate(0, 1, 8), rawCounterparty: "Deposit interest", categoryId: 15, status: "CONFIRMED", source: "STATEMENT", externalKey: `demo:${anchorKey}:interest` });

tables.debt_cases.push(
  { id: 1, personId: 1, direction: "THEY_OWE_ME", originalAmountMinor: 32000, currency: "GEL", openedAt: monthDate(-2, 20), status: "OPEN", closedAt: null, note: "Weekend trip" },
  { id: 2, personId: 2, direction: "I_OWE_THEM", originalAmountMinor: 15000, currency: "GEL", openedAt: monthDate(-1, 6), status: "OPEN", closedAt: null, note: "Shared tickets" },
  { id: 3, personId: 3, direction: "THEY_OWE_ME", originalAmountMinor: 20000, currency: "GEL", openedAt: monthDate(-5, 11), status: "CLOSED", closedAt: monthDate(-4, 2), note: "Team lunch" },
);
tables.debt_events.push(
  { id: 1, debtCaseId: 1, kind: "OPENED", actualAmountMinor: null, actualCurrency: null, accountId: null, transactionId: null, debtValueMinor: 0, closesCase: 0, occurredAt: monthDate(-2, 20), note: null },
  { id: 2, debtCaseId: 1, kind: "SETTLEMENT", actualAmountMinor: 12000, actualCurrency: "GEL", accountId: 7, transactionId: null, debtValueMinor: 12000, closesCase: 0, occurredAt: monthDate(-1, 18), note: "Cash returned" },
  { id: 3, debtCaseId: 2, kind: "OPENED", actualAmountMinor: null, actualCurrency: null, accountId: null, transactionId: null, debtValueMinor: 0, closesCase: 0, occurredAt: monthDate(-1, 6), note: null },
  { id: 4, debtCaseId: 3, kind: "OPENED", actualAmountMinor: null, actualCurrency: null, accountId: null, transactionId: null, debtValueMinor: 0, closesCase: 0, occurredAt: monthDate(-5, 11), note: null },
  { id: 5, debtCaseId: 3, kind: "CLOSED", actualAmountMinor: 20000, actualCurrency: "GEL", accountId: 1, transactionId: null, debtValueMinor: 20000, closesCase: 1, occurredAt: monthDate(-4, 2), note: "Paid in full" },
);

tables.statement_imports.push(
  { id: 1, accountId: 1, sourceId: 1, fileName: "demo-everyday-2025.xlsx", origin: "FILE", periodFrom: epochDay(-11, 1), periodTo: epochDay(-6, 28), openingBalanceMinor: 265000, closingBalanceMinor: 612400, totalRows: 51, inserted: 48, duplicates: 3, reconciled: 2, reviewCount: 0, importedAt: monthDate(-6, 28, 20) },
  { id: 2, accountId: 1, sourceId: 1, fileName: null, origin: "CREDO_SYNC", periodFrom: epochDay(-5, 1), periodTo: epochDay(-1, 28), openingBalanceMinor: 612400, closingBalanceMinor: 1042800, totalRows: 47, inserted: 44, duplicates: 3, reconciled: 3, reviewCount: 0, importedAt: monthDate(-1, 28, 20) },
  { id: 3, accountId: 2, sourceId: 2, fileName: null, origin: "CREDO_SYNC", periodFrom: epochDay(-5, 1), periodTo: epochDay(-1, 28), openingBalanceMinor: 32000, closingBalanceMinor: 32000, totalRows: 2, inserted: 2, duplicates: 0, reconciled: 0, reviewCount: 0, importedAt: monthDate(-1, 28, 20) },
  { id: 4, accountId: 1, sourceId: 1, fileName: "demo-current.xlsx", origin: "FILE", periodFrom: epochDay(0, 1), periodTo: epochDay(0, 15), openingBalanceMinor: 1042800, closingBalanceMinor: 1129950, totalRows: 14, inserted: 12, duplicates: 1, reconciled: 1, reviewCount: 1, importedAt: monthDate(0, 15, 18) },
);
tables.reconciliation_issues.push({ id: 1, accountId: 1, transactionId: pendingCoffeeId, importId: 4, state: "OPEN", createdAt: monthDate(0, 15, 18) });

const balances = new Map();
for (const transaction of [...tables.transactions].sort((left, right) => left.occurredAt - right.occurredAt || left.id - right.id)) {
  const balance = (balances.get(transaction.accountId) ?? 0) + transaction.amountMinor;
  balances.set(transaction.accountId, balance);
  if (transaction.source === "STATEMENT" || transaction.source === "SMS") transaction.balanceAfterMinor = balance;
}

const payload = {
  format: "whfin-backup",
  schemaVersion: 1,
  exportedAt: `${anchor}T18:00:00Z`,
  appVersion: "0.1.0-demo (1)",
  databaseVersion: 4,
  primaryCurrency: "GEL",
  tables,
};

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, JSON.stringify(payload, null, 2));
console.log(`Wrote ${tables.transactions.length} transactions and ${Object.values(tables).flat().length} total rows to ${outputPath}`);
