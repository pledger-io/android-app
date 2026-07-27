# Design: Transaction schedules (list + create + delete)

**Status:** Approved for implementation  
**Roadmap:** Foundation for recurring detection ([pledger-io/.github#13](https://github.com/pledger-io/.github/issues/13))  
**Branch:** `cursor/transaction-schedules-25b7`  
**Depends on:** Sessions + MFA (shipped); account list / `OwnedAccountPickerSheet` patterns from the transaction form

## Context

After durable API tokens and MFA, the next mobile slice is **scheduled transfers** — the same automation surface the web UI exposes under schedule overview (list, create dialog, delete confirm). Backend contracts already exist; Android has no client stubs yet.

This is the **manual** schedule CRUD foundation. Automatic recurring detection / suggestions (#13) builds on top later. OIDC remains blocked (client-secret handling + [pledger-io/.github#24](https://github.com/pledger-io/.github/issues/24)).

Web also supports **edit** via `PATCH /v2/api/schedules/{id}`; Android **defers PATCH** this PR to keep scope to list / create / delete.

## Goals (this PR)

1. Settings → **Schedules** screen: list schedules from the server.
2. **Create** schedule (name, amount, periodicity + interval, source + destination accounts).
3. **Delete** schedule (confirm dialog) via `DELETE`.
4. Typed DTOs + `ScheduleRepository`; unit tests; docs; en/nl/de strings.

## Non-goals

- OIDC / AppAuth / `.well-known` onboarding
- Recurring **detection** / insights / suggest-from-history (#13)
- **PATCH edit** of existing schedules (web-only for now)
- Linking schedules to **contracts** (`forContract`)
- Editing `activeBetween` / description beyond what create needs (optional description only if trivial; prefer name + amount + schedule + transfer)
- Offline Room cache / outbox for schedules (network-first like API sessions)
- Generating actual transactions from schedules on-device (server-side automation)

## Design

### API

Add to `PledgerApiService`:

```kotlin
@GET("v2/api/schedules")
suspend fun listSchedules(): Response<List<ScheduleDto>>

@POST("v2/api/schedules")
suspend fun createSchedule(@Body request: CreateScheduleRequest): Response<ScheduleDto>

@GET("v2/api/schedules/{id}")
suspend fun getSchedule(@Path("id") id: Long): Response<ScheduleDto>

@DELETE("v2/api/schedules/{id}")
suspend fun deleteSchedule(@Path("id") id: Long): Response<Unit>

// PATCH deferred — do not expose in repository for this PR
```

Create body (authoritative):

```json
{
  "name": "...",
  "amount": 12.34,
  "schedule": { "periodicity": "WEEKS|MONTHS|YEARS", "interval": 1 },
  "transferBetween": {
    "source": { "id": 1, "name": "..." },
    "destination": { "id": 2, "name": "..." }
  }
}
```

Response includes `id`, `name`, optional `description`, `amount`, `schedule`, `transferBetween`, optional `activeBetween`, optional `forContract`. Parse extras for forward compatibility; **do not** surface contract or active-range editing in UI this PR.

### Domain

```kotlin
enum class Periodicity {
  WEEKS, MONTHS, YEARS;

  companion object {
    fun fromApi(value: String): Periodicity = /* WEEKS|MONTHS|YEARS; fail clearly on unknown */
  }
}

data class ScheduleRule(
  val periodicity: Periodicity,
  val interval: Int, // ≥ 1
)

data class ScheduleAccountRef(
  val id: Long,
  val name: String,
)

data class TransferBetween(
  val source: ScheduleAccountRef,
  val destination: ScheduleAccountRef,
)

data class TransactionSchedule(
  val id: Long,
  val name: String,
  val description: String?,
  val amount: Double,
  val schedule: ScheduleRule,
  val transferBetween: TransferBetween,
)
```

Map DTOs → domain in the repository (or a small mapper). Unknown periodicity → `Resource.Error` for that item or fail the list map with a clear message (prefer skip-log + drop only if product wants resilience; default: fail list parse so tests catch contract drift).

### Repository

`ScheduleRepository` / `ScheduleRepositoryImpl`:

- `listSchedules(): Resource<List<TransactionSchedule>>`
- `createSchedule(name, amount, rule, source, destination): Resource<TransactionSchedule>`
- `deleteSchedule(id): Resource<Unit>`

Optional: `getSchedule(id)` for post-create refresh — not required if POST returns the full entity.

No Room; no WorkManager. Failures use existing `Resource` + HTTP message mapping.

Create validation (ViewModel and/or repository):

- Non-blank name
- Amount ≠ 0 (match product/web: typically positive; if web allows signed amounts, allow any non-zero)
- `interval >= 1`
- Source and destination present and **distinct** ids

### UI

1. **Entry:** Settings → **Data** section (alongside Categories / Tags): row “Schedules” → `Screen.Schedules` / `settings/schedules`.  
   (Budgets is an alternative home; prefer Settings Data so automation catalogs stay together and Budgets stays month-scoped.)
2. **`SchedulesScreen`** (patterned on `ApiSessionsScreen`):
   - Pull-to-refresh list: name, formatted amount, human schedule (“Every 2 weeks”), source → destination
   - Empty state + error snackbar
   - FAB / top action: Create
   - Per row: delete affordance → confirm dialog → DELETE → refresh list
3. **Create sheet / dialog:**
   - Name, amount (reuse currency formatting helpers)
   - Periodicity picker (`WEEKS` / `MONTHS` / `YEARS`) + interval stepper/field
   - Source + destination: **reuse** `OwnedAccountPickerSheet` (or the same owned-account load path as the transaction form). Schedules are transfers between owned accounts — do not invent a new account search unless counterparty transfers are confirmed later.
4. Nav: register in `Screen` + `NavGraph`; wire Settings callback like API tokens / MFA.

### Strings

en / nl / de for Settings row, screen title, empty/error, create fields, periodicity labels (“Every {n} week(s)/month(s)/year(s)”), delete confirm, validation errors.

### Docs

- This design + ADR-022
- ARCHITECTURE API surface: Schedules row
- README: remove shipped “Edit transaction” from Planned; note schedules as next/planned

### Tests

- Repository list/create/delete success + 401/404 mapping (MockWebServer or MockK)
- DTO → domain periodicity mapping (incl. unknown value)
- ViewModel: create validation (blank name, interval < 1, same source/dest); delete refreshes list
- Optional: UI string resources present for en (spot-check via existing string test patterns if any)

## Implementation order

1. DTOs + API + domain models + `ScheduleRepository` + unit tests  
2. `SchedulesScreen` list + delete confirm  
3. Create sheet + owned-account pickers  
4. Settings row + nav  
5. Strings (en/nl/de) + ARCHITECTURE/README polish if needed  
6. `testDebugUnitTest`, `lintDebug`, `assembleDebug`

## Risks

| Risk | Mitigation |
|------|------------|
| PATCH deferred while web can edit | Document in UI/README; follow-up PR for edit |
| Amount sign / currency code missing on create | Follow web create body; format with display currency; confirm with OpenAPI if amount is always major units |
| Source/dest must be owned accounts | Reuse owned picker only; reject identical ids |
| Large schedule lists | Simple LazyColumn first; paginate only if API requires |
| #13 detection expectations | Explicit non-goal; this PR is manual CRUD foundation only |
| OIDC distraction | Out of scope; blocked by client-secret + .github#24 |
