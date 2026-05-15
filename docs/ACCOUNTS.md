# Account types in Pledger.io

Pledger.io separates **your accounts** (assets you own and track balances for) from **counterparty accounts** (other parties in transactions). The Android app loads the list of owned types from `GET /v2/api/account-types` and always offers **creditor** and **debtor** for counterparties.

## Categories

| Category | Purpose | Used in transactions |
|----------|---------|---------------------|
| **Owned** | Banks, wallets, cards, cash you control | Source/target for transfers; expense from an owned account; income to an owned account |
| **Counterparty** | People and organisations you pay or receive from | Expense **to** creditor; income **from** debtor |

## Owned account types (typical API values)

| Type code | Display name | Role |
|-----------|----------------|------|
| `default` | Checking | Primary current account for daily spending |
| `joined` | Joint checking | Shared household current account |
| `savings` | Savings | Reserved funds, goals, emergency buffer |
| `joined_savings` | Joint savings | Shared savings |
| `credit_card` | Credit card | Revolving credit; balance often negative |
| `cash` | Cash | Physical cash on hand |

Additional types may appear per server configuration (e.g. `loan`, `mortgage`, `investment`).

## Counterparty types (fixed)

| Type code | Display name | Role in Pledger |
|-----------|----------------|-----------------|
| `creditor` | Creditor | Who you **pay** on an expense (supermarket, landlord, tax office) |
| `debtor` | Debtor | Who **pays you** on income (employer, customer, friend) |

> API label `debit` is treated as `debtor` for compatibility.

## How types map to transactions

| Transaction | From account | To account |
|-------------|--------------|------------|
| **Expense** (CREDIT) | Owned account | Creditor (or owned for internal) |
| **Income** (DEBIT) | Debtor | Owned account |
| **Transfer** | Owned account | Owned account |

## Managing accounts in the app

- **Accounts tab** — **Owned** accounts load in full (with balances). **Parties** load in pages of 50 with search and infinite scroll, so large counterparty lists stay fast. **All** shows owned accounts plus a card to browse parties.
- **Add (+)** — pick the account type first, then complete the form (fields adapt to the category).
- **Account detail** — balance, type explanation, transactions; edit or delete the account.

## API references

- List types: `GET /v2/api/account-types`
- List/search accounts: `GET /v2/api/accounts?type=…&offset=…&numberOfResults=…&accountName=…`
  - **Owned list / sync / dashboard:** owned types only (from `account-types`, excluding creditor/debtor).
  - **Parties tab:** `type=creditor,debtor,debit` with pagination and optional `accountName` search.
- Create/update: `POST` / `PUT /v2/api/accounts` with `type` set to the type code
