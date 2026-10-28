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

Additional types may appear per server configuration (e.g. `investment`).

## Liability types (loans, mortgages, debts)

| Type code | Display name | Role |
|-----------|--------------|------|
| `loan` | Loan | A loan you repay over time |
| `mortgage` | Mortgage | Property financing |
| `debt` | Debt | Any other debt you are paying down |

- The backend keeps these types **hidden** from `GET /v2/api/account-types`, so the app always adds them to the owned type list itself (`AccountTypeCodes.liabilityTypeCodes`). They are owned accounts: listed under **Loans & liabilities**, included in dashboard net worth, search and the Account balances report. The server balance is shown as-is (negative = still owed).
- Liabilities carry an **interest rate** and **periodicity** (`MONTHS`/`YEARS`) and an optional account number, shown on the detail and editable in the form. The API stores the rate as a fraction (`0.035`); the form shows a percentage (`3.5`).
- **Starting amount owed** (create only): recorded like the web UI — a transaction from the liability to the server's `reconcile` account with description `Opening balance`. If that call fails the account still exists and the form stays open in edit mode.
- **Record payment** on the detail opens a transfer with the liability as destination. Liabilities are offered only as a **transfer destination**, never as a source or in income/expense own-account pickers; the backend then books the payment as an expense from the paying account.

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
| **Liability payment** (Transfer in the form) | Owned asset account | Loan / mortgage / debt |

## Managing accounts in the app

- **Accounts tab** — **All / Owned / Parties** filter chips are always shown at the top (including when a view is empty), so you can switch back from Parties without being stuck on an empty screen.
- **Owned** accounts load in full (with balances). **Parties** load in pages of 50 with search and infinite scroll, so large counterparty lists stay fast. **All** shows owned accounts plus a card to browse parties.
- **Add (+)** — pick the account type first, then complete the form (fields adapt to the category).
- **Account detail** — balance, type explanation, transactions; edit or delete the account.

## API references

- List types: `GET /v2/api/account-types`
- List/search accounts: `GET /v2/api/accounts?type=…&offset=…&numberOfResults=…&accountName=…`
  - **Owned list / sync / dashboard:** owned types only (from `account-types`, excluding creditor/debtor).
  - **Parties tab:** `type=creditor,debtor,debit` with pagination and optional `accountName` search.
- Create/update: `POST` / `PUT /v2/api/accounts` with `type` set to the type code
