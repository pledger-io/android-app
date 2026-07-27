# Design: Reports drill-downs + month-over-month comparisons

**Status:** Approved for implementation  
**Branch:** `cursor/reports-drilldowns-comparisons-25b7`  
**Related:** `docs/REPORTS_OVERVIEW.md`, ADR-017

## Problem

Reports shows a useful month snapshot, but:

1. Rows are display-only — users cannot open category transactions, an account, or a budget group from a report.
2. There is no comparison to the previous month (docs already list “compare previous month (%)” as next).
3. “Drill-down” in the UI today only means switching report-type chips.

## Goals

| Goal | Acceptance |
|------|------------|
| MoM comparison on Overview | Net cash flow shows Δ amount and % vs previous month when prior data exists |
| Optional category MoM | Top categories can show a small %/Δ vs prior month (best-effort by label) |
| Category → transactions | Tap category row → Transactions with that category (+ optional month window) |
| Account → detail | Tap account balance row → Account detail |
| Budget → expense txs | Tap budget performance row → Transactions filtered by expense group (existing pattern) |
| Keep chip detail views | Existing report types remain; lists become clickable where IDs exist |

## Non-goals

- Chart SDK (Vico), donuts, YTD/quarter range, CSV/PDF export
- New `pledger://reports` deep link
- Room-backed report cache
- Changing income/expense aggregation semantics

## Design

### Domain model enrichment

Extend report row models with optional navigation ids (nullable when unresolved):

```kotlin
data class PartitionAmount(
    val label: String,
    val amount: Double,
    val id: Long? = null, // categoryId or accountId depending on report
)

data class BudgetPerformanceItem(
    val name: String,
    val spent: Double,
    val budgeted: Double,
    val expenseId: Long? = null,
)

data class MonthDelta(
    val absolute: Double,
    val percent: Double?, // null if prior baseline is 0 / undefined
)

data class IncomeExpenseSummary(
    val income: Double,
    val expense: Double,
    // computed helpers ok on UI side: net = income - expense
)
```

**Category ids:** After partitioned balance by name, resolve ids via `CategoryRepository` (refresh/search cache) matching label → id. Unmatched → `id = null` (row still shown, not clickable or click no-ops).

**Account ids:** When building account partitions from owned accounts, set `id = account.id` (today only name is kept).

**Budget expense ids:** When mapping from `Budget` / expense groups, set `expenseId = budget.id`.

### MoM comparison

On Overview load for month `M`:

1. Load current overview as today (parallel).
2. Also load previous month `M.minusMonths(1)` income/expense (and optionally category breakdown) in parallel.
3. Compute for **net** = income − expense:
   - `absolute = currentNet - priorNet`
   - `percent = absolute / |priorNet|` when `priorNet != 0`, else null
4. Surface on Overview hero (localized “vs previous month”).
5. Failures for prior month are soft: hide MoM row rather than failing the whole Overview.
6. Cache: either extend `ReportsOverview` with optional `priorIncomeExpense` / deltas, or compute in VM after two loads. Prefer storing prior summary on the overview snapshot so SWR cache still works — invalidate month `M` as today (prior month cache can be separate get-or-fetch).

**Do not** require a second full five-call overview for prior month; income/expense (+ optional categories) is enough for this slice.

### Navigation

#### Transactions route

Extend `Screen.Transactions` query args (keep expense args):

- `categoryId` (Long, default -1)
- `categoryName` (String, default "")
- `year` / `month` (Int, default -1) — when set, initialize Transactions month to that `YearMonth`

`TransactionsViewModel` already supports `selectedCategory` filters; apply SavedStateHandle category the same way as expense deep-link. When year/month provided, set `currentMonth` initially.

#### Reports → NavGraph

Add callbacks from `ReportsScreen`:

- `onCategoryClick(categoryId, categoryName, yearMonth)`
- `onAccountClick(accountId)`
- `onBudgetExpenseClick(expenseId, expenseName, yearMonth)`

Wire in `NavGraph` using existing `Screen.Transactions.createRoute` / `Screen.AccountDetail.createRoute`.

### UI

- Overview hero: MoM Δ line under net (green/red by sign; use existing income/expense colors).
- `PartitionList` / budget list: `Modifier.clickable` when id present; contentDescription for a11y.
- Overview top categories: same click behavior.
- Non-clickable when id null (no dead affordance).

### Strings

en / nl / de for MoM labels (“vs previous month”, “+%1$s”, “-%1$s”, “n/a”), and any new content descriptions.

### Docs

- Update `REPORTS_OVERVIEW.md`: move MoM + tap-to-filter from “Future” into implemented behaviour.
- Brief note in ADR-017 consequences if useful.

### Tests

| Area | Cases |
|------|--------|
| Delta helper | prior 0 → percent null; normal %; sign |
| Repository | account partitions include ids; budget items include expenseId; category id resolution when categories available |
| Transactions VM | category + month from SavedStateHandle |
| Reports VM | prior-month soft failure leaves overview; MoM populated when prior success |
| Route builders | transactions createRoute encodes category/year/month |

## Implementation order

1. Models + pure `MonthDelta` helper  
2. Repository id enrichment  
3. Transactions route + VM init  
4. Reports overview MoM load + UI  
5. Clickable lists + NavGraph wiring  
6. Strings + docs + unit tests  
7. `testDebugUnitTest`, `lintDebug`, `assembleDebug`

## Risks

- **Category name mismatch** between balance partition labels and category list → no id, no click (acceptable).
- **Extra network** for prior month on every overview — mitigated by overview cache + soft fail; prior income/expense can reuse cache if we key by month.
- **Transactions filter UX** when arriving with category — ensure filters expanded / chip visible like expense deep-link.
