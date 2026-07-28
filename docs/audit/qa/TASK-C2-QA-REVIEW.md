# TASK-C2 QA Review — Room Migration 5→6 Tags

**Date:** 2026-07-28  
**Verdict:** PASS WITH NOTES

## Acceptance evidence

1. **PASS — `MIGRATION_5_6` creates the required schema-6 tables.**
   - The migration executes `CREATE TABLE IF NOT EXISTS` for `account_types`, `sync_metadata`, and `tags`.
   - Each definition matches exported schema 6:
     - `tags`: non-null `TEXT` `name`, primary key `name`.
     - `account_types`: non-null `TEXT` `code`, primary key `code`.
     - `sync_metadata`: non-null `TEXT` `key`, non-null `INTEGER` `lastSyncedAt`, primary key `key`.
   - No schema 5 JSON is exported in the repository, so an exhaustive automated 5-vs-6 schema diff is unavailable. The migration covers all three tables explicitly named by the acceptance criteria.

2. **PASS — Removing the `tags` CREATE is caught by tests.**
   - `PledgerDatabaseMigrationsTest` verifies the exact `tags` `execSQL` call, including its column and primary-key definition.
   - In an isolated checkout, removing only the production `tags` `execSQL` block made the focused test fail at the `tags` verification with one failed test and Gradle exit code 1.
   - Running the unchanged implementation in a clean isolated checkout produced 2 tests, 0 failures, and 0 errors.

3. **PASS — Unit tests and debug build succeed.**
   - `./gradlew testDebugUnitTest --no-daemon`: `BUILD SUCCESSFUL`.
   - `./gradlew assembleDebug --no-daemon` (run with the full suite in an isolated checkout): `BUILD SUCCESSFUL`; debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Verification

Focused regression command:

```text
./gradlew testDebugUnitTest --tests "com.pledgerio.app.data.local.PledgerDatabaseMigrationsTest" --rerun-tasks --no-daemon
```

Result: `BUILD SUCCESSFUL`; 2 tests executed, 0 failures, 0 errors.

Full verification commands:

```text
./gradlew testDebugUnitTest assembleDebug --no-daemon
./gradlew testDebugUnitTest --no-daemon
```

Result: both completed with `BUILD SUCCESSFUL`.

## Residual risks

- The repository exports schemas 6 and 7 but not schema 5. This prevents a definitive machine comparison proving that no other table, index, column, or foreign-key change belongs in 5→6.
- The regression test mocks `SupportSQLiteDatabase` and verifies SQL strings; it does not run the migration against a real version-5 SQLite database or invoke Room schema validation. A future `MigrationTestHelper` instrumented test should validate the complete 5→6 and 5→7 upgrade paths when an emulator/KVM runner is available.
- `CREATE TABLE IF NOT EXISTS` can hide an incorrectly shaped pre-existing table; Room's post-migration validation should reject that state at runtime, but the JVM test does not exercise it.

No production-code changes were required.
