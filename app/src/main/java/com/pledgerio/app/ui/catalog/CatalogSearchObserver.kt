package com.pledgerio.app.ui.catalog

import androidx.lifecycle.viewModelScope
import com.pledgerio.app.util.SearchDefaults
import com.pledgerio.app.util.debouncedFlatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel

/**
 * Observes a debounced search query and emits matching catalog items from Room.
 */
fun <T> ViewModel.observeCatalogSearch(
    searchQueryFlow: MutableStateFlow<String>,
    observeAll: () -> Flow<List<T>>,
    observeMatching: (String) -> Flow<List<T>>,
    onItems: (List<T>) -> Unit,
) {
    viewModelScope.launch {
        searchQueryFlow
            .debouncedFlatMapLatest(SearchDefaults.CATALOG_DEBOUNCE_MS) { query ->
                if (query.isBlank()) observeAll() else observeMatching(query)
            }
            .collect(onItems)
    }
}
