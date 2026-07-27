# ADR-020: Durable API Token Sessions

**Date:** 2026-07-27  
**Status:** Accepted

## Context

The auth epic (OIDC, 2FA, session management) is multi-PR. The backend already exposes durable
user sessions (long-lived API tokens) used by the web profile UI. Android had unused
`listSessions` / `createSession` stubs and no revoke client.

Interactive auth remains JWT + refresh in `SessionManager`. Mixing those JWT “this device”
credentials with durable API tokens would confuse users and revoke the wrong credential type.

## Decision

1. Ship Settings → **API tokens** (`Screen.ApiSessions`) for list / create / revoke of durable
   sessions via `UserSessionRepository` and `DELETE …/sessions/{session}`.
2. Treat JWT login as separate from durable API tokens; do not list the device JWT as a
   revocable session row.
3. Always **mask** tokens in the list UI; show the full secret only once after create (copy).
4. Surface profile **`mfa`** as read-only on Settings; defer OIDC login and MFA enroll/verify
   (see [ADR-021](021-mfa-enroll-verify.md)).

Design details: [api-token-sessions.md](../design/api-token-sessions.md).

## Consequences

### Positive

- Mobile can manage integration tokens without waiting on the full OIDC/MFA epic.
- Clear separation reduces accidental revoke of the interactive session.

### Negative / follow-ups

- OIDC AppAuth onboarding remains unimplemented; MFA enroll/challenge is ADR-021.
- Server may still return full tokens on list — UI masking is required forever.
