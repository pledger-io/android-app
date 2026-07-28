# Senior Developer Codebase Audit — 2026-07-28

**Scope:** Full static audit of Pledger.io Android (`main` @ audit start)  
**Method:** Parallel senior reviews (security, architecture, data layer, UI/tests) with spot verification of Critical findings  
**Emulator/GUI:** Not available in this environment; validation is unit tests, lint, and `assembleDebug`

## Executive summary

The app has a solid Clean Architecture skeleton (domain interfaces, Hilt, Room migrations policy, encrypted token storage, release cleartext denied). However, the audit found **2 Critical** defects that can hang background refresh or brick upgrades, plus multiple **High** defects that corrupt offline cache, mis-report sync success, leak financial diagnostics, or accept invalid financial input.

This document is the remediation backlog. Issues are grouped into tasks for senior developers. Each task has acceptance criteria reviewed by a senior QA agent.

| Severity | Count (deduped) |
|----------|-----------------|
| Critical | 2 |
| High     | 18 |
| Medium   | 24 |
| Low / Info | 20+ |

---

## Critical findings

### C1 — `CacheRefresher` self-deadlock (non-reentrant mutex)

- **Files:** `data/cache/CacheRefresher.kt`; callers in `CategoryRepositoryImpl`, `TagRepositoryImpl`, `ContractRepositoryImpl`, `AccountRepositoryImpl`, `BudgetRepositoryImpl`
- **Issue:** `launchIfStale` / `refreshInBackground` call `refreshNow(key) { refreshX() }`, and `refreshX()` itself calls `refreshNow(key) { … }`. Kotlin `Mutex` is non-reentrant → permanent hang holding the per-key mutex; subsequent refreshes for that key block for the process lifetime.
- **Task:** [TASK-C1](#task-c1--fix-cacherefresher-reentrancy-deadlock)

### C2 — Room `MIGRATION_5_6` missing `tags` table

- **Files:** `data/local/PledgerDatabaseMigrations.kt`, schema `6.json` (includes `tags`), `PledgerDatabaseMigrationsTest.kt`
- **Issue:** Schema v6 requires `tags`, but 5→6 only creates `account_types` and `sync_metadata`. Upgrades from v5 fail Room validation. Only 5→6 and 6→7 are registered; migration tests mock SQL strings and would not catch this.
- **Task:** [TASK-C2](#task-c2--fix-room-migration-graph-and-tests)

---

## High findings (prioritized)

| ID | Area | Summary | Task |
|----|------|---------|------|
| H1 | Sync | `SyncWorkRunner` discards `Resource.Error` / failed flushes → WorkManager reports success | TASK-H1 |
| H2 | Transactions | Monthly `endDate` sent as inclusive while API is exclusive → last day of month missing | TASK-H2 |
| H3 | Transactions | Page-0 `deleteAll()` wipes entire TX cache for a single month page | TASK-H2 |
| H4 | Transactions | Offline fallback ignores `accountId` filter | TASK-H2 |
| H5 | Auth UX | Terminal 401 clears tokens but UI never navigates to login | TASK-H3 |
| H6 | Accounts | Opening balance collected in form but never sent to API | TASK-H4 |
| H7 | Concurrency | Untracked `loadNextPage` can merge into wrong month/filter | TASK-H5 |
| H8 | Mutations | Duplicate submit possible (forms / login) — no sync guard | TASK-H5 |
| H9 | Outbox | Create-then-IOException / crash after POST can duplicate transactions | TASK-H6 |
| H10 | Session | UI repository writes not gated by `SessionDataBarrier` → cross-user cache pollution | TASK-H7 |
| H11 | Pagination | Partial page failure still replaces cache and marks fresh | TASK-H8 |
| H12 | Budgets | Month snapshot replacement races / non-atomic | TASK-H8 |
| H13 | Balances | Balance/expense failures overwrite cached values with zero | TASK-H8 |
| H14 | Security | Bug reports retain query strings with financial PII; logs survive logout | TASK-S1 |
| H15 | Security | Logcat gets unsanitized messages; debug HTTP logs full URLs | TASK-S1 |
| H16 | Security | `pingServer(candidate)` hit via dynamic base URL interceptor (validates old host) | TASK-S2 |
| H17 | UI | Date picker UTC midnight → prior day in western timezones | TASK-U1 |
| H18 | UI | Amount validation accepts `Infinity` / NaN; transfers allow same account | TASK-U1 |

---

## Medium / Low (backlog — tasked, not started this cycle)

See [TASK-BACKLOG](#task-backlog--mediumlow) for the full list. Notable items: CancellationException swallowing, Moshi on domain `TransactionTemplate`, Resource living in `util`, biometric lock is UI-only, MFA JWT fail-open, cleartext on distributed debug APKs, localization debt, lint baseline burn-down, docs drift.

---

## Remediation plan (phased)

### Phase 0 — Stop the bleeding (this PR / immediate tasks)
1. Fix CacheRefresher deadlock (C1)
2. Fix Room 5→6 migration + real migration validation (C2)
3. Sync worker typed outcomes (H1)
4. Transaction date range / cache / offline account filter (H2–H4)
5. Form money/date validation (H17–H18)
6. Issue-report / log sanitization hardening (H14–H15)

### Phase 1 — Correctness & session integrity
7. Auth session expiry navigation (H5)
8. Opening balance or remove field (H6)
9. ViewModel mutation/paging guards (H7–H8)
10. Outbox idempotency keys (H9)
11. Session-scoped cache writes (H10)
12. Pagination/budget/balance cache integrity (H11–H13)
13. Server-change health check client (H16)

### Phase 2 — Security depth & architecture hygiene
14. Biometric / Keystore binding clarification or upgrade
15. Encrypt Room or minimize retention; purge invoice-scan cache
16. Move `Resource` into domain; strip Moshi from domain models
17. Localization sweep; burn lint baseline `NewApi` / `MissingTranslation` / `DefaultLocale`
18. Pin GitHub Actions to SHAs; Gradle distribution checksum; dependency verification

### Phase 3 — Maintainability
19. Split `TransactionFormViewModel` / `NavGraph` / `AccountRepositoryImpl`
20. Lifecycle-aware `collectAsStateWithLifecycle`
21. Docs sync (Room v7, test counts, reports/edit status)
22. Real `MigrationTestHelper` coverage for all exported versions

---

## Task definitions (senior developer assignments)

### TASK-C1 — Fix CacheRefresher reentrancy deadlock

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / data layer |
| **Priority** | P0 Critical |
| **Estimate shape** | Small: one coordination class + repository call sites + unit tests |

**Problem:** Nested `refreshNow` for the same key deadlocks.

**Implementation plan:**
1. Ensure `launchIfStale` / `refreshInBackground` never nest lock acquisition for the same key.
2. Preferred design: repositories expose an unlocked `fetchAndWrite()` used inside a single `refreshNow`; OR `launchIfStale` runs the block without calling through a second `refreshNow`.
3. Coalesce concurrent callers with a shared `Deferred` (or recheck staleness inside the lock after acquiring once).
4. Add unit tests that would hang/fail under the old nested-lock design (same-key `launchIfStale` → `refreshX` → `refreshNow`).

**Acceptance criteria (QA):**
- [ ] Same-key nested refresh completes (no deadlock) under test with a timeout.
- [ ] Concurrent stale launches for one key produce a single network fetch (coalescing preserved).
- [ ] Success still marks metadata fresh; failure does not.
- [ ] Existing repository unit tests still pass.

---

### TASK-C2 — Fix Room migration graph and tests

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / persistence |
| **Priority** | P0 Critical |

**Implementation plan:**
1. Update `MIGRATION_5_6` to create the `tags` table matching schema v6.
2. Document / inventory whether pre-v5 installs exist; add migrations or a documented wipe path if historical schemas are unavailable.
3. Replace or supplement string-mock tests with schema-aware validation (at minimum assert CREATE for `tags`; ideally `MigrationTestHelper` when instrumented tests are available).

**Acceptance criteria (QA):**
- [ ] `MIGRATION_5_6` creates all tables present in exported schema 6 (`tags`, `account_types`, `sync_metadata`).
- [ ] Tests fail if `tags` CREATE is removed (regression guard).
- [ ] App still builds; unit tests pass.

---

### TASK-H1 — SyncWorker must not report success on failed steps

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / background work |
| **Priority** | P0 High |

**Plan:** Change `SyncWorkRunner.runStep` to accept typed outcomes (`Resource`, `Boolean`, `FlushResult`). Aggregate retryable vs permanent failures; return `Result.retry()` for transient network failures.

**Acceptance criteria (QA):**
- [ ] Injected failing repository step → worker result is retry (or failure), not success.
- [ ] Successful path still returns success.
- [ ] Outbox `StoppedOnNetworkError` triggers retry.

---

### TASK-H2 — Transaction cache, date range, offline account filter

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / data layer |
| **Priority** | P0 High |

**Plan:**
1. Treat repository `endDate` as inclusive for callers; send `endDate.plusDays(1)` to API (match recent-transactions comment).
2. Stop calling `deleteAll()` on every unfiltered page-0; upsert or range-scoped replace inside a transaction.
3. Apply `accountId` in offline fallback filtering.
4. Never treat null successful body as authoritative empty wipe without explicit contract.

**Acceptance criteria (QA):**
- [ ] Unit tests prove last day of month is requested (exclusive API end).
- [ ] Offline account detail filter excludes other accounts’ txs.
- [ ] Loading month A then month B does not wipe A’s cached rows unexpectedly (or documented range prune only).

---

### TASK-U1 — Money/date form correctness

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / UI |
| **Priority** | P0 High |

**Plan:** Reject non-finite amounts; reject same-account transfers; convert date-picker millis via UTC; fix weak split validation tests to call production validators.

**Acceptance criteria (QA):**
- [ ] `"Infinity"`, `NaN`, zero/negative rejected where positive money required.
- [ ] Transfer with identical source/destination invalid.
- [ ] Date selected in UTC−8 timezone matches calendar day in tests.
- [ ] Split validation tests exercise production code paths.

---

### TASK-S1 — Issue report & logging sanitization

| Field | Value |
|-------|-------|
| **Assignee role** | Senior Android / security |
| **Priority** | P0 High |

**Plan:** Log method + route template + status + duration only; sanitize logcat the same as buffer; clear diagnostic logs on logout/session change; do not put financial query params, username, or full logs into GitHub URL / auto-clipboard without redacted preview + opt-in.

**Acceptance criteria (QA):**
- [ ] Captured issue payload cannot contain transaction description query values.
- [ ] Logcat path uses sanitized text.
- [ ] Logout clears buffered diagnostics.
- [ ] Unit tests for sanitizer cover amount/description query keys.

---

### TASK-H3 — Session expiry → login navigation

Navigate to login (clear back stack) when tokens cleared after unrecoverable 401. Expose `StateFlow`/event from session layer.

### TASK-H4 — Opening balance honesty

Wire backend-supported opening balance **or** remove/disable the UI field until supported. No silent discard.

### TASK-H5 — Mutation & paging race guards

Synchronous in-flight guards on submit/save/login; generation tokens on transaction paging.

### TASK-H6 — Outbox exactly-once

Client idempotency UUID; avoid `ExistingWorkPolicy.REPLACE` cancelling active flush; durable claim/complete of outbox rows.

### TASK-H7 — Session-scoped cache publication

Gate all authenticated Room writes on session generation / `SessionDataBarrier`.

### TASK-H8 — Cache integrity for pagination, budgets, balances

Failed pages must not mark fresh; budget month keys; never zero-out balances on auxiliary endpoint failure.

### TASK-S2 — Server change health check

Dedicated OkHttp client without `DynamicBaseUrlInterceptor` for candidate URL validation; HTTPS preference in release.

---

## Task backlog (Medium/Low)

| ID | Summary |
|----|---------|
| M1 | Rethrow `CancellationException` in all repository catch blocks |
| M2 | Move canonical `Resource` into domain; finish import migration |
| M3 | Strip Moshi from `TransactionTemplate` domain model |
| M4 | Fix `TransactionType.fromString` vs extraction mapping contradiction |
| M5 | Deep link: consume intent data; require positive IDs |
| M6 | CacheRefresher shared Deferred / freshness recheck after wait |
| M7 | DAO-backed reactive Flows where docs claim Room observation |
| M8 | Join balances by account ID, not name |
| M9 | Atomic tag rename or compensate on partial failure |
| M10 | Avoid `runBlocking` locale read on main in `Application.onCreate` |
| M11 | Typed `DomainError` instead of raw exception strings |
| M12 | Persist full transaction fields / Moshi JSON for tags & splits |
| M13 | Reject DTO defaults that invent amount/type/date |
| M14 | Invalidate derived caches on all TX mutations |
| M15 | `collectAsStateWithLifecycle` across screens |
| M16 | Remove dead `createTransaction` / unimplemented budget CRUD or implement |
| M17 | Remove unused Vico / dead `CurrencyProvider` path or wire them |
| M18 | Translate 17 missing NL/DE strings; remove baseline suppressions |
| M19 | Replace hardcoded English UI strings with resources |
| M20 | FLAG_SECURE on MFA QR / API token screens; clipboard sensitive flag |
| M21 | Delete invoice-scan cache files after OCR / on logout |
| M22 | Restrict debug cleartext to localhost; pin Actions SHAs; Gradle SHA-256 |
| M23 | MFA fail-closed on malformed JWT role claims |
| M24 | Docs drift: Room v7, test counts, reports/edit status, AGENTS.md |

---

## QA review protocol

For each tasked item:
1. Senior developer implements (or finalizes task brief if deferred).
2. Senior QA agent reviews against acceptance criteria, checks for regressions, and records **PASS / PASS WITH NOTES / FAIL**.
3. FAIL items return to the developer before merge.

QA review records for this cycle live in `docs/audit/qa/`.

---

## Out of scope / environment limits

- No instrumented emulator runs in this VM (no KVM).
- No live Pledger backend; network behavior covered by MockWebServer unit tests only.
- Backend API contract changes (true idempotency keys, balance partition IDs) require coordinated server work.
