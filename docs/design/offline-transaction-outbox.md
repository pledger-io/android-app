# Design: Offline transaction create outbox (MVP)

**Status:** Approved for implementation  
**Roadmap:** [pledger-io/.github#34](https://github.com/pledger-io/.github/issues/34) — Offline transaction drafts/outbox  
**Branch:** `cursor/offline-transaction-outbox-25b7`  
**Related:** ADR-005 (Room cache), ADR-010 (WorkManager + sync generation), USABILITY T3.3

## Problem

Transaction **creates** require network. Offline or flaky saves return `Resource.Error` and discard the user’s form work. Room only caches server rows after successful API writes. Scan “drafts” and form templates are unrelated.

## Goals (MVP)

| Goal | Acceptance |
|------|------------|
| Queue create while offline / IO failure | Form create enqueues durable outbox row and closes with success UX (“Saved offline — will sync”) |
| Background flush when online | Worker drains pending creates under current sync generation + network |
| Pending visibility | Transactions list (or banner) shows pending outbox items; user can discard a pending row |
| Session safety | Logout / server switch / `LocalDataCleaner` wipes outbox; stale generation does not flush |
| Invalidate derived data on flush | After successful create, invalidate reports overview for tx month (+ MoM next month) and budget month |

## Non-goals (later)

- Offline **edit** / **delete** / split PATCH
- Server-side idempotency protocol (no API key today) — mitigate with careful retry policy
- Offline invoice OCR / extract
- Offline **create account** mid-form
- Multi-device conflict resolution
- Optimistic balance updates

## Design

### Room schema v7 — `transaction_outbox`

| Column | Type | Notes |
|--------|------|--------|
| `localId` | TEXT PK | UUID |
| `createdAtMillis` | INTEGER | FIFO order |
| `status` | TEXT | `PENDING` \| `FAILED` |
| `lastError` | TEXT? | Last flush error |
| `attemptCount` | INTEGER | default 0 |
| `date` | TEXT | API date `yyyy-MM-dd` |
| `currency` | TEXT | |
| `description` | TEXT | |
| `amount` | REAL | |
| `sourceAccountId` | INTEGER | |
| `destinationAccountId` | INTEGER | |
| `categoryId` | INTEGER? | |
| `expenseId` | INTEGER? | |
| `contractId` | INTEGER? | |
| `tagsJson` | TEXT? | JSON array of strings |
| `displaySourceName` | TEXT? | UI only |
| `displayDestinationName` | TEXT? | UI only |
| `displayCategoryName` | TEXT? | UI only |
| `type` | TEXT? | optional DEBIT/CREDIT/TRANSFER for list rendering |

Migration `MIGRATION_6_7` creates the table. Export schema; bump `PledgerDatabase` to 7; register entity + DAO; include in `LocalDataCleaner` / `clearAllTables` path.

### Domain / repository

```kotlin
data class PendingTransactionCreate(
  val localId: String,
  val createdAtMillis: Long,
  val status: OutboxStatus,
  val lastError: String?,
  // fields needed for list + rebuild CreateTransactionRequest
)

interface TransactionOutboxRepository {
  fun observePending(): Flow<List<PendingTransactionCreate>>
  suspend fun enqueueCreate(transaction: Transaction): Resource<PendingTransactionCreate>
  suspend fun discard(localId: String): Resource<Unit>
  suspend fun flushPending(generation: String): FlushResult // or Unit + internal guards
}
```

**Create path** (`TransactionRepository.createTransaction` or form VM):

1. If `NetworkMonitor` reports online → existing API path.
2. If offline **or** API throws `IOException` → `enqueueCreate` → `Resource.Success` with a sentinel / flag so UI can show offline message.  
   Prefer: return a sealed success type or `Resource.Success` + check `transaction.id == 0 && outboxLocalId` — cleaner: add `CreateTransactionResult.Online(tx) | Queued(pending)` used by form VM only via use case.

Simplest MVP UX: repository method `createTransactionOrEnqueue` used by form:

```kotlin
suspend fun createTransactionOrEnqueue(tx: Transaction): Resource<CreateOutcome>
sealed class CreateOutcome {
  data class Synced(val transaction: Transaction) : CreateOutcome()
  data class Queued(val pending: PendingTransactionCreate) : CreateOutcome()
}
```

HTTP 4xx/5xx (non-IO) still surface as `Resource.Error` (do not enqueue bad payloads).

### Flush worker

- Extend `SyncWorkRunner` **after** catalog sync (accounts useful for integrity): call `outboxRepository.flushPending(generation)` inside generation + `SessionDataBarrier` steps.
- Also schedule one-shot expedited flush when: enqueue succeeds and device is online (optional), and when `NetworkMonitor` flips to online (Application / coordinator collector) via `WorkManager.enqueue` unique work `pledger_outbox_flush`.
- Per row: build `CreateTransactionRequest` → API create → on 2xx insert Room transaction, invalidate mutations for date, delete outbox row.
- On `IOException`: leave `PENDING`, stop drain (retry next cycle).
- On HTTP 4xx: mark `FAILED` with message (user can discard/edit later — MVP discard only).
- Before each row: `sessionGuard.isCurrent(generation)`; if false abort.

**Retry / idempotency:** Do not auto-retry `FAILED`. For `PENDING` after unknown timeout: risk of duplicate — document; prefer only enqueue on clear offline or caught `IOException` before response; if response parsing fails after 2xx, try to avoid re-POST (treat as needs manual check) — MVP: if `response.isSuccessful` path throws after success unlikely; if timeout with no response, leave PENDING and accept rare dup (document in ADR).

### UI

1. **Form** (`TransactionFormViewModel`): on `Queued` → `saveSuccess = true` + snackbar/string `transaction_saved_offline`.
2. **Transactions list**: observe pending; section “Waiting to sync” above list; row shows description/amount/date; overflow or swipe: Discard. Optional syncing indicator when flush runs (nice-to-have).
3. **Edit/delete** remain online-only; no change except clearer offline error if desired.

### Cleaner / DI

- `LocalDataCleaner.clearAllUserData()` deletes outbox (Room `clearAllTables` if used — verify includes new table).
- Hilt provide DAO from database.

### Strings

en/nl/de: saved offline, pending section title, discard, flush failed message.

### Docs

- New short `docs/design/offline-transaction-outbox.md` (this file).
- ADR-005 or small ADR-018 note: write outbox create-only.
- USABILITY T3.3 partial checkbox note.
- README offline bullet.

### Tests

| Area | Cases |
|------|--------|
| Enqueue | persists row; observe emits |
| createOrEnqueue | offline → Queued; online success → Synced; HTTP 400 → Error no enqueue |
| Flush | success removes row + inserts tx; IOException leaves pending; 400 → FAILED; stale generation no API |
| Cleaner | outbox empty after clear |
| Form VM | Queued → saveSuccess |
| List VM | shows pending; discard |

## Implementation order

1. Entity/DAO/migration/schema export  
2. Outbox repository + createOrEnqueue wiring  
3. Flush in SyncWorkRunner (+ optional one-shot)  
4. Form + Transactions UI  
5. Cleaner + strings + docs  
6. Unit tests  
7. `testDebugUnitTest`, `lintDebug`, `assembleDebug`

## Risks (accepted for MVP)

- Duplicate create on retry after ambiguous network failure  
- Pending rows reference account ids that user deleted before flush → mark FAILED  
- List cache `deleteAll` on online fetch must not delete outbox (separate table — OK)
