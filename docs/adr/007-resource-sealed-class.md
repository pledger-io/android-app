# ADR-007: Resource Sealed Class for State Representation

**Date:** 2026-05-13
**Status:** Accepted

## Context

Data operations can succeed, fail, or be in progress. The UI needs to differentiate between these states to show loading indicators, error messages, or data. We need a consistent pattern across all repositories and use cases.

Options considered:
- **Nullable returns + exceptions** — Unclear semantics; forces try-catch at every call site
- **Result<T>** (Kotlin stdlib) — Only Success/Failure, no Loading state
- **Custom sealed class** — Full control over states; can carry metadata
- **Arrow's Either** — Functional, powerful, but heavy dependency for simple use case

## Decision

Define a `Resource<T>` sealed class with three states:

```kotlin
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}
```

All repository methods return either `Flow<Resource<T>>` (for observable data) or `suspend fun: Resource<T>` (for one-shot operations).

## Consequences

### Positive
- Exhaustive `when` expressions force handling of all three states in the UI
- `Loading` state is a first-class concept, enabling shimmer placeholders
- `Error` carries a human-readable message and optional exception for logging
- Consistent pattern reduces cognitive load — every data flow follows the same shape
- `Resource.Loading` is a singleton (`data object`), avoiding unnecessary allocations

### Negative
- Every `when` block needs three branches, even when Loading is irrelevant
- No built-in support for partial data (e.g., cached data + loading from network simultaneously)
- Simple operations that can't fail still need to be wrapped in `Resource`

### Mitigations
- Repositories can emit `Success` with cached data while still fetching from network — the UI renders immediately
- ViewModels typically map `Resource` to a richer `UiState` that separates `isLoading` from `data`
