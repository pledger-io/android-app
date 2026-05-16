package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import javax.inject.Inject

class CreateInitialBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(year: Int, month: Int, income: Double): Resource<Unit> {
        return budgetRepository.createInitialBudget(year, month, income)
    }
}
