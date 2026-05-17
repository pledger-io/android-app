# ADR-018: App Localization (en / nl / de)

**Date:** 2026-05-17  
**Status:** Accepted

## Context

Users requested Dutch and German in addition to English. The app had many strings in `values/strings.xml` but most Composables still used hardcoded English. Settings listed language as a future item.

Options considered:

- **Third-party i18n library** — Unnecessary; Android resource qualifiers are standard and work with Compose.
- **Remote translations (Crowdin/Lokalise)** — The backend monorepo uses Crowdin; the mobile app can adopt the same pipeline later without changing the in-app model.
- **Single `strings.xml` + runtime JSON** — Harder to maintain than resource qualifiers and loses lint/plural support.

## Decision

1. **Resource-based i18n** with `values/` (English), `values-nl/`, `values-de/`.
2. **`AppLocale`** enum persisted in DataStore; **System default** uses `LocaleListCompat.getEmptyLocaleList()`.
3. **`AppCompatDelegate.setApplicationLocales()`** via `LocaleManager`; applied on startup (`PledgerApp`) and when the user changes language (activity `recreate()`).
4. **`android:localeConfig`** for Android 13+ per-app language visibility.
5. **Composable helpers** (`LocalizedLabels.kt`) for enums whose labels appear in pickers (theme, experience, report type, app locale).
6. **Ongoing migration**: new UI must use string resources; remaining hardcoded English in secondary screens should be moved in follow-up PRs.

## Consequences

### Positive

- Works offline; no network for translations.
- Aligns with Android Studio translation editor and future Crowdin export of `strings.xml`.
- Easy to add `values-fr/` etc. without architectural change.

### Negative

- Large `strings.xml` files must stay in sync across locales (lint `MissingTranslation` recommended).
- Activity recreate on language change is a brief flash (acceptable for settings-driven change).
- ViewModel validation messages may remain English until migrated.

### Follow-up

- Wire Crowdin or CI check for missing keys across `values-*`.
- Locale-aware `NumberFormat` / `DateTimeFormatter` for currency and dates in UI.
- Complete string extraction for transaction form validation and account/category screens.
