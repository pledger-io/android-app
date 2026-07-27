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
| Database | Room (offline cache, v7 schema with explicit migrations) |
| Async | Coroutines + Flow |
| Navigation | Jetpack Navigation Compose |
| Background work | WorkManager |
| Auth | JWT + refresh token in EncryptedSharedPreferences; proactive refresh |
| Build | AGP 8.13, Gradle 8.13, version catalog (`gradle/libs.versions.toml`) |
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 36 (Android 16) |

## Prerequisites

- **Android Studio** Meerkat (2024.3.1) or newer (Android 16 SDK support)
- **JDK 21** for Gradle builds (JDK 25 is not supported by the Android Gradle Plugin)
- **Android SDK Platform 36** and **Build-Tools 36.0.0** (install via SDK Manager)

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

- **Onboarding** — Configurable server URL (`DynamicBaseUrlInterceptor`), health check, JWT login; server URL kept after auth errors or logout
- **Dashboard** — Accounts overview, income/expense summary, recent transactions; FAB menu to add transaction or account
- **Transactions** — Paged list with month navigation, infinite scroll, type chips (income/expense), optional filters (category, expense/budget, contract) with inline autocomplete; pull-to-refresh; create transaction form with type-specific account inputs (creditor/debtor search vs owned-account dropdown), **Guided/Power** experience defaults, and one-tap AI auto-classification suggestions (expense group, category, tags); transaction detail with classification and account logos
- **Accounts** — Grouped list with **All / Owned / Parties** filter chips (always visible, including empty states); type-aware add menu, balances from `/v2/api/balance`; detail with logo, type explanation, transaction history, and delete; add/edit with catalog-driven fields (see [Account types](docs/ACCOUNTS.md))
- **Currencies** — Fetched from API, cached in Room, used for `formatCurrency()` across the app
- **Budgets** — Initial budget setup on 404; monthly overview per expense group; manage groups (add/edit); detail screen (see [Budgets](docs/BUDGETS.md))
- **Reports** — Report type selector UI (chart data integration in progress)
- **Search** — Global search from the Dashboard for transactions (last 6 months), owned/counterparty accounts (cache-first owned), and categories; category rows open Transactions for the current month
- **Settings** — Storage, biometric unlock, **language** (English / Dutch / German / system), theme, display currency, finance experience mode (Guided/Power), **budget alerts** (enable + threshold), in-app bug reports (logs + GitHub issue), logout
- **Offline** — Room cache with network fallback; create-transaction outbox queues offline/IO failures and flushes via WorkManager; periodic sync (accounts, currencies, budget alerts with deep links)
- **Account logos** — `iconFileCode` loaded from `GET /v2/api/files/{fileCode}` on account and transaction detail screens

### Planned / partial

- Edit transaction from detail screen
- Full reports charts (Vico dependency is present)

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
| `account/add?type={type}` | Create account (optional type preselect) |
| `account/{id}` | Account detail |
| `account/{id}/edit` | Edit account |
| `budgets` | Budget overview (current month); add expense groups via FAB |
| `budget/{id}` | Expense group detail; edit monthly budget |
| `reports` | Reports (bottom tab) |
| `search` | Global search (from Dashboard) |
| `settings` | Settings |

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — Layers, data flow, API integration, UI patterns
- [Account types](docs/ACCOUNTS.md) — Owned vs counterparty accounts, type codes, transaction mapping
- [Budgets](docs/BUDGETS.md) — Initial setup, expense groups, API mapping
- [Transaction form redesign](docs/TRANSACTION_FORM_REDESIGN.md) — Planned UX for creating transactions (type-first flow, amount hero, contextual account labels)
- [Invoice scan to transaction plan](docs/INVOICE_SCAN_TRANSACTION_PLAN.md) — Proposed OCR + text extraction pipeline to prefill new transactions
- [Usability modes](docs/USABILITY_MODES.md) — Guided mode for novices and Power mode for advanced users
- [Localization](docs/LOCALIZATION.md) — English, Dutch, and German (extensible)
- [Architecture Decision Records](docs/adr/README.md) — Rationale for major technical choices

Backend API reference: [pledger-io/rest-application](https://github.com/pledger-io/rest-application) (`/v2/api/…` contract).

## Building

### Local scripts

| Script | Output |
|--------|--------|
| `scripts/build-debug.sh` / `build-debug.ps1` | Debug APK |
| `scripts/build-release.sh` / `build-release.ps1` | Release APK (minified) |

Or use Gradle directly:

```bash
./gradlew assembleDebug
```

On Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
```

Requires **JDK 21**.

### GitHub Actions

| Workflow | Trigger | Result |
|----------|---------|--------|
| [CI](.github/workflows/ci.yml) | Push / PR to `main` or `master` | Unit tests + lint + instrumented smoke test + debug APK artifact |
| [Release](.github/workflows/release.yml) | **Publish** a GitHub Release, or manual run | Release APK attached to the release |

**Distributing an APK on GitHub Release**

1. Push a tag (e.g. `v1.0.0`) and [create a release](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-your-repository) from that tag, or create a release in the GitHub UI.
2. Click **Publish release** (draft releases do not run the workflow).
3. The **Release** workflow builds `assembleRelease` and uploads `pledger-io-<tag>-<version>.apk` to the release assets.

To test the release build without publishing: **Actions → Release → Run workflow**. The APK is saved as a workflow artifact.

### In-app bug reports

Users can report problems from **Settings → About → Report a problem**. The app collects sanitized recent logs, prefills the org [bug report form](https://github.com/pledger-io/.github/issues/new?template=bug_report.yml) in the browser (summary, description, environment, logs), and the user taps **Submit** on GitHub — no app credentials required. Sign in to GitHub only if the repository requires it to open issues.

**Production signing is required for release builds** — configure these repository secrets:

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Encode a keystore (example):

```bash
base64 -w 0 release.keystore > keystore.b64
```

## License

This app is designed to work with the Pledger.io backend. See the backend repository for server licensing.
