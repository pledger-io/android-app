# ADR-022: Transaction Schedules (List / Create / Delete)

**Date:** 2026-07-28  
**Status:** Accepted

## Context

Sessions and MFA are shipped. The next product slice is **scheduled transactions** — manual
automation definitions that the web already manages via `/v2/api/schedules`. Recurring
detection ([pledger-io/.github#13](https://github.com/pledger-io/.github/issues/13)) needs this
CRUD foundation first. OIDC remains blocked (client-secret +
[pledger-io/.github#24](https://github.com/pledger-io/.github/issues/24)).

Web supports list, create, delete, and PATCH edit. Shipping edit on Android in the same PR
widens UX and validation surface without unblocking #13.

## Decision

1. Add `ScheduleRepository` over `GET/POST /v2/api/schedules` and `DELETE /v2/api/schedules/{id}`
   (optionally `GET …/{id}`); **defer PATCH** edit.
2. Domain model `TransactionSchedule` with `Periodicity` (`WEEKS` / `MONTHS` / `YEARS`) and
   `transferBetween` source/destination refs.
3. Settings → Data → **Schedules** screen: list, create sheet, delete confirm; reuse owned-account
   picker patterns from the transaction form.
4. Do **not** implement OIDC, recurring detection (#13), or contract linking (`forContract`).
5. Network-first only for this PR (no Room/outbox for schedules).

Design details: [transaction-schedules.md](../design/transaction-schedules.md).

## Consequences

### Positive

- Mobile can manage scheduled transfers without waiting on detection or OIDC.
- Tight scope (list/create/delete) mirrors the successful sessions slice pattern.
- Shared account pickers keep create UX consistent with transfers.

### Negative / follow-ups

- Users must use web (or a later PR) to edit an existing schedule.
- No offline schedule cache; create/delete require network.
- Detection/insights (#13) and contract-linked schedules remain future work.
