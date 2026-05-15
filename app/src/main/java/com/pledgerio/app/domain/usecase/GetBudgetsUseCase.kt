package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBudgetsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    operator fun invoke(year: Int, month: Int): Flow<Resource<List<Budget>>> {
        return budgetRepository.getBudgets(year, month)
    }
}
