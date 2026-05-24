package com.pledgerio.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Invokes [onLoadMore] when the user scrolls within [threshold] items of the list end.
 */
@Composable
fun LazyListPaginationEffect(
    listState: LazyListState,
    enabled: Boolean,
    threshold: Int = 5,
    alsoLoadWhen: () -> Boolean = { false },
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(listState, enabled, threshold) {
        derivedStateOf {
            if (!enabled) return@derivedStateOf false
            if (alsoLoadWhen()) return@derivedStateOf true
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 0) return@derivedStateOf false
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - threshold
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
}
