# Mobile Application Improvement Audit

**Project:** `pledger-io/android-app`  
**Date:** 2026-05-24  
**Audit type:** Static code and configuration review (architecture, security, reliability, testing, and maintainability)

## Goal

This document captures concrete improvements to strengthen the Android app quality, reduce production risk, and increase delivery speed. Recommendations are prioritized so the team can execute in phases.

## Implementation Progress

Legend: `[x]` done, `[~]` in progress, `[ ]` not started.

- [x] Critical #1: release-safe logging (`BODY` in debug, `NONE` in release).
- [x] Critical #2: cleartext disabled by default with debug-only override.
- [x] High #3: destructive migration fallback removed; explicit migration scaffold added; Room schema export enabled.
- [x] High #4: blocking interceptor cleanup removed (`clearAllUserDataAsync` on app scope).
- [x] High #6: UI/data layering tightened for reports and invoice-scan (`ReportsOverviewStore`, `ProcessInvoiceScanUseCase`, `InvoiceTextReader`).
- [~] High #5/#7: major use-case consistency cleanup completed (`LoginUseCase`, `GetDashboardDataUseCase`, `GetBudgetsUseCase`, `GetTransactionsUseCase` wired in ViewModels) and transaction-form helpers extracted (account preservation + submission/id-resolution + auto-classify apply logic); additional decomposition still possible.
- [x] High #8: instrumented/UI test coverage started (`androidTest` smoke test + CI instrumented job).
- [x] Medium #9: `allowBackup` hardened (`false`).
- [x] Medium #10: `CurrencyProvider` singleton access removed (`getInstance`/`setInstance` retired; formatting no longer relies on static provider state).
- [~] Medium #11: migration bridge added and domain-layer imports moved to `domain/common/Resource`; data/ui layers still pending for full completion.
- [~] Medium #12: transaction offline fallback improved for date/type/text filters and now explicitly errors for unsupported offline ID filters (category/expense/contract); full offline parity still pending schema/API alignment.
- [x] Medium #13: `SyncWorker` now classifies transient vs permanent failures (`IOException` retry, auth failures fail).
- [x] Medium #14: CI lint gate added (`lintDebug`) with baseline to enforce no new lint debt.
- [x] Low #15: release hygiene improved (non-static `versionCode` via env/CI and required release signing in release workflow); docs aligned.
- [x] Test expansion #1: added `AuthInterceptor` tests.
- [x] Test expansion #2: added `ReportsViewModel` cache/refresh unit coverage.
- [x] Test expansion #3 (partial): login flow now executes through `LoginUseCase` in production code paths.
- [x] Test expansion #4 (partial): added `SyncWorker` failure-classification unit coverage.
- [x] Test expansion #6 (initial): added first Compose `androidTest` smoke test (`MainActivitySmokeTest`).
- [x] Test expansion #5 (initial): added migration test scaffolding for 5→6.

## What is working well

- Clear package structure with `data` / `domain` / `ui` separation and ADR documentation.
- Strong repository-level unit testing footprint.
- Solid foundations for offline-first behavior (Room + cache policy + sync metadata).
- Good auth/session primitives (`SessionManager`, token refresh, logout cleanup).
- Thoughtful product UX direction documented in feature plans.

## Priority Findings

### Critical

1. **Release logging leaks sensitive API payloads**
   - Evidence: `NetworkModule` always sets `HttpLoggingInterceptor.Level.BODY`.
   - Risk: JWTs, credentials, and financial payloads can appear in logs.
   - Recommendation:
     - Use `if (BuildConfig.DEBUG) BODY else NONE`.
     - Keep issue-report logging sanitized and metadata-only.

2. **Cleartext traffic currently permitted globally**
   - Evidence: `network_security_config.xml` has `<base-config cleartextTrafficPermitted="true">`.
   - Risk: Release traffic can be sent over insecure HTTP.
   - Recommendation:
     - Restrict cleartext to debug only (debug config/flavor).
     - Enforce HTTPS in release builds.
     - Align README wording to actual behavior.

### High

3. **Destructive Room migration policy**
   - Evidence: `fallbackToDestructiveMigration()` + `exportSchema = false`.
   - Risk: app upgrades can wipe local data/cache silently.
   - Recommendation:
     - Add explicit Room migrations.
     - Turn on schema export and commit schema history.
     - Remove destructive fallback for release.

4. **Blocking cleanup call inside network interceptor**
   - Evidence: `AuthInterceptor` calls `clearAllUserDataBlocking()` (uses `runBlocking`).
   - Risk: thread blocking in OkHttp chain, potential latency spikes.
   - Recommendation:
     - Return auth failure immediately.
     - Trigger async session-expired cleanup on app scope.
     - Keep interceptor non-blocking.

5. **Very large ViewModels increase change risk**
   - Evidence: `TransactionFormViewModel` and `TransactionsViewModel` hold multiple responsibilities.
   - Risk: regression probability and test complexity rise with each feature.
   - Recommendation:
     - Extract state reducers, input validation, filter orchestration, and side-effect handlers.
     - Move orchestration into focused domain/use-case components.

6. **Layering violations from UI directly depending on data-layer internals**
   - Evidence: `ReportsViewModel` depends on `ReportsOverviewCache`; `InvoiceScanViewModel` depends on `InvoiceTextExtractor`.
   - Risk: weak architectural boundaries and harder replacements/testing.
   - Recommendation:
     - Access through domain-level interfaces/use cases only.
     - Keep `ui` unaware of data implementation types.

7. **Domain use-case layer is inconsistent**
   - Evidence: some use cases are used (`CreateInitialBudgetUseCase`, `SaveBudgetExpenseUseCase`), while others exist but are not wired (`LoginUseCase`, `GetDashboardDataUseCase`, `GetTransactionsUseCase`, `GetBudgetsUseCase`).
   - Risk: duplicate orchestration patterns and architectural drift.
   - Recommendation:
     - Decide one direction:
       - Wire use cases consistently for all major flows, or
       - Remove unused use cases and simplify docs.

8. **No instrumented/UI test coverage**
   - Evidence: no files in `app/src/androidTest`.
   - Risk: navigation, compose rendering, and integration regressions escape unit tests.
   - Recommendation:
     - Add smoke UI tests for onboarding/login/main-tab navigation/transaction list.
     - Add a CI job for instrumentation tests (managed emulator or device lab).

### Medium

9. **Backup posture should be tightened**
   - Evidence: `android:allowBackup="true"` in manifest.
   - Risk: session-adjacent data may be restorable on some device/backup combinations.
   - Recommendation:
     - Set `allowBackup=false` or add strict backup rules excluding secure/session stores.

10. **Global singleton usage (`CurrencyProvider`) bypasses DI**
    - Evidence: `CurrencyProvider.getInstance()` accessed during cleanup.
    - Risk: hidden coupling and harder testability/lifecycle control.
    - Recommendation:
      - Replace singleton access with injected abstraction across app.

11. **`Resource` type location blurs boundaries**
    - Evidence: `Resource` resides in `util` but is used across domain and data.
    - Risk: cross-layer coupling through a generic utility namespace.
    - Recommendation:
      - Move to a dedicated `domain/common` location or migrate to `Result` + typed domain errors.

12. **Offline behavior for transactions is partial**
    - Evidence: limited fallback behavior in transaction paging/filtering paths.
    - Risk: inconsistent user experience when offline.
    - Recommendation:
      - Define explicit cache semantics per filter/page, or
      - Explicitly message which query modes are online-only.

13. **WorkManager retry policy is broad**
    - Evidence: worker retry path is not strongly classified by error type.
    - Risk: repeated retries for permanent failures.
    - Recommendation:
      - Classify transient vs permanent failures.
      - Use backoff policies and fail fast on unrecoverable cases.

14. **Static analysis gates are missing in CI**
    - Evidence: CI runs unit tests + debug build only.
    - Risk: style, correctness, and Android lint issues slip into main.
    - Recommendation:
      - Add `lintDebug`.
      - Add Detekt/ktlint (or equivalent) with baseline and fail-on-new policy.

### Low

15. **Release hygiene and doc drift**
    - Evidence:
      - `versionCode = 1` is static.
      - README says Room schema v3 while DB is v6.
      - Release signing falls back to debug key when missing config.
    - Recommendation:
      - Enforce versioning policy.
      - Keep docs synced with implementation.
      - Fail release build when production signing material is absent.

## Recommended Test Expansion

Add focused tests in this order:

1. `AuthInterceptor` behavior (401 retry path, token refresh fail path, auth endpoint bypass).
2. `ReportsViewModel` cache + refresh behavior.
3. `LoginViewModel` and server setup validation behavior.
4. `SyncWorker` using test worker builder + fake repositories.
5. Room migration tests for each schema upgrade.
6. Compose smoke tests for critical flows in `androidTest`.

## 30 / 60 / 90 Day Execution Plan

## 0-30 days (Risk reduction)

- Fix logging level for release.
- Restrict cleartext to debug only.
- Remove blocking cleanup from interceptor path.
- Add lint gate to CI.
- Start instrumented smoke tests.

## 31-60 days (Architecture hardening)

- Implement Room migrations + schema export.
- Refactor reports/invoice scan to domain-facing interfaces.
- Split transaction ViewModel responsibilities.
- Resolve use-case consistency decision and apply it.

## 61-90 days (Quality at scale)

- Expand UI/integration test suite.
- Improve offline consistency strategy for transactions/reports.
- Adopt static analysis stack fully (lint + detekt + formatting checks).
- Strengthen release process checks (signing/version/docs sync).

## Suggested Tracking Format

Create epics/issues grouped by:

- **Security hardening**
- **Data integrity and migrations**
- **Architecture and maintainability**
- **Test coverage and CI quality gates**
- **Release engineering**

Each issue should include: owner, effort estimate, risk level, acceptance criteria, and rollback strategy.

---

If needed, this audit can be converted into a sprint-by-sprint issue backlog with file-level task breakdowns.
