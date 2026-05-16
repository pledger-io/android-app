# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the Pledger.io Android application. ADRs document significant architectural decisions, their context, and rationale.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [001](001-mvvm-clean-architecture.md) | MVVM with Clean Architecture | Accepted |
| [002](002-jetpack-compose-ui.md) | Jetpack Compose for UI | Accepted |
| [003](003-hilt-dependency-injection.md) | Hilt for Dependency Injection | Accepted |
| [004](004-retrofit-moshi-networking.md) | Retrofit + Moshi for Networking | Accepted |
| [005](005-room-offline-caching.md) | Room for Offline Caching | Accepted |
| [006](006-encrypted-shared-preferences.md) | EncryptedSharedPreferences for Auth | Accepted |
| [007](007-resource-sealed-class.md) | Resource Sealed Class for State | Accepted |
| [008](008-navigation-compose.md) | Jetpack Navigation Compose | Accepted |
| [009](009-dark-first-design.md) | Dark-First Design Language | Accepted |
| [010](010-workmanager-background-sync.md) | WorkManager for Background Sync | Accepted |
| [011](011-single-module-structure.md) | Single Module with Package Boundaries | Accepted |
| [012](012-google-fonts-provider.md) | Google Fonts Provider for Typography | Accepted |
| [013](013-ksp-over-kapt.md) | KSP over KAPT for Annotation Processing | Accepted |
| [014](014-coil-authenticated-images.md) | Coil for Authenticated Account Logos | Accepted |
| [015](015-stale-while-revalidate-cache.md) | Stale-While-Revalidate Cache for Reference Data | Accepted |

## ADR Format

Each ADR follows the format:
- **Status** — Proposed, Accepted, Deprecated, Superseded
- **Context** — What prompted the decision
- **Decision** — What was decided
- **Consequences** — Trade-offs and implications
