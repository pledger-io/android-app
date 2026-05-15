# ADR-011: Single Module with Package Boundaries

**Date:** 2026-05-13
**Status:** Accepted

## Context

Android projects can be structured as single-module or multi-module. Multi-module enforces compile-time layer boundaries and enables parallel builds, but adds Gradle configuration complexity. For a new project, we need to balance structure with velocity.

Options considered:
- **Single module** — Simple Gradle setup, fast initial development, enforce boundaries by convention
- **Multi-module by layer** (`:core:data`, `:core:domain`, `:core:ui`) — Compile-time boundary enforcement
- **Multi-module by feature** (`:feature:dashboard`, `:feature:transactions`) — Maximum isolation, parallel builds
- **Hybrid** (layer + feature modules) — Best of both but highest Gradle complexity

## Decision

Start with a **single module** (`app/`), enforcing layer boundaries through **package structure and naming conventions**.

```
com.pledgerio.app/
├── data/       # Data layer — MUST NOT be imported by domain/
├── domain/     # Domain layer — MUST NOT import data/ or ui/
├── ui/         # UI layer — imports domain/, never data/ directly
├── di/         # DI wiring — bridges all layers
└── util/       # Shared utilities
```

The package structure mirrors what a multi-module setup would look like, making future modularization straightforward.

## Consequences

### Positive
- Zero Gradle overhead — single `build.gradle.kts` file
- Faster build times for a project of this size (no inter-module resolution)
- Simpler dependency management (one dependency block)
- Low friction for new feature development — no module creation ceremony

### Negative
- Layer boundaries are conventions, not compile-time rules — accidental cross-layer imports are possible
- No parallel module compilation
- All code recompiles on any change (mitigated by incremental compilation)

### Modularization Path
The package structure is designed for extraction. When build times or team size justify it:
1. Extract `domain/` into `:core:domain` (pure Kotlin module, no Android)
2. Extract `data/` into `:core:data` (depends on `:core:domain`)
3. Extract shared UI into `:core:ui` (theme, components)
4. Extract features into `:feature:*` (depends on `:core:*`)
