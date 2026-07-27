# ADR-010: WorkManager for Background Sync and Notifications

**Date:** 2026-05-13 (updated 2026-07-26)
**Status:** Accepted

## Context

The app needs to:
1. Periodically sync data from the server to keep the offline cache fresh
2. Notify users when budget thresholds are exceeded

Background work on Android is heavily restricted by Doze mode, app standby, and battery optimization. We need a reliable background execution mechanism.

Options considered:
- **WorkManager** — Guaranteed execution, survives process death, battery-friendly, Hilt support
- **AlarmManager** — Low-level, requires manual retry logic, no constraint support
- **JobScheduler** — API 21+, no backward compatibility layer, no Hilt integration
- **Foreground Service** — Always running, bad for battery, inappropriate for periodic sync
- **Firebase Cloud Messaging** — Server-push, but requires Firebase dependency and server-side changes

## Decision

Use **WorkManager** with `PeriodicWorkRequest` for a 12-hour background sync cycle.

Implementation:
- `SyncWorker` is annotated with `@HiltWorker` for dependency injection
- Reconciled from `PledgerApp.onCreate`: authenticated sessions enqueue one unique periodic
  worker; logged-out startup cancels any retained work
- Fresh login rotates an opaque encrypted work-generation ID and schedules immediately;
  logout, server changes, and terminal authentication failures invalidate that generation
  before cancellation and local cleanup
- Logout first persists an encrypted revocation tombstone. Tombstoned credentials are unusable
  for normal requests, worker guards, and startup scheduling even if durable token deletion
  fails; startup retries credential/cache cleanup and work cancellation
- Each worker receives only the opaque generation ID and re-checks it between sync steps and
  immediately before publishing a notification
- Every worker repository step and notification publication holds a shared session-data barrier.
  Session invalidation, cancellation dispatch, cache cleanup, and new-session activation hold the
  same barrier, so an old write cannot overlap cleanup or continue with another step. WorkManager
  cancellation is not treated as proof that a running worker has completed.
- Syncs the SWR-cached resources from [ADR-015](015-stale-while-revalidate-cache.md):
  currencies, categories, contracts, expense groups, owned accounts, counterparty accounts
- Loads the current-month budget and fires local notifications when any expense group meets
  or exceeds the user-configured alert threshold (default 80%; see Notification Strategy)
- Transaction cache is **not** refreshed here; lists load from the API when screens are opened
- Uses `ExistingPeriodicWorkPolicy.UPDATE` so startup reconciliation replaces stale generation input
  without creating a second periodic worker
- Requires `NetworkType.CONNECTED` so it doesn't fire when the device is offline
- `Configuration.Provider` on the Application class enables Hilt worker injection

## Consequences

### Positive
- Guaranteed execution even after device reboot (with `RECEIVE_BOOT_COMPLETED`)
- Respects battery optimization — won't drain battery on budget devices
- Hilt integration via `@HiltWorker` provides clean dependency injection
- Automatic retry with exponential backoff on failure
- Constraints (network required) can be added when needed

### Negative
- Minimum periodic interval is 15 minutes; 12 hours is well within limits
- Execution timing is inexact — WorkManager batches work for battery efficiency
- Testing WorkManager requires `work-testing` artifact and special setup
- Notifications require `POST_NOTIFICATIONS` permission on Android 13+

### Notification Strategy
- **Budget alert**: Configurable in Settings (enabled by default). Fires when any expense
  group’s spending meets or exceeds the user’s threshold (**50 / 70 / 80 / 90 / 100%**,
  default **80**) after a successful sync
- Preference keys live in `UserPreferences` and survive logout (like theme/locale)
- Dedup via a month+threshold+over-budget-ids fingerprint so the same over-set does not
  re-notify every 12h sync; a new group crossing the threshold (or a threshold change)
  produces a new fingerprint and alerts again
- Notification channel id `budget_alerts`; localized channel name/description and alert copy
  (en / nl / de)
- Auto-cancel on tap; `PendingIntent` opens `pledger://budgets?year=&month=` for the synced
  month via `MainActivity` (see [ADR-017](017-deep-links-and-reports.md))
- Enabling alerts on API 33+ requests `POST_NOTIFICATIONS`; if denied, the preference can stay
  on and the OS may still suppress delivery until the user grants permission in system settings
