# Localization (i18n)

The Pledger Android app supports **English** (default), **Dutch**, and **German**, with a path to add more languages without code changes to business logic.

## User-facing behavior

- **Settings → Language** offers:
  - **System default** — follows the device locale when a matching translation exists; otherwise English.
  - **English**, **Dutch**, **German** — fixed app language via Android per-app locales.
- Changing language recreates the activity so all `stringResource()` calls reload.
- Preference is stored in DataStore (`app_locale`) and applied on cold start in `PledgerApp`.

## Technical approach

| Piece | Location | Role |
|-------|----------|------|
| Default strings | `res/values/strings.xml` | English (source of truth) |
| Dutch | `res/values-nl/strings.xml` | Full translation set |
| German | `res/values-de/strings.xml` | Full translation set |
| Locale model | `domain/model/AppLocale.kt` | `system`, `en`, `nl`, `de` |
| Persistence | `UserPreferences.setAppLocale()` | DataStore |
| Runtime apply | `LocaleManager` + `AppCompatDelegate.setApplicationLocales()` | Per-app locale API |
| Activity theme | `Theme.AppCompat.DayNight.NoActionBar` | **Required** — platform `Theme.Material` ignores app locales |
| Activity class | `AppCompatActivity` | Works with `BiometricPrompt` and locale APIs |
| Enum labels | `ui/util/LocalizedLabels.kt` | `@Composable` helpers for theme, experience, reports |
| Play / system discovery | `res/xml/locales_config.xml` | Declared locales on Android 13+ |

## Adding a new language

1. Copy `values/strings.xml` to `values-<tag>/strings.xml` (e.g. `values-fr` for French).
2. Translate every `name` (keep `%1$s` / `%1$d` placeholders).
3. Add the locale to `AppLocale` (`storageValue`, `languageTag`, `selectable` if shown in Settings).
4. Add a label string in **all** `strings.xml` files: `language_french` (or similar).
5. Extend `AppLocale.localizedName()` in `LocalizedLabels.kt`.
6. Register `<locale android:name="fr" />` in `res/xml/locales_config.xml`.
7. Run `./gradlew :app:lintDebug` and fix any `MissingTranslation` if lint is enabled for new locales.

## Authoring rules

- **No user-visible literals in Composables** — use `stringResource(R.string.*)` or `LocalizedLabels`.
- **ViewModels** must not hold translated strings; pass string res IDs or format in the UI layer.
- **Server / API data** (account names, categories) stays as returned by the backend.
- **Dates and amounts** use existing formatters; locale-aware number formatting can be improved later via `Locale.getDefault()` after app locale is applied.

## Verification

- Switch language in Settings and confirm main tabs, empty states, errors, and dialogs update.
- Set device language to Dutch with app on **System default** and confirm Dutch strings appear.
- Cold start after kill: selected language persists.

## Related

- [ADR-018: App localization](adr/018-app-localization.md)
