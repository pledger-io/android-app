package com.pledgerio.app.domain.usecase

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.domain.repository.BudgetRepository
import javax.inject.Inject

class CreateInitialBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(year: Int, month: Int, income: Double): Resource<Unit> {
        return budgetRepository.createInitialBudget(year, month, income)
    }
}
