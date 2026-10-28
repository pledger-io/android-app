# Design constraints

These describe current implementation boundaries. [ADRs](adr/README.md) capture decision rationale; design plans are historical proposals and checklists.

| Area | Constraint and implication | Evidence / rationale |
|---|---|---|
| Platform | Native Android, minimum API 26, compile/target API 36. Gradle uses JDK 21; source/JVM target is 17. | `gradle/libs.versions.toml`, `app/build.gradle.kts`, [Development](DEVELOPMENT.md) |
| Structure | One `:app` module. UI/domain/data boundaries are package conventions; avoid UI dependencies on concrete data adapters. Use cases are added where orchestration warrants them. | [ADR-001](adr/001-mvvm-clean-architecture.md), [ADR-011](adr/011-single-module-structure.md), [ADR-019](adr/019-security-and-quality-hardening.md) |
| Backend | User-configured REST server, `/health` validation and `/v2/api` contract. Dynamic host substitution replaces scheme/host/port only; path-prefixed hosting needs additional support. | `PledgerApiService`, `DynamicBaseUrlInterceptor` |
| Transport | Release cleartext disabled; debug resource override permits local HTTP. HTTP body logging is debug-only; production logs/report text must be sanitized. | `res/xml/network_security_config.xml`, debug override, `NetworkModule`, [ADR-019](adr/019-security-and-quality-hardening.md) |
| Credentials | Interactive JWT/refresh stored in EncryptedSharedPreferences. Durable API tokens are distinct, masked in lists and revealed once after creation. | [ADR-006](adr/006-encrypted-shared-preferences.md), [ADR-020](adr/020-api-token-sessions.md) |
| MFA | Pre-verification credentials stay in memory; no full session activation or sync until TOTP succeeds. OIDC onboarding remains deferred. | [ADR-021](adr/021-mfa-enroll-verify.md) |
| Session isolation | Logout/server changes must invalidate the sync generation, coordinate cleanup through the session barrier, and clear prior user data. WorkManager cancellation alone is not proof a worker finished. | `AuthenticatedSessionCoordinator`, `SessionDataBarrier`, `SyncSessionGuard` |
| Storage | Room v8 uses exported schemas and explicit migrations (5→6, 6→7 and 7→8). No destructive migration fallback. Backup is disabled. Token encryption does not encrypt all cached financial data. | `PledgerDatabaseMigrations`, `DatabaseModule`, manifest, [ADR-019](adr/019-security-and-quality-hardening.md) |
| Offline behavior | Cached reads can be stale/incomplete. Create-only outbox requires resolved account IDs; offline edit/delete/split PATCH and creating missing accounts are unsupported. Category/expense/contract ID transaction filters need network. | `TransactionOutboxRepositoryImpl`, `TransactionRepositoryImpl`, [outbox design](feature/offline-transactions/offline-transaction-outbox.md) |
| Outbox delivery | Server is authoritative. Unknown POST outcomes use client-side reconciliation; no server idempotency key or multi-device conflict protocol guarantees exactly-once delivery. Session cleanup removes pending user writes. | `TransactionOutboxRepositoryImpl`, `LocalDataCleaner` |
| Background work | Connected periodic work has a 12-hour requested interval, not an exact execution time. Alert permission, preferences, deduplication, and current-session checks govern publication. | `SyncWorkScheduler`, `SyncWorker`, `BudgetAlertNotifier` |
| Financial semantics | `DEBIT` is income, `CREDIT` is expense; account catalog determines owned vs party accounts. Preserve server semantics in UI labels and requests. | [Accounts](feature/accounts/accounts.md) |
| OCR | ML Kit reads camera/imported images locally; backend interprets extracted text. User reviews the resulting transaction before save. Current picker accepts images, not PDFs. | `InvoiceTextExtractor`, `ProcessInvoiceScanUseCase`, `InvoiceScanScreen` |
| Reports | Depend on supported server partitions and some client aggregation. In-memory caches need mutation invalidation; report errors must not be presented as valid zero balances. | `ReportRepositoryImpl`, `ReportsOverviewCache`, `TransactionMutationInvalidator` |
| Navigation | Navigation Compose with custom `pledger://` links; protected navigation waits for authentication. Verified HTTPS App Links and server push are separate future work. | [ADR-008](adr/008-navigation-compose.md), [ADR-017](adr/017-deep-links-and-reports.md) |
| UX / language | Guided and Power expose the same capabilities. Use string resources; keep English/Dutch/German resources aligned and preserve formatting placeholders. | [Experience modes](feature/experience-modes/experience-modes.md), [Localization](feature/localization/localization.md), [ADR-018](adr/018-app-localization.md) |
| Quality / release | CI runs unit tests, lint with baseline, and instrumented smoke checks. Release builds use minification/shrinking and require CI signing configuration. Do not grow lint debt to hide regressions. | [Development](DEVELOPMENT.md), `.github/workflows`, [ADR-019](adr/019-security-and-quality-hardening.md) |

## Known architectural debt

The Resource compatibility bridge and remaining large ViewModels are gradual refactoring work. A single module cannot enforce all intended dependency boundaries. Offline filtered reads lack full server parity, and outbox reconciliation cannot replace a server idempotency contract. The [audit archive](audit/SENIOR_AUDIT_2026-07-28.md) records historical findings; inspect current source before treating an audit item as unresolved.

Return to [C4 architecture](ARCHITECTURE.md) or [documentation home](README.md).
