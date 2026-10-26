# Categories

Categories are lightweight labels for transactions (for example **Groceries**, **Transport**, **Subscriptions**). They improve search, filtering, and report grouping.

The Android app now includes a dedicated **Manage categories** flow so users can maintain this catalog directly on device.

Backend contract: [pledger-io/rest-application](https://github.com/pledger-io/rest-application) (`src/contract/paths/categories*.yaml`).

## UX design goals

The category manager is designed around fast, low-friction maintenance:

1. **Easy discovery** — entry point in **Settings → Manage categories**.
2. **Fast retrieval** — instant search field with cache-backed filtering.
3. **Clear actions** — each row exposes edit and delete affordances.
4. **Safe destructive action** — explicit delete confirmation dialog.
5. **Visual hierarchy** — summary card, iconography, and concise copy keep the page scannable.

## API (v2)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v2/api/categories` | List/search categories |
| `GET` | `/v2/api/categories/{id}` | Get one category |
| `POST` | `/v2/api/categories` | Create category |
| `PUT` | `/v2/api/categories/{id}` | Update category |
| `DELETE` | `/v2/api/categories/{id}` | Delete category |

### Create / update payload

```json
{
  "name": "Groceries",
  "description": "Food and household purchases"
}
```

## App behaviour

### Manage Categories screen (`CategoriesScreen`)

- Top bar with count-based subtitle.
- Search bar with clear action.
- Summary card that explains category quality guidance.
- Category list cards with inline **Edit** and **Delete** actions.
- FAB (**New category**) to create entries quickly.
- Pull-to-refresh to force server sync.

### Create / Edit dialog

- Required `name` validation.
- Optional `description`.
- Inline error message for failed API requests.

### Delete flow

- Confirmation dialog before deletion.
- On success the item is removed from Room cache immediately.

### Empty states

- No categories yet → onboarding-style CTA to create first category.
- No search matches → guidance to adjust query or add a new one.

## Data flow and caching

- Source of truth is Room (`categories` table).
- UI observes `CategoryRepository.observeCategories()` or `observeMatching(query)`.
- Create/update/delete mutations write through to Room, then trigger a background refresh of the category catalog.
- `SyncWorker` also refreshes categories periodically.

## Code map

| Piece | Location |
|-------|----------|
| API | `PledgerApiService` (`createCategory`, `updateCategory`, `deleteCategory`) |
| DTOs | `CategoryDto.kt` (`CategoryDto`, `CategoryUpsertRequest`, `CategoryPagedResponse`) |
| DAO | `CategoryDao` |
| Repository | `CategoryRepositoryImpl` |
| UI | `ui/categories/*` |
| Settings entry | `ui/settings/SettingsScreen.kt` |
| Route | `categories` |
