# Design: API token sessions (first auth epic slice)

**Status:** Approved for implementation  
**Roadmap:** [pledger-io/.github#34](https://github.com/pledger-io/.github/issues/34) — OIDC, 2FA, and session management  
**Branch:** `cursor/api-token-sessions-25b7`

## Context

Full OIDC login + MFA enroll/challenge is a multi-PR epic. Backend contracts already define **durable sessions** (long-lived API tokens) used by the web profile UI:

| API | Contract |
|-----|----------|
| `GET /v2/api/user-account/{user}/sessions` | List |
| `POST /v2/api/user-account/{user}/sessions` | Create (`description` ≥ 8, `expires` date) |
| `DELETE /v2/api/user-account/{user}/sessions/{session}` | **Revoke** (missing from Android client today) |
| `GET /v2/api/user-account/{user}` | Profile includes `mfa: Boolean` |

Android already has unused `listSessions` / `createSession` Retrofit stubs. Web lists sessions but does not revoke in UI; mobile can ship list + create + revoke.

## Goals (this PR)

1. Settings → **API tokens / sessions** screen: list active durable sessions.
2. **Create** token (description + expiry date) → show token **once** with copy affordance.
3. **Revoke** token (confirm dialog) via DELETE.
4. Show profile **MFA enabled/disabled** status on Settings security (read-only; no enroll yet).
5. Typed DTOs + repository; unit tests; docs.

## Non-goals (later PRs)

- OIDC AppAuth login / `.well-known` consumption in onboarding
- Login-time `verify-2-factor` challenge after password
- MFA QR enroll / enable / disable UI (`…/2-factor` GET+PATCH)
- Treating JWT “this device” as a revocable session row (these are durable API tokens, not interactive JWTs)

## Design

### API

Add to `PledgerApiService`:

```kotlin
@DELETE("v2/api/user-account/{user}/sessions/{session}")
suspend fun revokeSession(
  @Path("user") user: String,
  @Path("session") sessionId: Long,
): Response<Unit>
```

Keep list/create. Optionally type `verify2Factor` later — out of scope.

`SessionResponse.valid` uses existing `DateRangeDto` (`startDate` / `endDate`) per OpenAPI.

### Domain

```kotlin
data class ApiSession(
  val id: Long,
  val description: String,
  val token: String?, // null after list (server may still return it — never show full token in list UI if long; mask)
  val validFrom: LocalDate?,
  val validUntil: LocalDate?,
)
```

On **list**: mask token in UI (`••••` + last 4) or hide. On **create** success: show full token once.

### Repository

`UserSessionRepository` (or extend `AuthRepository`):

- `listSessions(): Resource<List<ApiSession>>` — username from `SessionManager.getUsername()`
- `createSession(description, expires): Resource<ApiSession>`
- `revokeSession(id): Resource<Unit>`
- `getProfile(): Resource<UserProfile>` — at least `mfa` for Settings

Fail clearly if username missing.

### UI

1. Settings Security section:
   - Existing biometric toggle
   - Row: “Two-factor authentication” subtitle Enabled/Disabled from profile (load on Settings open)
   - Row: “API tokens” → navigate to sessions screen
2. `ApiSessionsScreen`:
   - Lazy list of sessions (description, validity range)
   - FAB or top action: Create
   - Create sheet: description (min 8), expiry date picker (default +1 year)
   - After create: dialog with token + Copy
   - Per row: Revoke with confirm
3. Nav: `Screen.ApiSessions` / `settings/sessions`

### Strings

en/nl/de for all new copy.

### Docs

- `docs/design/api-token-sessions.md` (this file)
- Short ADR or ARCHITECTURE note under security: durable sessions vs JWT
- Roadmap note: OIDC + MFA enroll remain follow-ups

### Tests

- Repository list/create/revoke success + 401/404 mapping (MockWebServer or MockK)
- ViewModel create validation (description length), revoke refresh list
- Settings shows MFA from profile mock

## Implementation order

1. DELETE API + domain models + repository  
2. Sessions screen + create/revoke UX  
3. Settings rows + profile MFA  
4. Strings + docs + tests  
5. `testDebugUnitTest`, lint, assembleDebug

## Risks

- Server may return full token on list — always mask in list UI.
- Created token is a secret — use clipboard + one-time dialog; do not log.
- Username path encoding for emails (`@`) — use path encoding Retrofit provides.
