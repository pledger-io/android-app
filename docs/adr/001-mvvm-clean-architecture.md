# ADR-001: MVVM with Clean Architecture

**Date:** 2026-05-13
**Status:** Accepted

## Context

We need an architecture that separates concerns clearly, supports testability, and scales as the feature set grows. The app has significant complexity: multiple data sources (REST API + local cache), several feature screens, and offline requirements.

Common Android architecture options:
- **MVC** — Tends to produce large Activities/Fragments with tangled UI and business logic
- **MVP** — Better separation but requires manual lifecycle management and boilerplate interfaces
- **MVVM** — Natural fit with Compose's reactive model; ViewModels survive configuration changes
- **MVI** — Strong unidirectional flow but adds complexity for screens with simple interactions

## Decision

Adopt **MVVM (Model-View-ViewModel)** combined with **Clean Architecture** (UI → Domain → Data layers) and the **Repository pattern**.

- **ViewModels** expose `StateFlow<UiState>` to Compose screens
- **Use Cases** encapsulate business logic and are the ViewModel's entry point to the domain
- **Repositories** abstract data sources behind interfaces defined in the domain layer
- **Domain models** are independent of framework concerns (no Room annotations, no Moshi annotations)

## Consequences

### Positive
- ViewModels are testable without Android framework — they only depend on use cases
- Use cases can be reused across ViewModels (e.g., `GetTransactionsUseCase` in both Transactions and Account Detail screens)
- Swapping data sources (e.g., replacing Retrofit with Ktor) requires changes only in the data layer
- Compose's reactive model pairs naturally with `StateFlow`

### Negative
- Additional boilerplate: domain models require mapping to/from DTOs and entities
- Simple CRUD screens still require a UseCase class, even if it's a pass-through
- New developers need to understand the layer boundaries and dependency rule

### Mitigations
- Entity/DTO classes include `toDomain()` / `fromDomain()` companion functions to keep mapping colocated
- Pass-through use cases are kept minimal and serve as extension points for future validation
