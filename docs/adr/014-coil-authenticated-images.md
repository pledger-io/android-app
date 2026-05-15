# ADR-014: Coil for Authenticated Account Logos

**Date:** 2026-05-15
**Status:** Accepted

## Context

Bank accounts in Pledger.io can have an `iconFileCode` pointing at a file stored on the server. Images are served by `GET /v2/api/files/{fileCode}` and require the same JWT authentication and base URL as other API calls.

Options considered:
- **Coil + shared OkHttpClient** — Reuse interceptors (auth, dynamic base URL); standard Compose `AsyncImage` support
- **Retrofit `ResponseBody` + manual decode** — More boilerplate, no disk/memory cache
- **Glide** — Mature but heavier; Coil is already idiomatic with Compose

## Decision

Use **Coil 2** (`coil-compose`) with an `ImageLoader` built from the app's singleton `OkHttpClient`.

Implementation:
- `AccountIconUrlProvider` builds `{baseUrl}/v2/api/files/{fileCode}` from `SessionManager`
- `NetworkModule` provides `ImageLoader`; `PledgerApp.onCreate` calls `Coil.setImageLoader()`
- `AccountIcon` composable wraps `SubcomposeAsyncImage` with a circular clip and fallback `AccountBalance` icon
- `iconFileCode` is stored on domain `Account`, mapped from `AccountDto`, persisted in Room (schema v3)
- Account detail and transaction detail screens show logos; transaction detail loads linked accounts by id to resolve icon codes

## Consequences

### Positive
- Auth and host rewriting work identically for JSON and images
- Coil handles caching, cancellation, and Compose lifecycle
- Fallback UI when code is missing or download fails

### Negative
- Full URLs tied to session base URL — changing server URL invalidates in-memory cache entries until reload
- Room migration required when adding `iconFileCode` column (destructive migration acceptable for current app stage)
- Entry-point accessor in `AccountIcon` for Hilt singleton outside ViewModel (alternative: pass URLs from ViewModel state)
