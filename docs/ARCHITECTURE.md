# Architecture Documentation

## Overview

Pledger.io Android is a native client for the self-hosted Pledger.io personal finance manager. It connects to a user-configured REST backend and provides a mobile experience for accounts, transactions, budgets, and reports.

All API paths use the **v2** prefix (e.g. `/v2/api/accounts`, `/v2/api/security/authenticate`). The base URL is stored per installation and applied at request time.

## High-Level Architecture

The application follows **Clean Architecture** with three layers:

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│  (Compose screens + ViewModels + UiState)   │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│  (Models + Repository interfaces + UseCases) │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  (Retrofit + Room + Repository implementations)│
└─────────────────────────────────────────────┘
```

### Dependency Rule

Dependencies point **inward only**: UI → Domain ← Data. The domain layer has no Android, Retrofit, or Room types. Repository interfaces live in `domain/`; implementations in `data/`.

## Layer Details

### UI Layer (`ui/`)

Each feature package typically contains:

| Artifact | Role |
|----------|------|
| `*Screen.kt` | `@Composable` UI; reads `StateFlow<UiState>`; forwards events to ViewModel |
| `*ViewModel.kt` | `@HiltViewModel`; coroutines; maps `Resource` / repository data to `UiState` |
| `*UiState` | Data class colocated with ViewModel |

Shared UI lives in `ui/components/` (`PledgerCard`, `LoadingScreen`, `ErrorScreen`, `EmptyScreen`, `AccountIcon`, …).

#### Feature packages

```
ui/
├── theme/              # Material 3 colors, typography (Sora + DM Sans)
├── components/         # Reusable composables
├── navigation/         # NavGraph, Screen routes
├── onboarding/         # Server setup, login
├── dashboard/          # Overview, recent transactions
├── transactions/       # List, detail, form, filters, autocomplete
├── accounts/           # List, detail, form
├── budgets/
├── reports/
├── settings/
└── PledgerApp.kt       # Scaffold + bottom navigation
```

#### Navigation

- Routes are defined in `Screen` (sealed class).
- `NavGraph` wires composable destinations; path args (`accountId`, `transactionId`) are read in ViewModels via `SavedStateHandle`.
- **Bottom tabs:** Dashboard, Transactions, Budgets, Accounts, Reports.
- **Stack screens:** Detail views, add/edit forms, onboarding, settings (bottom bar hidden when not on a main tab).
- Outer scaffold padding is passed into `NavGraph` so FABs clear the bottom navigation bar.

#### Transaction list UX

- **Paging:** `TransactionRepository.getTransactionsPage()` returns `PagedResult`; ViewModels merge pages and deduplicate by `id`.
- **Month navigator:** `startDate` / `endDate` sent to API per visible month; can load older months when scrolling.
- **Filters:** Optional `category`, `expense`, `contract` query params; UI uses debounced autocomplete against category/budget/contract APIs.
- **Type display:** `DEBIT` = income (green), `CREDIT` = expense (red) in lists.

#### Transaction create form

Account fields depend on transaction type:

| Type | From account | To account |
|------|--------------|------------|
| Income (`DEBIT`) | Debtor autocomplete (`type=debtor`) | Owned accounts dropdown |
| Expense (`CREDIT`) | Owned accounts dropdown | Creditor autocomplete (`type=creditor`) |
| Transfer | Owned accounts dropdown | Owned accounts dropdown |

Owned account types come from `GET /v2/api/account-types` (excluding counterparty types); lists are loaded with `GET /v2/api/accounts?type=…`.

**Redesign (planned):** See [Transaction form redesign](TRANSACTION_FORM_REDESIGN.md) for UX goals (type-first layout, money-flow card, date picker, sticky submit) and phased implementation.

#### Account logos

- Accounts expose `iconFileCode` from the API.
- `AccountIconUrlProvider` builds `{baseUrl}/v2/api/files/{fileCode}`.
- Coil loads images with the same `OkHttpClient` as Retrofit (JWT + dynamic host).
- Shown on **account detail** (balance card) and **transaction detail** (from/to rows; accounts fetched by id for icon codes).

### Domain Layer (`domain/`)

- **Models** — `Account`, `Transaction`, `Budget`, `Category`, `Currency`, `TransactionFilters`, `FilterOption`, `AccountTypeOption`, etc.
- **Repositories** — `AuthRepository`, `AccountRepository`, `TransactionRepository`, `BudgetRepository`, `CategoryRepository`, `ContractRepository`, `CurrencyRepository`.
- **Use cases** — e.g. `GetDashboardDataUseCase` aggregates dashboard data.

`AccountRepository` additionally supports `searchAccounts(typeCode, nameQuery)` and `getAccountsByTypes(typeCodes)` for the transaction form.

### Data Layer (`data/`)

- **Remote** — `PledgerApiService`, Moshi DTOs (`@JsonClass(generateAdapter = true)`).
- **Local** — Room (`PledgerDatabase` v3): accounts, transactions, budgets, categories, currencies.
- **Repositories** — Network-first with Room fallback pattern:

  1. Emit `Resource.Loading` (where applicable)
  2. Call API
  3. On success: persist to Room, emit `Resource.Success`
  4. On failure: emit cached data if present, else `Resource.Error`

Account balances are enriched via `POST /v2/api/balance/account` (partitioned by account name) after list/detail fetches.

## Dependency Injection

| Module | Provides |
|--------|----------|
| `NetworkModule` | Moshi, `OkHttpClient`, `Retrofit`, `PledgerApiService`, Coil `ImageLoader` |
| `DatabaseModule` | `PledgerDatabase`, DAOs |
| `RepositoryModule` | Repository bindings |

ViewModels: `@HiltViewModel` + `hiltViewModel()` in Compose.

`PledgerApp` sets global `CurrencyProvider` and `Coil.setImageLoader()` on startup.

## Networking

### Request pipeline

```
Screen → ViewModel → Repository → Retrofit
                                    ↓
                              OkHttpClient
                    ┌───────────────┼───────────────┐
         DynamicBaseUrlInterceptor   AuthInterceptor   LoggingInterceptor
                    (user base URL)   (Bearer JWT)
```

- **Placeholder Retrofit base URL** — Real host comes from `DynamicBaseUrlInterceptor` using `SessionManager.getBaseUrl()`.
- **Auth** — `AuthInterceptor` adds `Authorization: Bearer …`; 401 on authenticated calls clears session.

### Authentication flow

1. User enters server URL → validated with `GET {baseUrl}/health` (OkHttp, not Retrofit).
2. URL saved → `POST /v2/api/security/authenticate` → JWT + refresh token in `SessionManager`.
3. Subsequent API and image requests use the stored base URL and token.

### Key API surface (non-exhaustive)

| Area | Endpoints |
|------|-----------|
| Auth | `/v2/api/security/authenticate`, `/v2/api/security/oauth`, `/v2/api/security/logout` |
| Accounts | `/v2/api/accounts`, `/v2/api/accounts/{id}`, `/v2/api/account-types` |
| Transactions | `/v2/api/transactions`, `/v2/api/transactions/{id}` |
| Categories | `/v2/api/categories` |
| Budgets | `/v2/api/budgets`, `/v2/api/budgets/expenses` |
| Contracts | `/v2/api/contracts` |
| Balance | `/v2/api/balance`, `/v2/api/balance/{partition}` |
| Currencies | `/v2/api/currencies` |
| Files | `/v2/api/files/{fileCode}` |
| Health | `/health` |

### Error handling

Repositories return `Resource<T>`:

- `Resource.Success(data)`
- `Resource.Error(message)`
- `Resource.Loading`

## Offline Strategy

### Room cache

Entities: accounts (including `iconFileCode`), transactions, budgets, categories, currencies. Successful writes refresh the cache; reads fall back when offline.

### Background sync (`SyncWorker`)

Periodic work (12 h) via WorkManager:

1. Sync currencies
2. Refresh accounts (`getAccounts().first()`)
3. Load current-month budgets and notify if any group exceeds 80% spend

Transactions are refreshed on screen load rather than in the worker.

### Network monitoring

`NetworkMonitor` exposes connectivity as `Flow<Boolean>` for optional offline UI.

## Security

| Concern | Implementation |
|---------|----------------|
| Token storage | `EncryptedSharedPreferences` (AES-256-GCM) |
| Transport | User-supplied URL; cleartext permitted in debug config for dev servers |
| Biometric | Optional via settings (`BiometricPrompt`) |
| Session expiry | 401 handling in `AuthInterceptor` |
| Release | ProGuard / R8 enabled |

## State Management

Unidirectional flow per screen:

```
User action → ViewModel → StateFlow<UiState> → Compose recomposition
```

Navigation arguments via `SavedStateHandle`. No Android framework types inside ViewModels beyond `SavedStateHandle`.

## Theming

- **Dark-first** navy background with **emerald** primary (`#00C896`)
- **Semantic:** income green, expense red, warning amber
- **Fonts:** Sora (headlines), DM Sans (body) via Google Fonts provider

## Testing Strategy (target)

| Layer | Tools |
|-------|--------|
| Use cases | JUnit, MockK, coroutines-test |
| ViewModels | JUnit, MockK, Turbine |
| Repositories | JUnit, MockK |
| UI | Compose UI tests for critical flows |

## Module Boundaries

Single `:app` module today; package layout supports future `:core:*` and `:feature:*` modules (see ADR-011).
