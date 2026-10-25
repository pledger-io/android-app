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
| [016](016-usability-improvement-program.md) | Usability Improvement Program | Accepted |
| [017](017-deep-links-and-reports.md) | Deep Links and Reports Data | Accepted |
| [018](018-app-localization.md) | App Localization (en / nl / de) | Accepted |
| [019](019-security-and-quality-hardening.md) | Security and Quality Hardening Baseline | Accepted |
| [020](020-api-token-sessions.md) | Durable API Token Sessions | Accepted |
| [021](021-mfa-enroll-verify.md) | MFA Enroll and Login Verification | Accepted |

## ADR Format

Each ADR follows the format:
- **Status** — Proposed, Accepted, Deprecated, Superseded
- **Context** — What prompted the decision
- **Decision** — What was decided
- **Consequences** — Trade-offs and implications

## Relationship to the C4 views

- Context and containers: ADR-004, 006, 010, 014, 020, 021 explain remote access, credentials, workers, images, and authentication.
- Android components: ADR-001–003, 007, 008, 011, 013 explain layering, UI, dependency injection, state, navigation, module boundaries, and code generation.
- Cross-cutting constraints: ADR-005, 009, 012, 015–019 explain storage/cache evolution, appearance, typography, usability, localization, and hardening.

ADRs record decision history, not a feature checklist. Later decisions can refine earlier ones: ADR-015 extends ADR-005 caching; ADR-019 replaces its destructive-migration policy; ADR-021 extends the MFA scope deferred in ADR-020. Current implementation boundaries are in [Constraints](../CONSTRAINTS.md) and [C4 architecture](../ARCHITECTURE.md).

For a new decision, use the next available number, describe alternatives and consequences, and link any superseded decision. Mark only the affected policy superseded when the rest of an ADR still applies. Do not infer a new accepted decision solely from an unimplemented design plan.

Return to [documentation home](../README.md).
