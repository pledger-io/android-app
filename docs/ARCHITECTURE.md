# Architecture Documentation

## Overview

Pledger.io Android is a native Android client for the self-hosted Pledger.io personal finance manager. It connects to a user-provided REST backend and provides a mobile-native experience for managing accounts, transactions, budgets, and financial reports.

## High-Level Architecture

The application follows **Clean Architecture** principles with three distinct layers, enforced through package boundaries:

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│  (Jetpack Compose screens + ViewModels)     │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│  (Use Cases + Models + Repository Interfaces)│
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  (Retrofit API + Room DB + Repo Impls)      │
└─────────────────────────────────────────────┘
```

### Dependency Rule

Dependencies point **inward only**: UI → Domain ← Data. The domain layer has no dependencies on Android framework, networking libraries, or database implementations. Repository interfaces are defined in `domain/` and implemented in `data/`.

## Layer Details

### UI Layer (`ui/`)

Each feature is organized into its own package containing:

- **Screen** — A `@Composable` function representing the full screen. Receives state from the ViewModel and dispatches user events back to it.
- **ViewModel** — An `@HiltViewModel` that holds a `StateFlow<UiState>` and exposes action functions. Collects data from use cases or repositories and maps it to the screen's UI state.
- **UiState** — A data class colocated with the ViewModel, representing the screen's complete state (loading, error, data).

```
ui/
├── theme/          # Material 3 theme definition
├── components/     # Shared composables (cards, loading, error, empty)
├── navigation/     # NavGraph and Screen route definitions
├── onboarding/     # Server setup + Login
├── dashboard/      # Financial overview
├── transactions/   # Transaction list + detail
├── accounts/       # Account list + detail
├── budgets/        # Budget management
├── reports/        # Charts and analytics
└── settings/       # App configuration
```

#### Navigation

Navigation uses Jetpack Navigation Compose with a sealed `Screen` class defining all routes. Arguments (like `accountId`, `transactionId`) are passed as path parameters and extracted via `SavedStateHandle` in ViewModels.

Bottom navigation covers five main tabs: Dashboard, Transactions, Budgets, Accounts, Reports. Detail screens and onboarding sit outside the bottom nav and show/hide the bar accordingly.

### Domain Layer (`domain/`)

The domain layer is a pure Kotlin module with no Android framework dependencies.

- **Models** — Domain entities (`Account`, `Transaction`, `Budget`, `Category`) with business logic (e.g., `Budget.percentUsed`, `Budget.remaining`).
- **Repository Interfaces** — Contracts for data access. Return `Flow<Resource<T>>` for observable data or `suspend fun` for one-shot operations.
- **Use Cases** — Single-responsibility classes that encapsulate business logic. Each use case has an `operator fun invoke()` convention, making call sites readable. Use cases validate inputs, combine repository calls, and transform data.

### Data Layer (`data/`)

- **Remote** (`remote/`) — Retrofit service interface (`PledgerApiService`) and Moshi DTOs. DTOs are annotated with `@JsonClass(generateAdapter = true)` for compile-time adapter generation.
- **Local** (`local/`) — Room database with entities, DAOs, and type converters. Entities contain `toDomain()` and `fromDomain()` mapping functions.
- **Repository Implementations** (`repository/`) — Coordinate between remote and local sources. General pattern:
  1. Emit `Resource.Loading`
  2. Attempt network fetch
  3. On success: cache to Room, emit `Resource.Success`
  4. On failure: fall back to Room cache if available, emit `Resource.Error`

## Dependency Injection

Hilt provides compile-time verified DI with three modules:

| Module | Scope | Provides |
|--------|-------|----------|
| `NetworkModule` | Singleton | Moshi, OkHttpClient, Retrofit, API service |
| `DatabaseModule` | Singleton | Room database and all DAOs |
| `RepositoryModule` | Singleton | Binds repository implementations to interfaces |

ViewModels are injected via `@HiltViewModel` and accessed in Compose through `hiltViewModel()`.

## Networking

### Request Pipeline

```
Composable → ViewModel → UseCase → Repository → Retrofit → OkHttp → Server
                                                              ↑
                                                    AuthInterceptor
                                                    (attaches JWT)
                                                    LoggingInterceptor
```

### Authentication Flow

1. User enters server URL → validated via `GET /api/info`
2. User submits credentials → `POST /api/authenticate` returns JWT
3. Token stored in `EncryptedSharedPreferences` via `SessionManager`
4. `AuthInterceptor` attaches `Authorization: Bearer <token>` to every request
5. On 401 response → session cleared → user redirected to login

### Error Handling

All repository methods return `Resource<T>` — a sealed class with three states:
- `Resource.Success(data)` — operation completed, data available
- `Resource.Error(message)` — operation failed, human-readable message
- `Resource.Loading` — operation in progress

## Offline Strategy

### Caching

Room serves as an offline cache. Every successful API response is persisted to Room before being emitted to the UI. When the network is unavailable, repositories fall back to cached data.

### Background Sync

`SyncWorker` (via WorkManager) runs every 12 hours to:
1. Refresh accounts and transactions
2. Check budget thresholds
3. Fire local notifications when any budget exceeds 80%

### Network Monitoring

`NetworkMonitor` exposes a `Flow<Boolean>` of connectivity state using `ConnectivityManager.NetworkCallback`. The UI can display offline banners reactively.

## Security

| Concern | Implementation |
|---------|---------------|
| Token storage | `EncryptedSharedPreferences` with AES-256-GCM |
| Transport | HTTPS enforced by server URL validation |
| Biometric | `BiometricPrompt` API (optional, toggle in settings) |
| Session expiry | 401 interceptor clears session automatically |
| ProGuard | Enabled in release builds, keeps DTOs and Room entities |

## State Management

Each screen follows a unidirectional data flow:

```
User Action → ViewModel function
                    ↓
            Update StateFlow<UiState>
                    ↓
            Compose recomposes with new state
```

UI state is a single data class per screen, making state easy to reason about, test, and preview. ViewModels never hold Android framework references — they use `SavedStateHandle` for navigation arguments.

## Theming

The app uses Material 3 with a custom color scheme:

- **Dark theme** (default): Deep navy background (`#0D1B2A`) with emerald green accent (`#00C896`)
- **Light theme**: Light gray background with the same emerald accent
- **Typography**: Sora (geometric, for headlines) + DM Sans (friendly, for body text), loaded via Google Fonts provider
- **Semantic colors**: Income green (`#4ADE80`), expense red (`#F87171`), warning amber (`#FBBF24`)

## Testing Strategy

| Layer | Tool | What's Tested |
|-------|------|---------------|
| Use Cases | JUnit + MockK + Coroutines Test | Input validation, data transformation, business rules |
| ViewModels | JUnit + MockK + Turbine | State transitions, error handling |
| Repositories | JUnit + MockK | Network/cache coordination |
| UI | Compose UI Test | Critical flows (login, add transaction) |

## Module Boundaries

While currently a single-module app, the package structure is designed for future modularization:

```
:app           → Android application module
:core:data     → Retrofit, Room, repository impls (future)
:core:domain   → Models, use cases, repo interfaces (future)
:core:ui       → Shared composables, theme (future)
:feature:*     → Individual feature modules (future)
```
