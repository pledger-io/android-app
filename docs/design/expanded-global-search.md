# Design: Expanded global search

**Status:** Approved for implementation  
**Issue:** [pledger-io/.github#36](https://github.com/pledger-io/.github/issues/36)  
**Branch:** `cursor/expanded-global-search-25b7`

## Problem

Search MVP works but:

1. Owned accounts call `refreshOwnedAccounts()` on every query (network-first).
2. Category rows are not clickable.
3. `SearchScreen` treats `error != null` as exclusive — hides account/category hits even though the ViewModel already populates them on tx failure.
4. Missing `SearchViewModel` tests and README mention.

## Goals (from #36)

| Goal | Approach |
|------|----------|
| Cache-first owned accounts | Filter `accountRepository.observeOwnedAccounts()` (already SWR / `launchIfStale`); do **not** call `refreshOwnedAccounts()` in search |
| Category → transactions | `onNavigateToCategory(id, name)` → `Screen.Transactions.createRoute(categoryId, categoryName, year, month)` with current month (or last 6 months start — prefer **current** `YearMonth.now()` to match report drill-downs) |
| Partial success UX | Always show result sections when any list is non-empty; render tx `error` as a non-blocking banner/text above the list (not an exclusive branch) |
| Tests + docs | `SearchViewModelTest` happy + partial failure; README bullet |

## Non-goals

Unchanged from issue #36 (budgets/contracts search, infinite scroll, deep link, SearchRepository, outbox).

## Implementation notes

### ViewModel

```kotlin
val ownedAccounts = accountRepository.observeOwnedAccounts()
    .first()
    .filter { it.name.contains(query, ignoreCase = true) }
```

Keep counterparties + categories paths. On tx Error: set `error` **and** keep `transactions = emptyList()` (or prior?) — prefer empty txs + accounts/categories filled. Clear error on next successful search / blank query.

Optional: rename `error` → `transactionsError` in UI state for clarity (nice-to-have).

### Screen

Change `when` order:

1. blank query → prompt  
2. searching && all empty → spinner (or keep spinner only while searching with no prior results)  
3. else → LazyColumn with optional error banner + sections  

Spinner-only when `isSearching && no results yet` avoids flicker wiping partial content on re-query.

### NavGraph

Wire `onNavigateToCategory` like reports category drill-down.

### Strings

Reuse existing search strings; add `search_transactions_error` if needed for banner (“Couldn’t load transactions”).

### Docs

README feature list + short note in ADR-017 consequence that owned accounts are cache-first in search.

## Tests

- Blank → no search calls (or cleared state)
- Success merges txs + accounts + categories
- Tx Error still returns accounts/categories; `error` set
- Owned path does not call `refreshOwnedAccounts` (verify with mockk)
- Category navigation is UI/callback — covered by Screen route test if needed

## Order

1. ViewModel cache-first + state clarity  
2. Screen partial UX + category click  
3. NavGraph  
4. Tests + README/ADR  
5. `testDebugUnitTest`, lint, assembleDebug
