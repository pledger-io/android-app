# ADR-005: Room for Offline Caching

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app connects to a self-hosted server that may be unavailable (home server down, user offline, poor connectivity). Users should still be able to view their most recent financial data. We need a local persistence layer for caching.

Options considered:
- **Room** — SQLite abstraction with compile-time query verification, Flow support, and coroutine integration
- **SQLDelight** — Kotlin multiplatform, SQL-first, but less Android ecosystem integration
- **Realm** — Object database, no SQL, but adds significant APK size and proprietary dependency
- **DataStore** — Key-value only, not suitable for relational data like transactions

## Decision

Use **Room** as the local database for offline caching of accounts, transactions, budgets, and categories.

Design choices:
- Entities are separate from domain models — they contain `toDomain()` / `fromDomain()` mapping functions
- `TypeConverters` handle `LocalDate` and `List<String>` serialization
- DAOs expose `Flow<List<Entity>>` for observable queries and `suspend` functions for writes
- `fallbackToDestructiveMigration()` for schema changes (cache is repopulated from server)
- Each entity includes a `lastSynced` timestamp for staleness detection

## Consequences

### Positive
- Compile-time SQL verification catches query errors at build time
- Flow-returning DAOs enable reactive UI updates when cached data changes
- Seamless coroutine support — no callback-based APIs
- Battle-tested on Android with extensive documentation
- Destructive migration is acceptable since Room is a cache, not the source of truth

### Negative
- Mapping between Entity ↔ Domain Model ↔ DTO adds boilerplate
- Destructive migration means schema changes lose cached data (acceptable for a cache layer)
- Room's auto-generated code increases build time slightly

### Caching Strategy
```
API Request → Success? → Store in Room → Emit to UI
                  ↓ No
           Room has data? → Emit cached data with staleness indicator
                  ↓ No
           Emit Error
```
