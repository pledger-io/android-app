package com.pledgerio.app.budgets.domain.usecase

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.budgets.domain.model.BudgetListState
import com.pledgerio.app.budgets.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBudgetsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    operator fun invoke(year: Int, month: Int): Flow<Resource<BudgetListState>> {
        return budgetRepository.getBudgets(year, month)
    }
}
