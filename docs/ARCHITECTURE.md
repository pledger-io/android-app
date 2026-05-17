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
├── budgets/            # Overview, initial setup, expense groups, detail
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
- **Usability roadmap:** Tiered improvements (settings discoverability, offline banner scope, error retry, theme alignment) are documented in [USABILITY_IMPROVEMENT_PLAN.md](USABILITY_IMPROVEMENT_PLAN.md) and [ADR-016](adr/016-usability-improvement-program.md).

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

The form adapts using a persisted **finance experience mode**:

- **Guided** (default): cleaner first-pass form with optional sections collapsed.
- **Power**: optional sections (and templates on new transactions) are visible by default for faster repetitive entry.

**Redesign (planned):** See [Transaction form redesign](TRANSACTION_FORM_REDESIGN.md) for UX goals (type-first layout, money-flow card, date picker, sticky submit) and phased implementation.

#### Account logos

- Accounts expose `iconFileCode` from the API.
- `AccountIconUrlProvider` builds `{baseUrl}/v2/api/files/{fileCode}`.
- Coil loads images with the same `OkHttpClient` as Retrofit (JWT + dynamic host).
- Shown on **account detail** (balance card) and **transaction detail** (from/to rows; accounts fetched by id for icon codes).

#### Accounts list UX

- **Filter chips** (All, Owned, Parties) stay visible above the list, including empty and error states, so users can always switch views (e.g. back from an empty Parties tab).
- **Owned** accounts load in full; **Parties** use paginated search (`type=creditor,debtor,debit`, 50 per page). See [Account types](ACCOUNTS.md).

#### Budgets UX

- **Initial setup:** `GET /v2/api/budgets` for the current month returns **404** when no budget exists → form → `POST /v2/api/budgets`.
- **Overview:** Cards per expense group with spent/budgeted from `GET …/expenses/balance`.
- **Add / edit groups:** `BudgetsScreen` (FAB) and `BudgetDetailScreen` (edit) — `PATCH /v2/api/budgets/expenses` to create (no `id`) or update amount (`id` set).
- **DTO note:** `DateRangeDto.endDate` is nullable for the active month.

See [Budgets](BUDGETS.md) for API and screen details.

### Domain Layer (`domain/`)

- **Models** — `Account`, `Transaction`, `Budget`, `Category`, `Currency`, `TransactionFilters`, `FilterOption`, `AccountTypeOption`, etc.
- **Repositories** — `AuthRepository`, `AccountRepository`, `TransactionRepository`, `BudgetRepository`, `CategoryRepository`, `ContractRepository`, `CurrencyRepository`.
- **Use cases** — e.g. `GetDashboardDataUseCase`, `CreateInitialBudgetUseCase`, `SaveBudgetExpenseUseCase`.

`AccountRepository` additionally supports `searchAccounts(typeCode, nameQuery)` and `getAccountsByTypes(typeCodes)` for the transaction form.

### Data Layer (`data/`)

- **Remote** — `PledgerApiService`, Moshi DTOs (`@JsonClass(generateAdapter = true)`).
- **Local** — Room (`PledgerDatabase` v5): accounts, transactions, budgets, categories, currencies, contracts, expense groups, account types, sync metadata.
- **Repositories** — Stale-while-revalidate cache for reference data, network-first for paged data:

  1. Read returns cached values from Room immediately (via Flow or one-shot query).
  2. `CacheRefresher` checks the `sync_metadata` TTL for that key.
  3. If stale, a background refresh is launched on the `@ApplicationScope` coroutine scope.
  4. The network call writes through to Room and marks the key fresh; Room Flow emits the new data.
  5. Mutations write through to Room and invoke `CacheRefresher.refreshInBackground` so other observers re-emit.
  6. If the cache is empty and the network fails the repository returns `Resource.Error`.

  See [ADR-015](adr/015-stale-while-revalidate-cache.md) for the full design.

Account balances are enriched via `POST /v2/api/balance/account` (partitioned by account name) after list/detail fetches.

## Dependency Injection

| Module | Provides |
|--------|----------|
| `NetworkModule` | Moshi, `OkHttpClient`, `TokenRefresher`, `AuthInterceptor`, `Retrofit`, `PledgerApiService`, Coil `ImageLoader` |
| `DatabaseModule` | `PledgerDatabase`, DAOs (including `SyncMetadataDao`, `ContractDao`, `ExpenseGroupDao`) |
| `DispatcherModule` | `@IoDispatcher`, `@DefaultDispatcher`, `@ApplicationScope` (for background cache refresh) |
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
                    (user base URL)   (Bearer JWT + refresh)
```

- **Placeholder Retrofit base URL** — Real host comes from `DynamicBaseUrlInterceptor` using `SessionManager.getBaseUrl()`.
- **Auth** — `AuthInterceptor` adds `Authorization: Bearer …`, refreshes the token before expiry via `POST /v2/api/security/oauth`, retries once on **401**, then clears **auth tokens only** (`clearAuthTokens`) so the **server URL is preserved** (user returns to login, not server setup).

### Authentication flow

1. User enters server URL → validated with `GET {baseUrl}/health` (OkHttp, not Retrofit).
2. URL saved → `POST /v2/api/security/authenticate` → access token, refresh token, and `expires_in` in `SessionManager`.
3. Subsequent API and image requests use the stored base URL and token; `TokenRefresher` calls the oauth endpoint when the access token is near expiry.
4. Logout calls `POST /v2/api/security/logout` when possible, then `clearAuthTokens()` (base URL and biometric preference remain).

### Key API surface (non-exhaustive)

| Area | Endpoints |
|------|-----------|
| Auth | `/v2/api/security/authenticate`, `/v2/api/security/oauth`, `/v2/api/security/logout` |
| Accounts | `/v2/api/accounts`, `/v2/api/accounts/{id}`, `/v2/api/account-types` |
| Transactions | `/v2/api/transactions`, `/v2/api/transactions/{id}`, `/v2/api/ai/auto-complete` |
| Categories | `/v2/api/categories` |
| Budgets | `/v2/api/budgets` (GET/POST/PATCH), `/v2/api/budgets/expenses` (GET/PATCH), `/v2/api/budgets/expenses/balance` |
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

Entities: accounts (owned + counterparty, including `iconFileCode`), transactions, budgets,
categories, contracts, expense groups, currencies, sync metadata. Reads serve from Room first;
mutations write through.

### Stale-while-revalidate

A `sync_metadata` table tracks `key → lastSyncedAt` per resource. `CacheRefresher`:

- `refreshNow(key, block)` runs the block and marks the key fresh (coalesced per-key via mutex).
- `launchIfStale(key, ttl, block)` triggers a background refresh on the `@ApplicationScope`
  coroutine scope only if the cache is older than the TTL (called from Flow `onStart` blocks).
- `refreshInBackground(key, block)` is the fire-and-forget refresh after mutations.

TTLs live in `CachePolicy`: 15 min for accounts, 60 min for catalogs (categories, contracts,
expense groups), 24 h for currencies and account types.

### Background sync (`SyncWorker`)

Periodic work (every 12 h, requires network) scheduled from `PledgerApp.onCreate`. Each run
refreshes currencies, account types, categories, contracts, expense groups, owned accounts,
counterparty accounts, and the current-month budget; fires local notifications when any
budget group exceeds 80% spend.

Transactions are refreshed on screen load rather than in the worker.

### Network monitoring

`NetworkMonitor` exposes connectivity as `Flow<Boolean>` for optional offline UI.

## Security

| Concern | Implementation |
|---------|----------------|
| Token storage | `EncryptedSharedPreferences` (AES-256-GCM) |
| Transport | User-supplied URL; cleartext permitted in debug config for dev servers |
| Biometric | Optional via settings (`BiometricPrompt`) |
| Session expiry | Proactive JWT refresh + 401 retry; `clearAuthTokens` keeps server URL |
| Platform | compile/target SDK 36 (Android 16); edge-to-edge in `MainActivity` |
| Build | Version catalog `gradle/libs.versions.toml`; AGP 8.13 / Gradle 8.13 |
| Release | ProGuard / R8 enabled |

## State Management

Unidirectional flow per screen:

```
User action → ViewModel → StateFlow<UiState> → Compose recomposition
```

Navigation arguments via `SavedStateHandle`. No Android framework types inside ViewModels beyond `SavedStateHandle`.

`UserPreferences` stores user-controlled UI defaults such as theme, display currency, app locale (`system` / `en` / `nl` / `de`), last transaction type, and finance experience mode (`guided` / `power`) so novice-friendly behavior does not block power-user workflows.

#### Localization

- User-visible copy lives in `res/values/strings.xml` (English) with `values-nl` and `values-de` translations.
- `AppLocale` + `LocaleManager` apply per-app language via `AppCompatDelegate`; see [LOCALIZATION.md](LOCALIZATION.md) and [ADR-018](adr/018-app-localization.md).
- Composables use `stringResource()`; enum labels use `ui/util/LocalizedLabels.kt`.

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
