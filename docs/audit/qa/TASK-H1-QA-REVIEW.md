# TASK-H1 QA Review — SyncWorker typed outcomes

## Verdict

**PASS WITH NOTES**

All three acceptance criteria are satisfied by the implementation and focused JVM tests. No production-code change was required.

## Acceptance evidence

| Criterion | Result | Evidence |
|---|---|---|
| Failing repository step returns retry/failure, not success | PASS | `SyncWorkRunner.runResourceStep` classifies `Resource.Error` as retryable or permanent, `runBooleanStep` classifies `false` as permanent, and `SyncFailureAccumulator` returns a non-completed run outcome when any failure is recorded. `SyncWorker.resultFor` maps those outcomes to `Result.retry()` or `Result.failure()`. `network resource error is retryable and aggregates permanent failures` injects both a repository `Resource.Error` and a `false` sync result, then verifies `Retry`; `SyncWorkerTest` separately verifies permanent outcome → `Failure`. |
| Successful path returns success | PASS | When every typed step succeeds, `SyncWorkRunner.run` returns `SyncRunOutcome.Completed`; `SyncWorker.resultFor` maps it to `Result.success()`. Covered by `current generation completes sync and returns budgets for guarded publication` and `completed sync maps to worker success`. |
| Outbox `StoppedOnNetworkError` returns retry | PASS | `runFlushStep` maps `FlushResult.StoppedOnNetworkError` to `RETRYABLE_FAILURE`, which aggregates to `SyncRunOutcome.RetryableFailure` and then `Result.retry()`. Covered directly by `outbox network stop is retryable`. |

## Test evidence

- Focused command: `./gradlew testDebugUnitTest --tests 'com.pledgerio.app.util.SyncWorkRunnerTest' --tests 'com.pledgerio.app.util.SyncWorkerTest' --no-daemon`
- Result: `BUILD SUCCESSFUL`; 15 tests executed, 0 failures, 0 errors, 0 skipped (7 runner tests and 8 worker tests).
- Regression command: `./gradlew clean testDebugUnitTest --no-daemon`
- Result: `BUILD SUCCESSFUL`; the full test task was restored from the Gradle build cache.

## Residual risks

- Tests exercise `SyncWorkRunner` and `SyncWorker.resultFor` separately rather than constructing a real `SyncWorker` and invoking `doWork`; the production branch between them is direct, so this is a low integration risk.
- `Resource.Error` instances without an exception or recognizable HTTP status default to retryable. This prevents false success but can repeatedly retry an unclassified permanent error.
- Missing/blank sync generation and stale-session outcomes intentionally map to success. A scheduler wiring defect that omits the generation would therefore not surface as failed work; this is outside the repository-failure criteria.
- A forced uncached `--rerun-tasks` regression attempt stopped before tests because KSP expected a missing generated cache directory. A subsequent clean build succeeded, and the focused tests executed normally; this is a build-cache/tooling risk rather than evidence of a TASK-H1 defect.
