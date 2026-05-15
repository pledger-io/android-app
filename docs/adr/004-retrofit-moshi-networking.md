# ADR-004: Retrofit + Moshi for Networking

**Date:** 2026-05-13
**Status:** Accepted

## Context

The app communicates with a self-hosted Pledger.io REST API using JSON. We need an HTTP client that supports coroutines, interceptors (for auth), and type-safe API definitions.

Options considered:
- **Retrofit + Moshi** — Declarative API interfaces, compile-time JSON adapters, OkHttp interceptors
- **Retrofit + Gson** — Similar to Moshi but Gson uses reflection, is slower, and has weaker Kotlin support
- **Ktor Client** — Kotlin-native, multiplatform-ready, but less ecosystem support on Android

## Decision

Use **Retrofit** with **OkHttp** for HTTP and **Moshi** with KSP codegen for JSON serialization.

Key implementation details:
- `PledgerApiService` is a Retrofit interface with suspend functions for coroutine support
- All business endpoints use the **`/v2/api/`** prefix (see backend OpenAPI contract)
- `DynamicBaseUrlInterceptor` rewrites request host/port/scheme from `SessionManager.getBaseUrl()` so users can point at any self-hosted instance
- `AuthInterceptor` attaches JWT bearer tokens and handles 401 responses on authenticated calls
- Server validation during onboarding uses a direct OkHttp `GET {baseUrl}/health` call before login
- `HttpLoggingInterceptor` provides debug-level request/response logging
- DTOs use `@JsonClass(generateAdapter = true)` for compile-time Moshi adapter generation
- 30-second timeouts for connect, read, and write
- Account search supports `type` (repeatable) and `accountName` query params for autocomplete in the transaction form

## Consequences

### Positive
- Retrofit's interface-based API definitions are concise and type-safe
- Moshi's KSP codegen avoids reflection at runtime — faster, smaller, and ProGuard-friendly
- OkHttp interceptors provide a clean extension point for auth, logging, and caching
- Suspend function support makes coroutine integration seamless
- Mature ecosystem with extensive documentation and community support

### Negative
- Retrofit doesn't support WebSockets (not needed for Pledger.io's REST API)
- Two serialization layers: Moshi for network DTOs, Room for local entities — no shared format
- OkHttp adds ~1MB to APK size

### Alternatives Rejected
- **Gson**: Relies on reflection, poor Kotlin null-safety support, no codegen option
- **Ktor**: Would require rewriting interceptor patterns; less mature on Android
- **kotlinx.serialization**: Good Kotlin support but Retrofit converter is less mature
