# ADR-016: Usability Improvement Program

**Date:** 2026-05-16  
**Status:** Accepted

## Context

The Android client has a coherent v1 architecture (Compose, MVVM, SWR cache, offline banner on main tabs) but several surfaces create poor user expectations or dead ends:

- **Reports** tab shows placeholder content while looking like a finished feature.
- **Settings** is only linked from the Dashboard; users on other tabs cannot discover configuration.
- **Offline banner** appears only above the bottom navigation bar, not on forms or detail screens where failed saves are most confusing.
- **Error recovery** is inconsistent: list screens offer Retry via `ErrorScreen`; detail screens often do not.
- **Theme mismatch:** `PledgerTopBar` uses `isSystemInDarkTheme()` while the app theme follows user `ThemeMode` from Settings ([ADR-009](009-dark-first-design.md)).
- **Guided/Power modes** ([USABILITY_MODES.md](../USABILITY_MODES.md)) are implemented for the transaction form but not yet extended to empty states, navigation shortcuts, or global feedback.

A single ad-hoc fix per screen would drift patterns further. We need a documented, tiered program so UX work stays consistent with existing shared components and ADRs.

## Decision

Adopt a **tiered usability improvement program** documented in [USABILITY_IMPROVEMENT_PLAN.md](../USABILITY_IMPROVEMENT_PLAN.md).

### Principles

1. **Reuse shared UI** — Extend `LoadingScreen`, `ErrorScreen`, `EmptyScreen`, `OfflineBanner`, and `PledgerTopBar` rather than one-off screen logic.
2. **Honest connectivity** — Users must see offline/stale state anywhere they can read or write financial data (authenticated stack included).
3. **Recoverable errors** — Any full-screen error state that blocks content must offer **Retry** when the ViewModel can reload.
4. **No false affordances** — Placeholder features (Reports charts, notification settings, biometric without prompt) must be labeled, hidden, or implemented; not silent no-ops.
5. **Mode-aware, not mode-limited** — Guided/Power changes defaults and prominence only ([USABILITY_MODES.md](../USABILITY_MODES.md)); capabilities stay the same.
6. **Incremental delivery** — Tier 1 (quick wins) ships first; Tier 2–3 are scheduled without blocking Tier 1.

### Tier 1 (immediate)

| Item | Approach |
|------|----------|
| Settings discoverability | Settings action on every main tab top bar; same `Screen.Settings` route |
| Detail errors | Pass `onRetry` to `ErrorScreen` on account/transaction detail |
| Offline visibility | Show `OfflineBanner` for authenticated routes outside onboarding (not only above bottom nav) |
| Transaction refresh | Keep existing list in state while `isRefreshing`; replace when new page arrives |
| Top bar theme | `PledgerTopBar(darkTheme: Boolean)` aligned with `PledgerTheme` |
| Version display | `BuildConfig.VERSION_NAME` in Settings |
| Reports | Coming-soon `EmptyScreen` or equivalent; remove fake chart placeholder |

### Tier 2–3 (planned)

Sync/last-updated indicators, mode-aware empty CTAs, server URL edit with cache clear, biometric gate, localization and accessibility passes, budget deep links, deep linking, real reports, offline write queue — see plan doc for full list.

### Out of scope for this ADR

- Backend API changes
- Replacing Compose or navigation library ([ADR-002](002-jetpack-compose-ui.md), [ADR-008](008-navigation-compose.md))
- Changing SWR TTLs ([ADR-015](015-stale-while-revalidate-cache.md)) unless driven by new sync UI

## Consequences

### Positive

- Predictable UX patterns across screens; easier code review (“does this match ADR-016?”).
- Tier 1 reduces the most visible trust issues with small, testable diffs.
- Plan doc gives contributors a single backlog without scattering TODOs.

### Negative

- `PledgerTopBar` gains a parameter; all call sites must pass theme or use a composition local.
- Showing offline banner on more screens may feel noisy; copy must stay short ([ADR-015](015-stale-while-revalidate-cache.md) already explains cached data).

### Follow-up

- Update [USABILITY_IMPROVEMENT_PLAN.md](../USABILITY_IMPROVEMENT_PLAN.md) checkboxes as tiers complete.
- When Reports ship real data, add ADR amendment or ADR-017 if chart library choice is significant.
