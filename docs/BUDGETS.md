# Budgets

Pledger.io budgets track **expected monthly income** and **expense groups** (labels such as Groceries or Bills). Transactions can be linked to an expense group when categorizing. The Android app supports creating the initial budget, viewing progress per group, and managing groups on device.

Backend contract: [pledger-io/rest-application](https://github.com/pledger-io/rest-application) (`src/contract/paths/budgets*.yaml`).

## Concepts

| Term | Meaning |
|------|---------|
| **Budget** | One monthly period with an expected net income |
| **Expense group** | A named bucket with a monthly budget cap (API: *expense*) |
| **Active month** | Current calendar month; `period.endDate` may be `null` until the period closes |

The list on the **Budgets** tab shows one card per expense group (spent vs budgeted), not separate “budget” entities per group.

## API (v2)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v2/api/budgets?year=&month=` | Budget for a month; **404** if none exists yet |
| `POST` | `/v2/api/budgets` | Create initial budget (`year`, `month`, `income`) — once per user |
| `PATCH` | `/v2/api/budgets` | Update current budget income |
| `GET` | `/v2/api/budgets/expenses` | Search expense groups by name |
| `PATCH` | `/v2/api/budgets/expenses` | Create or update an expense group |
| `GET` | `/v2/api/budgets/expenses/balance?year=&month=` | Spent amounts per group for the period |

### Create initial budget

```json
POST /v2/api/budgets
{ "year": 2026, "month": 5, "income": 3500.0 }
```

### Update budget income

```json
PATCH /v2/api/budgets
{ "year": 2026, "month": 5, "income": 3750.0 }
```

### Create / update expense group

```json
PATCH /v2/api/budgets/expenses
{ "name": "Groceries", "amount": 400.0 }
```

- **Create:** omit `id`. Server sets a narrow range (`amount - 0.01` … `amount`).
- **Update:** include `id`; only `amount` (monthly cap) is changed on the server — name is read-only in the app when editing.

There is **no delete** endpoint for expense groups in the current API.

### Period dates

`BudgetDto.period` uses `DateRangeDto`:

- `startDate` — always present
- `endDate` — `null` for the **active** month (open period)

## App behaviour

### Budgets tab (`BudgetsScreen`)

1. Loads current month via `GET /v2/api/budgets`.
2. **404** → inline **Start your first budget** form (year, month, net income) → `POST /v2/api/budgets`.
3. **200** with groups → monthly overview (expected income + spent vs budgeted) + cards; tap a card for detail.
4. Edit income from the overview → bottom sheet → `PATCH /v2/api/budgets`.
5. **200** with no groups → empty state; **FAB** or empty-state action adds the first expense group.
6. **FAB** (when a budget exists) → bottom sheet to add a new expense group (name + monthly budget).
7. Month navigation only shows Room cache when that month’s snapshot is marked fresh (`SyncKeys.budgetMonth`), avoiding a flash of the previously viewed month.

### Detail (`BudgetDetailScreen`)

- Progress for a single expense group (from cache by id).
- **Edit** in the top bar → bottom sheet to update the monthly budget (name fixed).

### Background sync

`SyncWorker` loads current-month budgets and notifies when any group exceeds **80%** spent. Skipped when no budget exists yet (`needsInitialSetup`).

## Code map

| Piece | Location |
|-------|----------|
| API | `PledgerApiService` |
| DTOs | `BudgetDto.kt` (`CreateBudgetRequest`, `ExpenseRequest`, …) |
| Repository | `BudgetRepositoryImpl` |
| Use cases | `CreateInitialBudgetUseCase`, `UpdateBudgetIncomeUseCase`, `SaveBudgetExpenseUseCase`, `GetBudgetsUseCase` |
| UI | `ui/budgets/*` |
| Routes | `budgets`, `budget/{budgetId}` |

## Product reference

[How to manage your budgets](https://www.pledger.io/how-to/your-finances/budgeting.html) — web UI copy for income and expense labels.
