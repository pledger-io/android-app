# ADR-017: Deep Links and Reports Data

**Date:** 2026-05-16  
**Status:** Accepted

## Context

Tier 3 usability work required:

- Opening specific transactions, accounts, or budget months from outside the app (notifications, bookmarks, automation).
- Replacing the Reports tab placeholder with real summaries from the Pledger balance and transaction APIs.
- Global search across common entities.

Offline write queue, push notifications, and full charting libraries are deferred (larger scope).

## Decision

### Deep links (`pledger://`)

Custom URI scheme registered in `AndroidManifest.xml`:

| URI | Destination |
|-----|-------------|
| `pledger://transaction/{id}` | Transaction detail |
| `pledger://account/{id}` | Account detail |
| `pledger://budgets?year=Y&month=M` | Budgets tab for month |

`DeepLinkParser` maps URIs to a sealed `DeepLink` type. `MainActivity` forwards pending links to `PledgerRoot`, which navigates after login. Budget alert notifications use the budgets URI as their `PendingIntent` content intent (see [ADR-010](010-workmanager-background-sync.md)).

### Reports

`ReportRepository` loads:

- Income vs expense — transaction aggregation for the selected month
- Category / account balance — `POST /v2/api/balance/{partition}`
- Budget performance — existing `BudgetRepository`
- Net worth trend — `POST /v2/api/balance/by-date/balance`

`ReportsScreen` shows month navigation, pull-to-refresh, and progress-style summaries (no third-party chart SDK).

### Global search

`SearchScreen` queries transactions (description filter), owned accounts, counterparties, and categories. Entry point: Dashboard search icon.

### Guided / Power (Tier 3.6 partial)

Power mode expands transaction filters by default on first load (unless opened via expense deep link / navigation args).

## Consequences

- Custom scheme only; App Links (`https://`) can be added later with host verification.
- Report partitions depend on server support for `category`, `account`, and `balance` partition keys; errors surface in UI.
- Search loads owned accounts via refresh — acceptable for MVP, may be optimized with cache-only reads later.
