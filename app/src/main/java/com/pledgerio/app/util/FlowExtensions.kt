package com.pledgerio.app.util

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

object SearchDefaults {
    const val DEBOUNCE_MS = 300L
    const val CATALOG_DEBOUNCE_MS = 250L
    const val ACCOUNT_SEARCH_DEBOUNCE_MS = 350L
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun <T> Flow<String>.debouncedFlatMapLatest(
    debounceMs: Long = SearchDefaults.DEBOUNCE_MS,
    transform: suspend (String) -> Flow<T>,
): Flow<T> = debounce(debounceMs)
    .distinctUntilChanged()
    .flatMapLatest(transform)
