# TASK-H2 QA Review — Transaction Cache, Dates, Offline Filter

**Date:** 2026-07-28  
**Verdict:** PASS WITH NOTES

## Acceptance evidence

1. **PASS — Last day of month is included through an exclusive API end.**
   - `TransactionRepository` documents `startDate` through `endDate` as an inclusive repository contract.
   - `TransactionRepositoryImpl.getTransactionsPage` sends `endDate.plusDays(1)` to the API.
   - `getTransactionsPage converts inclusive end date to exclusive API end` requests July 1–31 and verifies the API receives `endDate = "2026-08-01"`.

2. **PASS — Offline account detail filtering excludes unrelated transactions.**
   - The exception fallback includes a cached transaction only when the requested account matches its source or destination account.
   - `getTransactionsPage offline fallback filters source and destination account` supplies source match, destination match, and unrelated rows; only the two matching rows are returned.

3. **PASS — Loading month B does not wipe month A's cached rows.**
   - Successful pages are upserted with `insertAll`; `getTransactionsPage` no longer calls the global `deleteAll`.
   - `getTransactionsPage upserts month pages without globally wiping cache` loads June and July, verifies both page rows are inserted, and verifies `deleteAll()` is never called.
   - This implementation uses retention/upsert, not a documented range prune.

## Verification

Focused command:

```text
./gradlew testDebugUnitTest --tests "com.pledgerio.app.data.repository.TransactionRepositoryImplTest" --no-daemon
```

Result: `BUILD SUCCESSFUL`; 13 tests executed, 0 failures, 0 errors.

## Residual risks

- Upsert-only retention prevents cross-month loss but does not reconcile server-side deletions or rows removed from a reloaded range; stale cached transactions can remain until another explicit deletion or cache clear.
- The month-retention test verifies repository/DAO interactions with a mocked DAO rather than persistence against Room. It guards the former global-wipe regression, but not future changes to actual DAO conflict or pruning behavior.
- Offline fallback is limited to page 0 and exception paths. HTTP error responses do not use cached fallback; this does not violate the stated TASK-H2 criteria but remains a degraded-connectivity limitation.

No production-code changes were required.
