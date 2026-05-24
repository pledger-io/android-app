package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.common.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBudgetsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    operator fun invoke(year: Int, month: Int): Flow<Resource<BudgetListState>> {
        return budgetRepository.getBudgets(year, month)
    }
}
