# ADR-015: Stale-While-Revalidate Cache for Reference Data

**Date:** 2026-05-16
**Status:** Accepted

## Context

The app talks to a self-hosted Pledger.io server that is often on a home network — flaky, slow,
or offline entirely. Earlier behaviour was network-first for **every** read of categories,
contracts, expense groups, and counterparty accounts, so:

- Typeahead pickers in the transaction form hit the network on every keystroke.
- Cold-starting a screen always blocked on the API even when the data had not changed in hours.
- Mutations (e.g. saving an expense group, creating a debtor account) refreshed only one screen;
  other screens kept stale state.
- `SyncWorker` existed but was **never scheduled**, so background refresh did nothing.
- `CategoryRepository.getCategories()` cached results in Room but had no consumers.
- Contracts and the counterparty account list were never cached at all.

## Decision

Adopt a **stale-while-revalidate (SWR)** cache for "reference" entities that rarely change:
accounts (owned and counterparty), categories, contracts, expense groups, and currencies.

### Mechanism

1. A `sync_metadata` table (`SyncMetadataEntity` + `SyncMetadataDao`) stores
   `key → lastSyncedAt` per logical resource. Keys live in `SyncKeys`.
2. `CachePolicy` defines a TTL per key (15 min for accounts, 60 min for catalogs, 24 h for
   currencies). `isStale(lastSyncedAt, ttl)` is the single check.
3. A singleton `CacheRefresher` (with an injected `@ApplicationScope` `CoroutineScope` from
   `DispatcherModule`) coordinates refreshes:
   - `refreshNow(key, block)` — runs `block`, marks the key fresh on success, coalesces
     concurrent callers via a per-key `Mutex`.
   - `launchIfStale(key, ttl, block)` — kicks off a background refresh only if the key is
     stale (used on Flow subscription).
   - `refreshInBackground(key, block)` — fire-and-forget refresh after a mutation; deduplicates
     in-flight jobs via a `Job` map.
4. Repositories expose two read APIs per resource:
   - A **cache-backed** `Flow` (`observeXxx`) reading from Room with `distinctUntilChanged()`.
     `onStart` triggers a stale check so the cache refreshes itself when subscribed.
   - A **suspend** `searchXxx`/`getXxx` that returns Room results immediately and falls back
     to the network only when the cache is empty.
5. Mutations (`createAccount`, `saveExpenseGroup`, …) **write through** to Room and invoke
   `refreshInBackground` so other observers re-emit.
6. `SyncWorker` is now scheduled from `PledgerApp.onCreate` (12 h period, requires network) and
   refreshes currencies, categories, contracts, expense groups, owned accounts, and
   counterparty accounts on every run.

### Per-resource TTLs

| Key | TTL |
|---|---|
| `owned_accounts` | 15 min |
| `counterparty_accounts` | 15 min |
| `account_types` | 24 h |
| `categories` | 60 min |
| `expense_groups` | 60 min |
| `contracts` | 60 min |
| `currencies` | 24 h |

### Database changes

Room version bumped to **5**. New entities:

- `ContractEntity` (id, name, description; indexed by name)
- `ExpenseGroupEntity` (id, name, expected; indexed by name) — catalog of expense groups,
  distinct from `BudgetEntity` which stores the monthly snapshot.
- `AccountTypeEntity` (code as PK) — cached set of account type codes returned by the
  server. Display metadata is resolved client-side via `AccountTypeCatalog`, so only the
  codes are persisted.
- `SyncMetadataEntity` (key, lastSyncedAt)

`AccountDao` gained type-scoped queries (`searchByTypes`, `countByTypes`, `replaceByTypes`) so
counterparties can live in the same table as owned accounts without wiping each other on
refresh.

`fallbackToDestructiveMigration()` remains acceptable because the cache is always rehydrated
from the server.

## Consequences

### Positive

- Cold-start screens render instantly from Room and refresh in the background.
- Typeahead pickers (categories, expense groups, contracts) work offline and don't drain the
  battery with per-keystroke network calls.
- Mutations propagate to every subscriber via Room's Flow.
- `SyncWorker` now actually runs (was previously defined but never enqueued).
- One uniform pattern across repositories — easier to reason about and test.
- TTLs are tunable in one file (`CachePolicy.kt`).

### Negative

- More moving parts: an extra DAO, a `CacheRefresher` singleton, and a sync metadata table.
- Stale data may briefly appear if the API moved on but the TTL hasn't elapsed; the
  background refresh fixes it within seconds.
- Counterparty lists are now eagerly fetched in one go during the background sync — could be
  large on heavily-used installs (paged by default; full pull capped at 200 per request).

### Testing

`FakeSyncMetadataDao` in `app/src/test/java/com/pledgerio/app/util/` lets us drive TTL
behaviour deterministically. `CategoryRepositoryImplTest` and `ContractRepositoryImplTest`
exercise both the cache-hit and cache-miss paths.
