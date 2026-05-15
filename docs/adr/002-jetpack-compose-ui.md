# ADR-002: Jetpack Compose for UI

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app needs a modern, responsive UI with animations, transitions, and Material 3 theming. Two main options exist for Android UI:

- **XML Views** — Mature, extensive library support, but verbose and hard to compose dynamically
- **Jetpack Compose** — Declarative, Kotlin-native, first-class Material 3 support, growing ecosystem

## Decision

Use **Jetpack Compose** with **Material 3** as the sole UI framework. No XML layouts, no View-based components.

Specific choices:
- Material 3 components for all UI elements
- Compose BOM for version alignment across Compose libraries
- `LazyColumn` / `LazyRow` for all lists (replacing RecyclerView)
- Compose Navigation (not Fragment-based navigation)

## Consequences

### Positive
- Declarative UI is more concise and easier to reason about than imperative XML
- Tight Kotlin integration eliminates the XML ↔ Kotlin bridge overhead
- State-driven recomposition aligns naturally with MVVM's `StateFlow`
- Material 3 theming is first-class in Compose
- Preview support in Android Studio for rapid iteration

### Negative
- Some third-party libraries still only support Views (mitigated by `AndroidView` interop)
- Compose tooling (layout inspector, profiling) is less mature than View tooling
- Developers familiar only with XML Views face a learning curve
- Larger initial APK size due to Compose runtime

### Mitigations
- Using Compose BOM ensures consistent versions and avoids compatibility issues
- R8/ProGuard in release builds removes unused Compose code
