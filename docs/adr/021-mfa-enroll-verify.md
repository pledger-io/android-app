# ADR-021: MFA Enroll and Login Verification

**Date:** 2026-07-27  
**Status:** Accepted

## Context

API token sessions (ADR-020) surfaced profile `mfa` as read-only. Users with two-factor enabled
receive a JWT whose only role is `PRE_VERIFICATION_USER` after password login. Activating that
token as a full session starts sync and API traffic that the backend rejects with 403.

Enroll/disable uses `GET/PATCH …/user-account/{user}/2-factor` (QR PNG + ENABLE/DISABLE). Login
challenge uses `POST …/user-account/verify-2-factor` with `{ verificationCode }` under the
pre-verification Bearer token.

OIDC AppAuth remains a separate concern (especially server-returned `client-secret`).

## Decision

1. Detect MFA-required login by decoding JWT `roles` for `PRE_VERIFICATION_USER`.
2. Hold pre-verification credentials in an in-memory `PendingMfaSession` only; call
   `activateSession` only after successful verify (or when MFA is not required).
3. Pass verify Authorization as an explicit header; teach `AuthInterceptor` not to strip it and
   not to treat invalid-TOTP **403** as terminal session failure.
4. Ship Settings → MFA setup for QR enroll / enable / confirmed disable via
   `UserSessionRepository`.
5. Defer OIDC login to a later PR.

Design details: [mfa-enroll-verify.md](../design/mfa-enroll-verify.md).

## Consequences

### Positive

- MFA users can complete login on mobile.
- Settings can enroll/disable without waiting on OIDC.
- Pre-verify tokens never wipe cache or schedule sync.

### Negative / follow-ups

- Disable does not require TOTP (backend contract); confirm dialog only.
- OIDC / AppAuth still unimplemented.
- Process death during OTP requires a fresh password login.
