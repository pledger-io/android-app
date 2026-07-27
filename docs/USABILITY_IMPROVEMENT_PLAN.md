# Usability Improvement Plan

**Status:** Active (Tier 3 partial — T3.3 create-outbox MVP in progress; T3.4 deferred)  
**Related:** [ADR-016](adr/016-usability-improvement-program.md), [USABILITY_MODES.md](USABILITY_MODES.md), [TRANSACTION_FORM_REDESIGN.md](TRANSACTION_FORM_REDESIGN.md)

## Purpose

This document captures a prioritized roadmap to improve end-user usability of the Pledger.io Android app. It complements existing architecture docs and ADRs; implementation is tracked by tier and checkbox below.

## Current baseline

The app already provides:

- Five-tab navigation (Dashboard, Transactions, Budgets, Accounts, Reports)
- Shared `LoadingScreen`, `ErrorScreen`, `EmptyScreen`
- Pull-to-refresh on main data screens
- Stale-while-revalidate caching ([ADR-015](adr/015-stale-while-revalidate-cache.md))
- Offline banner on main tabs when disconnected
- Guided / Power finance experience modes ([USABILITY_MODES.md](USABILITY_MODES.md))
- Redesigned transaction form (type selector, amount card, flow card, date picker, templates)

## User goals

| Goal | Primary surfaces |
|------|------------------|
| Financial overview | Dashboard, Reports |
| Fast transaction entry | Transaction form, FABs |
| Find and fix transactions | Transactions list, detail |
| Manage accounts and parties | Accounts list, detail, forms |
| Stay on budget | Budgets overview, detail |
| Reliable offline / flaky network | All authenticated screens |
| Configure and trust the app | Settings |

## Gap analysis (summary)

| Area | Severity | Issue |
|------|----------|--------|
| Reports | Medium | Overview hub added; Vico charts still optional follow-up |
| Settings access | Medium | Only reachable from Dashboard |
| Offline UX | Medium | Banner hidden on forms and detail screens |
| Error recovery | Medium | Detail screens lack Retry |
| List refresh | Low–Med | Transaction refresh can clear list (flicker) |
| Theme | Medium | `PledgerTopBar` uses system dark, not app `ThemeMode` |
| Settings completeness | Medium | Server URL edit, notifications, biometric gate stubbed |
| Deep links | High (future) | Not implemented |
| Accessibility / i18n | Medium | Sparse `contentDescription`; mostly hardcoded English |

## Roadmap

### Tier 1 — Quick wins (target: first release pass)

| ID | Task | Files / notes |
|----|------|----------------|
| T1.1 | Settings icon on all main tab top bars | `TransactionsScreen`, `BudgetsScreen`, `AccountsScreen`, `ReportsScreen`, `NavGraph` |
| T1.2 | Retry on `ErrorScreen` for account and transaction detail | `AccountDetailScreen`, `TransactionDetailScreen` |
| T1.3 | Offline banner on stack screens (forms, detail, settings) | `PledgerApp.kt` — show banner for all authenticated routes |
| T1.4 | Transaction list refresh keeps cached rows while loading | `TransactionsViewModel` |
| T1.5 | `PledgerTopBar` respects app `ThemeMode` | `PledgerTopBar.kt`, pass `darkTheme` from `Local` or parameter |
| T1.6 | Settings version from `BuildConfig.VERSION_NAME` | `SettingsScreen.kt` |
| T1.7 | Reports honest empty / coming-soon state | `ReportsScreen.kt`, `strings.xml` |

### Tier 2 — Core UX

| ID | Task |
|----|------|
| T2.1 | Global sync / “last updated” indicator on list screens |
| T2.2 | Mode-aware empty-state CTAs (Dashboard, Transactions, Accounts) |
| T2.3 | Edit server URL flow with re-auth and `LocalDataCleaner` |
| T2.4 | Biometric unlock on resume OR hide toggle until implemented |
| T2.5 | Localize shared components (`ErrorScreen`, onboarding, empty states) |
| T2.6 | Accessibility pass on shared components and Settings rows |
| T2.7 | Budget month navigation; overspend → filtered transactions |

### Tier 3 — Differentiators

| ID | Task |
|----|------|
| T3.1 | Deep links (`transaction/{id}`, `account/{id}`, budget month) |
| T3.2 | Reports wired to API (charts) |
| T3.3 | Offline write queue / draft transactions |
| T3.4 | Push notifications for budget alerts |
| T3.5 | Global search (transactions, accounts, categories) |
| T3.6 | Extend Guided/Power defaults per [USABILITY_MODES.md](USABILITY_MODES.md) |

## Implementation tracking

### Tier 1

- [x] T1.1 Settings on all main tabs
- [x] T1.2 Detail screen retry
- [x] T1.3 Offline banner on stack screens
- [x] T1.4 Transaction refresh without list clear
- [x] T1.5 Top bar theme alignment
- [x] T1.6 BuildConfig version in Settings
- [x] T1.7 Reports coming-soon state

### Tier 2

- [x] T2.1 Sync / last-updated indicator
- [x] T2.2 Empty-state CTAs
- [x] T2.3 Edit server URL
- [x] T2.4 Biometric gate (disabled until implemented)
- [x] T2.5 Localization pass (core empty/error/server strings)
- [x] T2.6 Accessibility pass (settings rows, nav back)
- [x] T2.7 Budget navigation links

### Tier 3

- [x] T3.1 Deep links
- [x] T3.2 Reports data
- [~] T3.3 Offline writes — create-only outbox MVP (queue + flush + pending list); edit/delete drafts still deferred
- [ ] T3.4 Budget notifications (deferred)
- [x] T3.5 Global search
- [x] T3.6 Guided/Power extensions (transaction filters default)

## Success criteria (manual)

- Settings reachable in one tap from any main tab
- User can retry after error on account/transaction detail without leaving screen
- Offline user sees banner before attempting save on forms
- Pull-to-refresh on transactions does not flash empty list
- Top bar gradient matches selected light/dark theme in Settings
- Reports tab sets clear expectation when charts are unavailable

## References

- Architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
- Decision record: [ADR-016](adr/016-usability-improvement-program.md)
- Transaction form: [TRANSACTION_FORM_REDESIGN.md](TRANSACTION_FORM_REDESIGN.md)
- Experience modes: [USABILITY_MODES.md](USABILITY_MODES.md)
