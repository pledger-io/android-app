package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.FilterOption

internal fun clearCounterpartySearchState(
    state: TransactionFormUiState,
    isSource: Boolean,
): TransactionFormUiState {
    return if (isSource) {
        state.copy(sourceSuggestions = emptyList(), isSearchingSource = false)
    } else {
        state.copy(targetSuggestions = emptyList(), isSearchingTarget = false)
    }
}

internal fun markCounterpartySearchInProgress(
    state: TransactionFormUiState,
    isSource: Boolean,
): TransactionFormUiState {
    return if (isSource) {
        state.copy(isSearchingSource = true)
    } else {
        state.copy(isSearchingTarget = true)
    }
}

internal fun applyCounterpartySearchSuccess(
    state: TransactionFormUiState,
    isSource: Boolean,
    options: List<FilterOption>,
): TransactionFormUiState {
    return if (isSource) {
        state.copy(isSearchingSource = false, sourceSuggestions = options)
    } else {
        state.copy(isSearchingTarget = false, targetSuggestions = options)
    }
}
