# Reports — overview and UX design

**Status:** Implemented (2026-05)  
**Related:** [ADR-017](adr/017-deep-links-and-reports.md), [USABILITY_IMPROVEMENT_PLAN.md](USABILITY_IMPROVEMENT_PLAN.md)

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

## Information architecture

```
Reports tab
├── Month navigator (all types)
├── Report type chips
│   ├── Overview (default)     ← NEW: parallel load, composite UI
│   ├── Income vs expenses
│   ├── Category breakdown
│   ├── Budget performance
│   ├── Net worth
│   └── Account balance
└── Pull-to-refresh / last updated
```

## Overview content (per selected month)

| Block | Data source | Purpose |
|-------|-------------|---------|
| Net cash flow hero | `getIncomeExpenseSummary` | Income − expenses; savings rate when income > 0 |
| Quick stats row | balances + budgets | Total assets; budgets on track vs over |
| Top categories | `getCategoryBreakdown` (top 5) | Where spending concentrated |
| Net worth mini chart | `getNetWorthTrend` filtered to month | Trend shape + latest value |
| Hint row | — | Points users to chips for full reports |

Detail report types reuse improved shared components (`IncomeExpenseCard`, `PartitionList`, `NetWorthSection`, `BudgetPerformanceList`).

## Data loading

- **Overview:** `ReportsViewModel` loads five repository calls in parallel (`async` + `awaitAll`). Partial success is allowed; error is shown only when every call fails.
- **Net worth:** Points filtered client-side to the selected `YearMonth` (API returns daily series from 1970; UI previously showed arbitrary first 15 rows).

## Future enhancements (out of scope)

- Vico line/bar charts for net worth and category donut
- Year-to-date / quarter range selector
- Export (CSV/PDF)
- Tap category row → transactions filtered by category
- Compare to previous month (% change)
