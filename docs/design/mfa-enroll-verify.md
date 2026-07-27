# Design: MFA enroll / disable + login verify

**Status:** Approved for implementation  
**Roadmap:** [pledger-io/.github#34](https://github.com/pledger-io/.github/issues/34) — OIDC, 2FA, and session management  
**Branch:** `cursor/mfa-enroll-verify-25b7` (bases on API token sessions)  
**Depends on:** [api-token-sessions.md](api-token-sessions.md) / PR #15 (`UserSessionRepository`, profile `mfa`, Settings security row)

## Context

When two-factor is enabled, `POST /v2/api/security/authenticate` still returns **200 + JWT**, but roles
are only `PRE_VERIFICATION_USER`. Full API access requires
`POST /v2/api/user-account/verify-2-factor` with a TOTP code, which returns a new JWT with normal roles.

Today Android treats any login JWT as fully authenticated and calls `activateSession` (cache wipe +
sync). That breaks MFA users (sync/API calls get **403**). Web stores the pre-verify JWT, hits
profile → 403, then routes to `/two-factor`.

Profile management (web `profile-2factor.view.tsx`):

| API | Behavior |
|-----|----------|
| `GET …/user-account/{user}/2-factor` | PNG QR bytes (enroll) |
| `PATCH …/2-factor` `{ action: "ENABLE", verificationCode }` | **204** |
| `PATCH …/2-factor` `{ action: "DISABLE" }` | **204** (no TOTP required) |

## Goals (this PR)

1. Detect MFA-required login via JWT `roles` containing `PRE_VERIFICATION_USER`.
2. Hold the pre-verify JWT **in memory only** (no `activateSession` / no sync) until verify succeeds or the user cancels.
3. Login → OTP screen → `verify-2-factor` → then `activateSession` with the upgraded tokens.
4. Settings → MFA screen: enroll (QR + enable) or disable (confirm).
5. Typed Retrofit DTOs; unit tests; docs. OIDC remains deferred.

## Non-goals

- OIDC / AppAuth / consuming `.well-known/openid-connect` (especially `client-secret`)
- Rotating TOTP secret UX beyond what GET QR already exposes
- Requiring TOTP to disable (API does not; we only add a confirm dialog)
- Treating durable API tokens as MFA factors

## Design

### Detect pre-verification JWT

Decode the JWT payload (Base64URL, no signature verify — we already trust the HTTPS response) and
read `roles` (array or single string). MFA required when any role equals `PRE_VERIFICATION_USER`
(also accept `ROLE_PRE_VERIFICATION_USER`).

If the payload cannot be parsed, treat as fully authenticated (fail open only for role detection;
token is still from authenticate). Prefer false negative only when roles are missing — rare.

### Pending MFA store

`PendingMfaSession` (`@Singleton`, process memory):

- `set(accessToken, refreshToken?, expiresIn, username)`
- `get()` / `clear()`
- Never write to `EncryptedSharedPreferences` / `SessionManager`

Cancel paths: back from OTP screen, logout-equivalent “Change server”, successful verify, failed
login overwrite.

### Auth API

```kotlin
@POST("v2/api/user-account/verify-2-factor")
suspend fun verify2Factor(
  @Header("Authorization") authorization: String,
  @Body request: Verify2FactorRequest,
): Response<LoginResponse>

@GET("v2/api/user-account/{user}/2-factor")
suspend fun get2FactorQr(@Path("user") user: String): Response<ResponseBody>

@PATCH("v2/api/user-account/{user}/2-factor")
suspend fun patch2Factor(
  @Path("user") user: String,
  @Body request: Patch2FactorRequest,
): Response<Unit>
```

```kotlin
data class Verify2FactorRequest(
  @Json(name = "verificationCode") val verificationCode: String,
)

data class Patch2FactorRequest(
  @Json(name = "action") val action: String,
  @Json(name = "verificationCode") val verificationCode: String? = null,
)
```

`AuthInterceptor`: when no installed session, **keep** an explicit `Authorization` header on
`verify-2-factor` (same pattern as logout). Skip proactive/reactive refresh for that path. Do not
treat verify **403** (invalid TOTP) as session termination.

### Domain / repository

```kotlin
sealed class LoginResult {
  data object FullyAuthenticated : LoginResult()
  data object MfaRequired : LoginResult()
}
```

- `AuthRepository.login` → `Resource<LoginResult>`  
  - MFA roles → pending store, **no** `activateSession`  
  - else → `activateSession` as today
- `AuthRepository.verifyTwoFactor(code)` → uses pending Bearer → on 200 `activateSession` + clear pending  
  - Invalid code: HTTP **403** → user-facing “Invalid verification code”
- `UserSessionRepository`: `get2FactorQr(): Resource<ByteArray>`, `enableMfa(code)`, `disableMfa()`

### UI

1. **Login:** on `MfaRequired` navigate to `Screen.Verify2Factor` (`login/verify-2fa`).
2. **Verify2FactorScreen:** explanation, 6-digit OTP field, Verify, Back (clears pending).
3. **Settings:** MFA row navigable when status known → `Screen.MfaSetup` (`settings/mfa`).
4. **MfaSetupScreen:**
   - Disabled: load QR → `ImageBitmap`, explain, OTP, Enable
   - Enabled: explain disable + confirm → Disable
   - Refresh profile / pop with result so Settings subtitle updates

### Strings

en / nl / de for login verify + MFA setup copy. Do not log OTP or QR bytes.

### Docs

- This design + ADR-021
- Update ARCHITECTURE auth/security rows
- Note OIDC still follow-up

## Tests

- JWT role helper: MFA vs full roles; missing payload
- AuthRepository: MFA login does not activate; verify activates and clears pending; 403 mapping
- UserSessionRepository: enable/disable/QR success + errors
- LoginViewModel / MfaSetupViewModel happy paths with mocks

## Risks

| Risk | Mitigation |
|------|------------|
| False “logged in” on pre-verify JWT | Never `activateSession` until verify succeeds |
| QR is secret material | Load once into memory bitmap; no disk cache |
| Disable without TOTP | Confirm dialog; document API limitation |
| Disable does not revoke other JWTs/API tokens | Copy in disable explain string |
| Process death mid-MFA | Pending cleared; user signs in again |
| Interceptor strips verify Authorization | Explicit path exception |

## Implementation order

1. Design + ADR docs  
2. DTOs, API, JWT helper, PendingMfaSession, interceptor  
3. Auth + UserSession repository methods + tests  
4. Login verify UI + nav  
5. MFA setup UI + Settings wiring  
6. Strings, ARCHITECTURE, unit/lint/assemble  
