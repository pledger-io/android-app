# ADR-010: WorkManager for Background Sync and Notifications

**Date:** 2026-05-13 (updated 2026-05-16)
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
- Each worker receives only the opaque generation ID and re-checks it between sync steps and
  immediately before publishing a notification
- Syncs the SWR-cached resources from [ADR-015](015-stale-while-revalidate-cache.md):
  currencies, categories, contracts, expense groups, owned accounts, counterparty accounts
- Loads the current-month budget and fires local notifications when any budget exceeds 80%
  (skipped when no budget exists yet for the current month)
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
- **Budget alert**: Fires when any budget group's spending exceeds 80% of allocation
- Notification channel: "Budget Alerts" with default importance
- Auto-cancel on tap
- Future: Deep link to the specific budget detail screen on tap
