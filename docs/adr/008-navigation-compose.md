# ADR-008: Jetpack Navigation Compose

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app has 10+ screens with various navigation patterns: bottom tabs, detail push/pop, and onboarding flow with backstack clearing. We need a navigation solution that integrates with Compose and Hilt.

Options considered:
- **Jetpack Navigation Compose** — Official Google solution, type-safe routes, integrates with Hilt ViewModels
- **Voyager** — Compose-native, simpler API, but smaller community and less tooling
- **Decompose** — Multiplatform, lifecycle-aware, but adds complexity for an Android-only app
- **Manual navigation** — Full control but reimplements solved problems (backstack, deep links, state restoration)

## Decision

Use **Jetpack Navigation Compose** with:
- A sealed `Screen` class defining all routes as string constants
- `NavHost` + `composable` blocks in a single `NavGraph.kt`
- `navArgument` for typed path parameters (`accountId: Long`, etc.)
- `hiltViewModel()` for ViewModel injection scoped to each nav destination
- Bottom navigation using `NavigationBar` + `NavigationBarItem`

## Consequences

### Positive
- Official solution with long-term support and Android Studio tooling
- Automatic backstack management and state restoration
- `SavedStateHandle` in ViewModels provides type-safe access to nav arguments
- Deep link support is built-in (useful for future notification taps)
- `hiltViewModel()` scopes ViewModels to nav destinations automatically

### Negative
- String-based routes are error-prone (mitigated by `Screen` sealed class centralizing route definitions)
- Type-safe navigation (Navigation 2.8+) is still evolving
- Nested navigation graphs add complexity for bottom tab state preservation
- Animation customization requires explicit `AnimatedNavHost` configuration

### Navigation Patterns

| Pattern | Implementation |
|---------|---------------|
| Bottom tabs | `NavigationBar` with `popUpTo` + `saveState` + `restoreState` |
| Detail screen | `navController.navigate(Screen.Detail.createRoute(id))` |
| Onboarding → Main | `popUpTo(onboarding) { inclusive = true }` |
| Logout | `popUpTo(0) { inclusive = true }` clears entire stack |
