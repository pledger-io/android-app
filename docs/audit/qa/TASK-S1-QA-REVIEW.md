# TASK-S1 QA Review — Issue report and logging sanitization

**Date:** 2026-07-28  
**Verdict:** PASS WITH NOTES

All four explicit acceptance criteria are satisfied. No production-code change was required.

## Scope reviewed

- `IssueLogInterceptor`, `AppLogCollector`, `AppLog`, and `LogSanitizer`
- `IssueReportFormatter`, `IssueReportUrlBuilder`, and `IssueReportRepositoryImpl`
- `AuthenticatedSessionCoordinator`, `AuthRepositoryImpl`, and session-clear call sites
- `SettingsScreen`, `IssueReportViewModel`, and `ReportIssueDialog`
- Network interceptor wiring and the related JVM unit tests

## Acceptance evidence

| Criterion | Result | Evidence |
|---|---|---|
| Captured issue payload cannot contain transaction description query values | PASS | `IssueLogInterceptor` records only HTTP method, an allowlisted route template, status/failure, and duration; it never reads the query. `AppLogCollector.export`, `IssueReportFormatter.buildLogExcerpt`, and `IssueReportUrlBuilder` provide additional sanitization before logs enter the issue URL. `IssueLogInterceptorTest` verifies transaction `description=Salary%20July` and `amount=1234.56` are absent, while `IssueReportRepositoryImplTest` decodes the final GitHub URL and verifies captured values are absent. |
| Logcat path uses sanitized text | PASS | `AppLogCollector.log` sanitizes the message and stack trace once, then sends those sanitized values to the in-memory buffer, disk, and `logcatWriter`. `AppLogCollectorTest` verifies both logcat output and exported diagnostics exclude description and amount values. `NetworkModule` also removes `HttpLoggingInterceptor`, closing the separate debug raw-URL path; `NetworkModuleTest` guards this wiring. |
| Logout clears buffered diagnostics | PASS | `AuthRepositoryImpl.logout` delegates to `AuthenticatedSessionCoordinator.logout`, which calls `appLog.clear()` after best-effort remote logout, work cancellation, credential clearing, and local-data cleanup. `AppLogCollector.clear` removes both memory and the cache file. Tests verify the collector's memory/disk behavior and verify the logout coordinator invokes `appLog.clear()` even when remote logout fails. Session activation, server switching, tombstoned startup reconciliation, and terminal authentication failure also clear diagnostics. |
| Sanitizer unit tests cover amount/description query keys | PASS | `LogSanitizerTest.redacts description and amount query values` exercises both keys and asserts their values are absent and replaced with `[REDACTED]`. The collector, interceptor, formatter, URL-builder, and repository tests add end-to-end JVM coverage of the same values. |

## Settings report flow

The dialog discloses that a short redacted log excerpt will be attached and warns users not to enter personal data. Submission builds a bounded URL, opens GitHub with `ACTION_VIEW`, and tells the user to review the form before submitting. The previous automatic full-log clipboard copy is removed.

## Verification

- Focused TASK-S1 suite: `BUILD SUCCESSFUL`.
- Full debug JVM suite: `./gradlew testDebugUnitTest --no-daemon` — `BUILD SUCCESSFUL`.
- Lint: `./gradlew lintDebug --no-daemon` — `BUILD SUCCESSFUL`; no new errors beyond the existing baseline.
- Debug APK: `./gradlew assembleDebug --no-daemon --rerun-tasks` — `BUILD SUCCESSFUL`.
- A combined lint/assemble invocation encountered missing Gradle intermediate jars. Running build and lint separately succeeded, indicating a tooling/intermediate race rather than a source failure.

## Residual risks

- The task plan called for a redacted preview plus opt-in. The UI discloses automatic attachment and requires the user to tap Submit, then offers browser review, but it has no in-app log preview or separate “include diagnostics” control. This does not fail the four stated acceptance checks, but the stronger consent design remains incomplete.
- `IssueReportUrlBuilder` sanitizes captured logs, not user-authored title or description. A user who pastes a raw financial URL into the description can still transmit it. The dialog warns against personal data, but final-boundary sanitization or an in-app preview would provide stronger protection.
- Logout integration is split across a mock-based coordinator test and a real collector clear test; no single test uses a real collector through the full logout path. The production call chain is direct, so this is a low integration-coverage risk.
- Interceptor failure logging and throwable stack-trace logcat sanitization are not directly regression-tested. Both use the same sanitized route/message paths, but dedicated tests would harden future changes.
- UI behavior was reviewed statically because this environment cannot run an Android emulator.
