# ADR-019: Security and Quality Hardening Baseline

**Date:** 2026-05-24  
**Status:** Accepted

## Context

The Android app had several production-risk defaults that were acceptable during rapid feature
delivery but not suitable as a long-term baseline:

- HTTP body logging was enabled for all builds.
- Cleartext HTTP was globally allowed.
- Room was configured with destructive fallback.
- CI lacked lint gates and instrumented validation.
- Some UI flows depended directly on data-layer implementations.

## Decision

Adopt a hardening baseline across runtime, persistence, architecture boundaries, and CI:

1. **Network/security defaults**
   - Keep request/response body logging in debug only.
   - Disable cleartext by default and allow it only through debug resource overrides.
   - Disable app backup to reduce accidental restore of sensitive session-adjacent data.

2. **Database evolution**
   - Remove destructive fallback.
   - Require explicit Room migrations (`PledgerDatabaseMigrations`).
   - Export Room schema JSON for auditability and migration review.

3. **Architecture boundaries**
   - Move report overview cache usage behind a domain contract (`ReportsOverviewStore`).
   - Route invoice-scan orchestration through a domain use case (`ProcessInvoiceScanUseCase`)
     and domain reader contract (`InvoiceTextReader`) instead of direct UI dependency on data
     extraction classes.

4. **Quality gates**
   - Enforce `lintDebug` in CI with a baseline so new issues fail while historical debt is tracked.
   - Add a first `androidTest` smoke check and run instrumented tests in CI.

## Consequences

### Positive

- Reduced risk of leaking sensitive payloads in production logs.
- More secure default transport policy while keeping local/dev workflows.
- Safer app upgrades with explicit schema evolution.
- Better layer isolation for reporting and invoice-scan flows.
- CI now blocks on new lint regressions and validates a real device startup path.

### Negative

- CI becomes slower due to instrumented tests.
- Migration ownership is now required for every schema version bump.
- Some historical lint debt remains until baseline is gradually burned down.

### Follow-up

- Continue extracting remaining large ViewModel responsibilities.
- Add additional instrumented smoke tests for onboarding, login, and tab navigation.
- Gradually retire lint baseline entries instead of allowing debt to grow.
