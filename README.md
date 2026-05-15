# Pledger.io Android

A native Android client for [Pledger.io](https://github.com/pledger-io) — a self-hosted, open-source personal finance manager. Connects to your own Pledger REST backend (`/v2/api/…`).

## Tech Stack

| Area | Choice |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository (+ Use Cases where needed) |
| DI | Hilt (KSP) |
| Networking | Retrofit + OkHttp + Moshi |
| Images | Coil (authenticated via shared OkHttp client) |
| Database | Room (offline cache, v3 schema) |
| Async | Coroutines + Flow |
| Navigation | Jetpack Navigation Compose |
| Background work | WorkManager |
| Auth | JWT in EncryptedSharedPreferences |
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 35 |

## Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 21** for Gradle builds (JDK 25 is not supported by the current Android Gradle Plugin)

## Setup

1. Open the project in Android Studio.
2. Sync Gradle dependencies.
3. Run on an emulator or device (API 26+).
4. On first launch, enter your Pledger.io server base URL (validated via `GET /health`).
5. Sign in with your credentials (`POST /v2/api/security/authenticate`).

HTTP cleartext is allowed in debug via `network_security_config.xml` for local/dev servers; use HTTPS in production.

## Project Structure

```
app/src/main/java/com/pledgerio/app/
├── data/
│   ├── local/          # Room database, DAOs, entities
│   ├── remote/         # Retrofit API, DTOs, interceptors
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Use cases (e.g. dashboard aggregation)
├── ui/
│   ├── theme/          # Material 3 theme
│   ├── components/     # Shared composables (cards, AccountIcon, …)
│   ├── navigation/     # NavGraph, Screen routes
│   ├── onboarding/     # Server setup & login
│   ├── dashboard/
│   ├── transactions/   # List, detail, create form, filters
│   ├── accounts/       # List, detail, add/edit form
│   ├── budgets/
│   ├── reports/
│   └── settings/
├── di/                 # Hilt modules
└── util/               # SessionManager, SyncWorker, CurrencyProvider, …
```

## Features

### Implemented

- **Onboarding** — Configurable server URL (`DynamicBaseUrlInterceptor`), health check, JWT login
- **Dashboard** — Accounts overview, income/expense summary, recent transactions; FAB menu to add transaction or account
- **Transactions** — Paged list with month navigation, infinite scroll, type chips (income/expense), optional filters (category, expense/budget, contract) with inline autocomplete; pull-to-refresh; create transaction form with type-specific account inputs (creditor/debtor search vs owned-account dropdown); transaction detail with classification and account logos
- **Accounts** — List with balances (`/v2/api/balance` partitioned by account), detail with logo and transaction history, add/edit account (types from `/v2/api/account-types` plus creditor/debtor)
- **Currencies** — Fetched from API, cached in Room, used for `formatCurrency()` across the app
- **Budgets** — List and detail screens
- **Reports** — Report type selector UI (chart data integration in progress)
- **Settings** — Storage, biometric toggle, language/theme placeholders, logout
- **Offline** — Room cache with network fallback; periodic sync via WorkManager (accounts, currencies, budget alerts)
- **Account logos** — `iconFileCode` loaded from `GET /v2/api/files/{fileCode}` on account and transaction detail screens

### Planned / partial

- Edit transaction from detail screen
- Full reports charts (Vico dependency is present)
- Deep links from budget notifications

## Navigation Routes

| Route | Screen |
|-------|--------|
| `server_setup` | Server URL entry |
| `login` | Authentication |
| `dashboard` | Main overview (bottom tab) |
| `transactions` | Transaction list (bottom tab) |
| `transaction/add` | Create transaction |
| `transaction/{id}` | Transaction detail |
| `accounts` | Account list (bottom tab) |
| `account/add` | Create account |
| `account/{id}` | Account detail |
| `account/{id}/edit` | Edit account |
| `budgets` / `budget/{id}` | Budget list & detail |
| `reports` | Reports (bottom tab) |
| `settings` | Settings |

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — Layers, data flow, API integration, UI patterns
- [Architecture Decision Records](docs/adr/README.md) — Rationale for major technical choices

Backend API reference: [pledger-io/rest-application](https://github.com/pledger-io/rest-application) (`/v2/api/…` contract).

## Building

```bash
./gradlew assembleDebug
```

On Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
```

## License

This app is designed to work with the Pledger.io backend. See the backend repository for server licensing.
