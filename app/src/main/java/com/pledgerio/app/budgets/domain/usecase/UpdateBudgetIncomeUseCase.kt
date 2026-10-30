package com.pledgerio.app.budgets.domain.usecase

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.budgets.domain.model.BudgetListState
import com.pledgerio.app.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

class UpdateBudgetIncomeUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(
        year: Int,
        month: Int,
        income: Double,
    ): Resource<BudgetListState> = budgetRepository.updateBudgetIncome(year, month, income)
}
