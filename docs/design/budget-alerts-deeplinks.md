# Design: Configurable budget alerts + notification deep links

**Status:** Approved for implementation (roadmap after PR #10)  
**Branch:** `cursor/budget-alerts-deeplinks-25b7`  
**Related:** ADR-010 (WorkManager sync), ADR-017 (deep links), `docs/BUDGETS.md`

## Problem

Background sync already fires a local notification when any current-month expense group is ≥ **80%** spent (`SyncWorker`). Gaps:

1. Threshold and enable/disable are hardcoded; Settings shows a disabled “coming soon” row.
2. Notification tap does nothing (no `PendingIntent`), despite README/ADR promising deep links.
3. Copy/channel strings are hardcoded English; `POST_NOTIFICATIONS` is never requested at runtime.
4. The same alert can re-fire every 12h sync while groups stay over threshold (noisy).

## Goals (this slice)

| Goal | Acceptance |
|------|------------|
| User can enable/disable budget alerts | Settings toggle; SyncWorker respects preference |
| User can choose alert threshold | Discrete percents: **50 / 70 / 80 / 90 / 100**; default **80** |
| Notification tap opens Budgets for the alerted month | `PendingIntent` → `pledger://budgets?year=&month=` (existing parser/nav) |
| Localized notification + channel strings | en / nl / de |
| Request notification permission when enabling | API 33+; graceful if denied (toggle can stay on; OS may still block) |
| Reduce duplicate noise | Dedup within a calendar month for the same over-threshold set |

## Non-goals

- Per–expense-group alert toggles
- FCM / server push
- Compose `navDeepLink` (keep manual `DeepLinkParser`)
- Deep link to `budget/{id}` detail (month overview is enough; detail URI deferred)
- Changing sync interval or alert math beyond the configurable threshold

## Design

### Preferences (`UserPreferences` DataStore)

| Key | Type | Default | Notes |
|-----|------|---------|--------|
| `budget_alerts_enabled` | Boolean | `true` | Preserves today’s behavior for existing installs |
| `budget_alert_threshold_percent` | Int | `80` | One of `{50,70,80,90,100}` |
| `budget_alert_last_fingerprint` | String | absent | Dedup token (see below) |

Keep alert prefs across logout (like theme/locale). Do **not** clear them in `clearSessionData()`.

Expose:

- `StateFlow` / suspend getters for enabled + threshold
- `setBudgetAlertsEnabled`, `setBudgetAlertThresholdPercent`
- `suspend fun isNewBudgetAlertFingerprint(fingerprint: String): Boolean`  
  — returns `true` if this fingerprint differs from the last **successfully published** alert.
- `suspend fun markBudgetAlertFingerprint(fingerprint: String)` — persist only after notify succeeds under the session guard.
- `suspend fun clearBudgetAlertFingerprint()` — when the over-set is empty so a later breach can alert again.

**Fingerprint format:**  
`"{yearMonth}|{threshold}|{sortedBudgetIds joined by comma}"`  
e.g. `2026-07|80|12,45`. Empty over-set → do not notify; optionally clear fingerprint so a later breach alerts again.

### SyncWorker

1. After `SyncRunOutcome.Completed`, read prefs (inject `UserPreferences`).
2. If `!enabled` → return (no notify).
3. `thresholdFraction = percent / 100f`; filter `budgets.filter { it.percentUsed >= thresholdFraction }`.
4. Build fingerprint from **current calendar month** (same month as `SyncWorkRunner` budget fetch) + threshold + over-budget ids.
5. If fingerprint is not new → skip.
6. Else `sessionGuard.publishIfCurrent { send… }`; **only if published**, mark fingerprint.

### Notification content + deep link

- Channel id stays `budget_alerts`; name/description from string resources.
- Title/body from string resources with placeholders (`quantity`, `threshold`, `names`).
- `setContentIntent`: `PendingIntent.getActivity` launching `MainActivity` with  
  `Intent(ACTION_VIEW, Uri.parse("pledger://budgets?year=$y&month=$m"))`,  
  flags `FLAG_ACTIVITY_SINGLE_TOP` / `CLEAR_TOP` as appropriate, `FLAG_IMMUTABLE`.
- Keep single notification id `1001` (replace-in-place).
- Pass `YearMonth.now()` matching the synced month (if runner ever supports another month, thread it through `Completed`).

Extract building/sending into a small testable helper if it keeps `SyncWorker` thin (e.g. `BudgetAlertNotifier`), still called under the generation guard.

### Settings UI

Replace the disabled notifications row in Preferences with:

1. **Toggle** — “Budget alerts” / subtitle explaining overspend notifications after background sync.
2. **Threshold row** (enabled only when alerts on) — opens existing-style picker (mirror theme/currency pickers) listing 50/70/80/90/100%.

On toggle **on** (API 33+): request `POST_NOTIFICATIONS` via Activity Result / Accompanist-style callback already used elsewhere if present; otherwise `ActivityCompat.requestPermissions` from the Settings composable/`rememberLauncherForActivityResult`. If permanently denied, show snackbar pointing to system settings; still persist enabled=true so sync will notify once OS allows.

### Strings

Add en/nl/de for:

- Settings titles/subtitles, threshold labels
- Notification channel name/description, title, body (plural-aware if practical)
- Permission denied snackbar

### Docs

- Update ADR-010 notification strategy (configurable threshold, deep link, dedup).
- Update ADR-017 / README if they claim unfinished deep-link-from-notification behavior.
- Short note in `docs/BUDGETS.md` under background sync.

### Tests

| Area | Cases |
|------|--------|
| Prefs | default 80/enabled; set threshold clamps/validates; fingerprint consume once |
| Alert filter helper / worker logic | disabled → no notify; below threshold → no; at/above → notify; same fingerprint → suppress; changed ids → notify |
| Deep link | existing budgets URI still parses; notification intent URI matches parser |
| Settings VM | toggle + threshold update prefs |
| SyncWorker classifyFailure | unchanged |

Prefer pure functions for “which budgets alert” + fingerprint so unit tests don’t need full WorkManager.

## Implementation order

1. Prefs + fingerprint API  
2. Notifier + SyncWorker wiring + strings  
3. Settings UI + permission request  
4. Docs  
5. Unit tests  
6. `testDebugUnitTest`, `lintDebug`, `assembleDebug`

## Risks

- **Permission denied:** alerts “on” but silent — mitigate with snackbar + subtitle when OS blocks (optional `NotificationManager.areNotificationsEnabled()` check in Settings subtitle).
- **Dedup too aggressive:** user never re-alerted if still over — acceptable for 12h sync; fingerprint changes when another group crosses or threshold changes.
- **Default enabled=true:** may surprise users who never saw Settings; matches current production behavior.
