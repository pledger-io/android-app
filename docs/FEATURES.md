# Feature overview

This describes the checked-in implementation. Server-backed features depend on the configured Pledger backend's API support. Implementation packages below are under `app/src/main/java/com/pledgerio/app`.

| Capability | Behavior and entry point | Implementation |
|---|---|---|
| Setup and login | Configure/validate server, password login with TOTP verification when required, refresh session, change server, log out | `ui/onboarding`, `AuthRepository`, `SessionManager` |
| Dashboard | Accounts, income/expense summary, recent transactions, spending insights, add actions | `ui/dashboard`, `GetDashboardDataUseCase` |
| Transactions | Month navigation, paging, filters, detail, create/edit/delete | `ui/transactions`, `TransactionRepository` |
| Transaction entry | Income/expense/transfer, category, expense group, contract, tags, templates, splits, classification suggestions | `TransactionFormViewModel`, `ui/transactions/form` |
| Invoice scan | Camera or image picker, local OCR, backend text interpretation, review/edit prefilled form before saving | `ui/transactions/scan`, `ProcessInvoiceScanUseCase` |
| Accounts | All/Owned/Parties filters, counterparty search, balances, history, add/edit/delete, authenticated logos | `ui/accounts`, `AccountRepository` |
| Budgets | Month selection, initial setup, expense-group spending, add/edit groups | `ui/budgets`, budget use cases |
| Reports | Monthly overview; income/expense, categories, budget performance, net worth, account balances; month-over-month comparisons, spending insights, refresh and linked category/account/budget drill-downs | `ui/reports`, `ReportRepository`, `ReportsOverviewStore` |
| Search | Dashboard entry to transaction, account, counterparty, and category search | `ui/search` |
| Catalogs | Category and tag management from Settings; contracts/currencies supply selectors | `ui/categories`, `ui/tags`, catalog repositories |
| Personalization | Theme, display currency, Guided/Power defaults, system/English/Dutch/German language | `ui/settings`, `UserPreferences` |
| Security and support | Optional biometric lock, MFA enroll/disable, durable API token list/create/revoke; sanitized problem report opens a GitHub form for user submission | `BiometricLockManager`, `IssueReportViewModel` |
| Cached reads and offline creates | Room fallback, pending transaction create/discard, refresh indicators, periodic catalog/account refresh and configurable budget alerts with month deep links | `CacheRefresher`, `SyncWorker` |
| Deep links | Transaction/account details and budget month via `pledger://`; navigation waits for login | `DeepLinkParser`, `MainActivity`, `NavGraph` |

## Transaction semantics

The backend uses `DEBIT` for income and `CREDIT` for expenses. Income flows from a debtor to an owned account, expenses from an owned account to a creditor, and transfers between owned accounts. Types come from the server catalog; see [Accounts](feature/accounts/accounts.md).

Guided and Power modes expose the same capabilities with different default expansion of optional fields, templates, and filters; see [experience modes](feature/experience-modes/experience-modes.md).

## Current limitations

- Room caches reads and persists a transaction-create outbox for offline/I/O failures. Offline edit/delete/split PATCH and offline creation of missing accounts are unsupported. Client reconciliation mitigates unknown POST outcomes; there is no server exactly-once guarantee or multi-device conflict protocol.
- Cached transaction fallback supports date/type/text filters. Category, expense, and contract ID filters return an explicit error offline.
- Scanning imports `image/*` or captures an image. PDF import and additional confidence/review UI in the scan plan remain proposals.
- Reports have API-backed summaries and lightweight visuals. The Vico dependency does not mean all proposed charts are implemented.
- WorkManager schedules refresh every 12 hours subject to connectivity and Android scheduling. Budget alerts support enablement/threshold settings, deduplication, notification permission handling, and a link to the alerted month.
- Supported links are `pledger://transaction/{id}`, `pledger://account/{id}`, and `pledger://budgets?year=Y&month=M`. These are custom scheme links, not verified HTTPS App Links. Push notifications are deferred.
- Cached data can be stale/incomplete. Unsupported backend report partitions surface errors.

See [C4 architecture](ARCHITECTURE.md), [constraints](CONSTRAINTS.md), and the [documentation index](README.md) for details and historical plans.
