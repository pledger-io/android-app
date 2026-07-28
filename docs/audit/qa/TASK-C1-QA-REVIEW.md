# TASK-C1 QA Review — CacheRefresher deadlock fix

**Date:** 2026-07-28  
**Verdict:** PASS WITH NOTES

## Scope reviewed

- `app/src/main/java/com/pledgerio/app/data/cache/CacheRefresher.kt`
- Cache-refresh call sites in `CategoryRepositoryImpl`, `TagRepositoryImpl`,
  `ContractRepositoryImpl`, `AccountRepositoryImpl`, and `BudgetRepositoryImpl`
- `app/src/test/java/com/pledgerio/app/data/cache/CacheRefresherTest.kt`

No acceptance-blocking defects were found. Production code was not modified.

## Acceptance criteria evidence

### 1. Same-key nested refresh completes under timeout — PASS

- `same-key nested background refresh completes without deadlock` exercises
  `launchIfStale(KEY) { refreshNow(KEY) { ... } }` and requires completion inside
  `withTimeout(100)`.
- `same-key forced background refresh completes without deadlock` provides the
  equivalent regression guard for `refreshInBackground`.
- `refreshNow` carries a coroutine-local set of active keys and executes a nested
  same-key block without trying to reacquire coordination. The outer refresh remains
  responsible for freshness metadata.
- Focused execution passed: 5 tests, 0 failures, 0 ignored.

### 2. Concurrent stale launches for one key produce one network fetch — PASS

- `concurrent stale launches perform one refresh` issues ten stale launches for the
  same key while the first refresh is held on a deferred barrier. The execution count
  remains one before and after release.
- `concurrent refreshNow callers share one execution` separately verifies that a
  follower receives the leader's result and does not execute its own block.
- `launchCoalesced` synchronizes per-purpose job maps, while `refreshNow` synchronizes
  the shared per-key in-flight result map. A second stale check after leadership is
  acquired prevents a redundant fetch if another caller refreshed the key meanwhile.
- Repository stale/background paths now call unlocked fetch-and-write helpers; public
  synchronous refresh methods are the only paths that wrap those helpers in
  `refreshNow`. No scoped call site retains the original nested-lock pattern.

### 3. Success marks fresh; failure does not — PASS

- `success marks fresh and failure leaves key stale` verifies a `Resource.Success`
  creates metadata and a `Resource.Error` leaves metadata absent.
- The nested and concurrent stale-launch success tests also assert that freshness
  metadata is written.
- Static review confirms `markFresh` runs only after a `Resource.Success`; errors and
  thrown exceptions do not mark the key fresh.

### 4. Existing repository unit tests still pass — PASS

- Full command: `./gradlew testDebugUnitTest --no-daemon`
- Result: `BUILD SUCCESSFUL`; 326 tests, 0 failures, 0 ignored.
- The repository package contributed 86 passing tests, including the reviewed
  Category, Tag, Contract, Account, and Budget repository coverage.

## Residual risks and follow-ups

- The stale-launch test creates overlapping work on one test scheduler, but does not
  call `launchIfStale` simultaneously from multiple real threads. The synchronized
  implementation is sound on static review, so this is not a release blocker. Add a
  barrier-based `Dispatchers.Default` stress test if stronger race-regression coverage
  is desired.
- Shared in-flight results are keyed by `String` and returned through an unchecked
  generic cast. Callers must keep one payload type per key. Typed cache keys would
  remove this latent misuse risk.
- Cancellation/exception propagation to coalesced followers is not explicitly tested.
  Add leader-cancellation and thrown-exception tests as hardening coverage.

## Test commands

```text
./gradlew testDebugUnitTest --tests 'com.pledgerio.app.data.cache.CacheRefresherTest' --no-daemon
BUILD SUCCESSFUL

./gradlew testDebugUnitTest --no-daemon
BUILD SUCCESSFUL — 326 tests, 0 failures, 0 ignored
```
