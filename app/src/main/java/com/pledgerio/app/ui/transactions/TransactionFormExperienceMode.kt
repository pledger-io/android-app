package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.FinanceExperienceMode

internal fun resolveMoreOptionsExpansion(
    previous: TransactionFormUiState,
    mode: FinanceExperienceMode,
): Boolean {
    return when {
        previous.isEditing -> previous.moreOptionsExpanded
        previous.moreOptionsManuallyToggled -> previous.moreOptionsExpanded
        mode == FinanceExperienceMode.POWER -> true
        else -> false
    }
}
