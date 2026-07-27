# Reports — overview and UX design

**Status:** Implemented (2026-05); drill-downs + MoM (2026-07)  
**Related:** [ADR-017](adr/017-deep-links-and-reports.md), [USABILITY_IMPROVEMENT_PLAN.md](USABILITY_IMPROVEMENT_PLAN.md), [design/reports-drilldowns-comparisons.md](design/reports-drilldowns-comparisons.md)

## Problem

The Reports tab exposed five separate report types behind filter chips. Each view showed only one dataset, so users had to switch chips repeatedly to answer basic questions (“How am I doing this month?”, “Where did money go?”, “What do I own?”).

Additional gaps:

| Gap | Impact |
|-----|--------|
| No landing overview | High — no single-month snapshot |
| Income vs expense card | Misleading twin progress bars; no net/savings |
| Category / balance lists | No share-of-total; capped at 12 without total |
| Net worth | Long date list from full history; no trend shape |
| Hardcoded English in cards | i18n inconsistency |

## Goals

1. **At-a-glance month snapshot** on tab open (Overview).
2. **Actionable numbers**: net cash flow, total balances, budget health, top categories.
3. **Drill-down preserved** via existing report-type chips for detail.
4. **Lightweight visuals** — Canvas sparkline (no new chart SDK in this pass; Vico remains available for a follow-up).
5. **Month-over-month context** on net cash flow (and optional category %).
6. **Tap-to-filter**: category / account / budget rows open Transactions or Account detail when ids are known.

## Information architecture

```
Reports tab
├── Month navigator (all types)
├── Report type chips
│   ├── Overview (default)     ← parallel load, composite UI + MoM
│   ├── Income vs expenses
│   ├── Category breakdown     ← tap row → Transactions (category + month)
│   ├── Budget performance     ← tap row → Transactions (expense + month)
│   ├── Net worth
│   └── Account balance        ← tap row → Account detail
└── Pull-to-refresh / last updated
```

## Overview content (per selected month)

| Block | Data source | Purpose |
|-------|-------------|---------|
| Net cash flow hero | `getIncomeExpenseSummary` (+ prior month soft-fail) | Income − expenses; savings rate; MoM Δ/% vs previous month |
| Quick stats row | balances + budgets | Total assets; budgets on track vs over |
| Top categories | `getCategoryBreakdown` (top 5) + prior labels | Where spending concentrated; optional MoM %; tap → txs |
| Net worth mini chart | `getNetWorthTrend` filtered to month | Trend shape + latest value |
| Hint row | — | Points users to chips for full reports |

Detail report types reuse improved shared components (`IncomeExpenseCard`, `PartitionList`, `NetWorthSection`, `BudgetPerformanceList`). Rows are clickable when a navigation id is present (category id, account id, or expense id).

## Data loading

- **Overview:** `ReportsViewModel` loads five repository calls for the selected month in parallel (`async` + `awaitAll`), plus prior-month income/expense and category breakdown (soft-fail — MoM omitted on error). Partial success is allowed; error is shown only when every *current-month* call fails.
- **Net worth:** Points filtered client-side to the selected `YearMonth` (API returns daily series from 1970; UI previously showed arbitrary first 15 rows).
- **Ids:** Account partitions keep `account.id`; budget rows keep expense-group id; category labels are resolved to ids via `CategoryRepository` when the catalog is available (unmatched → not clickable).

## Overview cache (in-memory)

`ReportsOverviewCache` stores assembled `ReportsOverview` snapshots per `YearMonth` (including prior-month fields when fetched):

| Month | TTL | Rationale |
|-------|-----|-----------|
| Current | 15 min | Transactions and balances change often |
| Previous | 1 hour | May still receive back-dated edits |
| Older | 7 days | Historical months rarely change |

**Behaviour (stale-while-revalidate):**

1. Navigate to a month → if a **fresh** cache hit exists, show immediately (no network).
2. If **stale** → show cached data, refresh in the background (`isRefreshing`).
3. Pull-to-refresh → invalidate that month and force a network load.
4. Logout → `LocalDataCleaner` clears the cache (avoids cross-user leakage).

TTLs live in `ReportsCachePolicy`. Room persistence is a possible follow-up if offline report viewing is required.

## Navigation from reports

| Source | Destination |
|--------|-------------|
| Category row (id present) | `Screen.Transactions` with `categoryId` / `categoryName` / `year` / `month` |
| Account balance row (id present) | `Screen.AccountDetail` |
| Budget performance row (expense id present) | `Screen.Transactions` with `expenseId` / `expenseName` / `year` / `month` |

`TransactionsViewModel` applies SavedStateHandle filters (and initial month) the same way as the existing expense deep-link.

## Future enhancements (out of scope)

- Vico line/bar charts for net worth and category donut
- Year-to-date / quarter range selector
- Export (CSV/PDF)
- `pledger://reports` deep link
- Room-backed report cache
