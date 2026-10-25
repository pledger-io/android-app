# Android documentation

Start with the [feature overview](FEATURES.md), then read the architecture from system context to implementation.

| Document | Purpose |
|---|---|
| [Features](FEATURES.md) | Current capabilities and limitations |
| [Architecture](ARCHITECTURE.md) | C4 system context, containers, and components |
| [Design constraints](CONSTRAINTS.md) | Platform, API, persistence, security, and UX boundaries |
| [ADRs](adr/README.md) | Architectural decisions and consequences |
| [Development](DEVELOPMENT.md) | Setup, builds, CI, and releases |

## Feature references

- [Accounts](feature/accounts/accounts.md): account types, ownership, and transaction mapping.
- [Budgets](feature/budgets/budgets.md): initial setup and expense groups.
- [Categories](feature/categories/categories.md): catalog management.
- [Reports](feature/reports/reports.md): overview and drill-down design.
- [Experience modes](feature/experience-modes/experience-modes.md): Guided/Power defaults.
- [Localization](feature/localization/localization.md): supported languages and translation workflow.

## Plans and audit history

These preserve original proposals and progress checklists. They may describe both completed and unfinished work; use Features and Architecture for current behavior.

- [Feature documentation and design history](feature/README.md): token sessions, MFA, offline outbox, alerts, search, and report comparisons.
- [Audit archive](audit/README.md): senior audit and QA reviews.
- [Transaction form redesign](feature/transactions/transaction-form-redesign.md)
- [Invoice scan plan](feature/invoice-scan/invoice-scan.md)
- [Usability improvement plan](feature/usability/usability-improvement-plan.md)
- [Codebase improvement audit](audit/CODEBASE_IMPROVEMENT_AUDIT.md)

## Maintenance

Update Features when behavior changes, C4 views when responsibilities or integrations change, and Constraints when supported boundaries change. Record significant new decisions in an ADR with context, status, decision, and consequences. Retain previous ADRs and link superseding decisions. Keep implementation links with diagrams; C4 level 4 is represented by source rather than a duplicate class inventory.
