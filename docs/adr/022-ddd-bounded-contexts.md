# ADR-022: Bounded Contexts for Package Structure

**Date:** 2026-10-30
**Status:** Accepted

## Context

The codebase was organized by technical layer (`data/`, `domain/`, `ui/`, `util/`), so classes belonging to the same feature were scattered across the tree and every layer grew into a grab-bag of unrelated concepts. As the feature surface grew (accounts, budgets, reports, sync, settings, MFA), navigating and reasoning about ownership became harder, and accidental coupling between unrelated features became easy.

## Decision

Reorganize `com.pledgerio.app` into **bounded contexts**, one package per domain area. Each context owns its domain model, repository ports, use cases, infrastructure implementations, and UI:

```
com.pledgerio.app.<context>.{
    domain.model        // entities, value objects, domain rules — no Android deps
    domain.repository   // repository ports (interfaces)
    domain.usecase      // single-purpose use cases
    data                // repository impls, Room DAO/entity, DTOs, caches
    ui                  // screens, view models, context-specific components
    <context helpers>   // e.g. sync/work, accounts/AccountIconUrlProvider
}
```

### Context map

| Context | Owns |
|---|---|
| `identity` | Authentication, sessions, MFA, biometric lock, onboarding UI |
| `accounts` | `Account` model, account repository, balance enrichment, accounts UI |
| `transactions` | `Transaction`/splits/filters/templates, transaction repository, OCR/invoice scan, transaction UI |
| `budgets` | `Budget`, expense groups, budget use cases, threshold alerts, budgets UI |
| `reports` | Report models, `ReportsOverview`, `SpendingInsights`, dashboard/reports UI |
| `catalog` | `Category`, `Tag`, `Currency`, `Contract` + repositories and pickers UI |
| `sync` | `TransactionOutbox`, `SyncMetadata`, WorkManager sync machinery (`SyncWorker`, schedulers) |
| `settings` | `UserPreferences`, `ThemeMode`, `AppLocale`, `FinanceExperienceMode`, issue reporting, settings UI |
| `shared` | Shared kernel: `Resource`, `AppLog`/`LogSanitizer`, network monitor, extensions, Room database + migrations, `PledgerApiService`, cross-context DTOs, generic UI components |

The app shell stays at the root: `MainActivity`, `PledgerApp`, `ui/navigation`, `ui/theme`, and `ui/components` (design-system widgets used by every context). `di/` remains central for Hilt wiring, since modules span contexts by design.

### Dependency rules

- Contexts may depend on `shared`, never on each other's `data` layer.
- Cross-context reads go through the other context's `domain.repository` port or a `domain.usecase`.
- `domain` packages must stay free of Android/Room/Retrofit imports.
- DTOs shared by several contexts (`PagedDto`, `DateRangeDto`) live in `shared.data.dto`.

## Consequences

- Feature work is local: a change to budgets touches `budgets/` plus at most `shared/` or another context's port.
- New code must be placed in the owning context; do not recreate top-level `data/`/`domain/`/`util/` packages.
- The dependency rules above are conventions, not yet enforced by tooling. An ArchUnit-style check (or `api`/`implementation` module split) can enforce them later if the team wants a hard guarantee.
- This refines ADR-011 (single module with package boundaries): the module count is unchanged, but package boundaries are now drawn along domain lines instead of technical layers.
