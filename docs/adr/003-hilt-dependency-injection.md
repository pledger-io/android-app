# ADR-003: Hilt for Dependency Injection

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app has significant dependency graphs: ViewModels depend on use cases, use cases on repositories, repositories on API services and DAOs, and API services on OkHttp clients. Manual construction is error-prone and creates tight coupling.

DI framework options for Android:
- **Manual DI** — No framework overhead but doesn't scale; hard to manage scopes
- **Dagger 2** — Compile-time, performant, but high boilerplate with component/subcomponent definitions
- **Hilt** — Opinionated Dagger wrapper, standard Android scopes, less boilerplate
- **Koin** — Runtime resolution, simple setup, but no compile-time verification

## Decision

Use **Hilt** for dependency injection.

Three modules partition the dependency graph:
- `NetworkModule` — Provides Moshi, OkHttp, Retrofit, API service (Singleton scope)
- `DatabaseModule` — Provides Room database and DAOs (Singleton scope)
- `RepositoryModule` — Binds repository implementations to interfaces (Singleton scope)

ViewModels use `@HiltViewModel` and are injected into Compose via `hiltViewModel()`.

## Consequences

### Positive
- Compile-time dependency graph verification catches wiring errors at build time, not runtime
- Standard Android scopes (`SingletonComponent`, `ViewModelComponent`) reduce boilerplate vs raw Dagger
- `@HiltViewModel` + `hiltViewModel()` provides seamless Compose integration
- `@HiltWorker` enables DI in WorkManager workers without manual factory wiring

### Negative
- Build time impact from annotation processing (mitigated by KSP — see ADR-013)
- Hilt's opinionated scoping doesn't cover every possible scope (rarely an issue for this app's needs)
- Debugging generated Dagger code requires understanding of Dagger internals

### Mitigations
- Using KSP instead of KAPT significantly reduces annotation processing time
- Keeping module count low (3 modules) limits graph complexity
