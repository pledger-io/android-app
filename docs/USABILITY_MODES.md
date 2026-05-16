# Usability Modes (Guided + Power)

To support both novice and advanced users, the app exposes a persisted **Finance experience** setting:

- **Guided** — beginner-first defaults that reduce cognitive load
- **Power** — expert-first defaults that optimize speed

Users can switch mode at any time in **Settings → Finance experience**.

## Why this exists

Novice users and power users have different needs:

- New users need fewer visible options and clear progression.
- Frequent users need fewer clicks and immediate access to advanced controls.

This mode system ensures the same feature set is available while changing only default presentation.

## Current behavior

### Guided mode (default)

- Transaction form opens with optional sections collapsed.
- Transaction templates are hidden by default on new transactions.
- A hint below optional fields explains where categories/tags/contracts live.

### Power mode

- Transaction form opens with optional sections expanded.
- Transaction templates are shown immediately on new transactions.
- Advanced users can still collapse sections manually when needed.

## Product rules

1. **No feature loss across modes.** Modes change defaults, not capabilities.
2. **Manual override wins.** If a user expands/collapses optional sections, that action is respected in the current form session.
3. **Safe default for newcomers.** Guided mode is the initial default.
4. **Persisted preference.** The selected mode is stored in `UserPreferences` (`finance_experience_mode`) and applied automatically.

## Code map

| Layer | Location |
|-------|----------|
| Domain model | `domain/model/FinanceExperienceMode.kt` |
| Persistence | `util/UserPreferences.kt` |
| Settings state/actions | `ui/settings/SettingsViewModel.kt` |
| Settings UI picker | `ui/settings/SettingsScreen.kt` |
| Transaction form behavior | `ui/transactions/TransactionFormViewModel.kt`, `TransactionFormScreen.kt` |

## Next usability iterations

- Extend mode-aware defaults beyond transaction creation (dashboard onboarding cards, guided empty-state CTAs, shortcut-heavy power actions).
- Add telemetry (or local diagnostics) to validate reduced friction for first-time users without slowing expert flows.
