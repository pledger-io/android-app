# Pledger.io Android

A native Android application for [Pledger.io](https://github.com/pledger-io) — a self-hosted, open-source personal finance manager.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Repository + UseCases
- **DI:** Hilt
- **Networking:** Retrofit + OkHttp + Moshi
- **Database:** Room (offline caching)
- **Async:** Coroutines + Flow
- **Navigation:** Jetpack Navigation Compose
- **Auth:** JWT via EncryptedSharedPreferences
- **Min SDK:** 26 (Android 8.0)

## Setup

1. Open the project in Android Studio (Ladybug or newer)
2. Sync Gradle dependencies
3. Run on an emulator or device (API 26+)
4. On first launch, enter your Pledger.io server URL
5. Sign in with your credentials

## Project Structure

```
app/src/main/java/com/pledgerio/app/
├── data/
│   ├── local/          # Room database, DAOs, entities
│   ├── remote/         # Retrofit API service, DTOs
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── ui/
│   ├── theme/          # Material 3 theme (colors, typography)
│   ├── components/     # Shared composables
│   ├── dashboard/      # Dashboard screen
│   ├── transactions/   # Transactions list & detail
│   ├── accounts/       # Accounts list & detail
│   ├── budgets/        # Budget management
│   ├── reports/        # Analytics & charts
│   ├── settings/       # App settings
│   ├── onboarding/     # Server setup & login
│   └── navigation/     # Navigation graph
├── di/                 # Hilt DI modules
└── util/               # Extensions, session manager
```

## Features

- Dashboard with net worth, income/expenses, recent transactions
- Transaction management (create, edit, delete, filter)
- Account management with balance tracking
- Budget tracking with progress indicators
- Reports & analytics
- Offline caching with Room
- Biometric authentication
- Dark-first design with emerald green accent

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — System design, layer responsibilities, data flow
- [Architecture Decision Records](docs/adr/README.md) — Rationale behind every significant technical choice

## Building

```bash
./gradlew assembleDebug
```

## License

This app is designed to work with the Pledger.io backend.
See [pledger-io/rest-application](https://github.com/pledger-io/rest-application) for the API.
