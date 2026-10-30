package com.pledgerio.app.budgets.domain.usecase

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.budgets.domain.model.BudgetListState
import com.pledgerio.app.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

class SaveBudgetExpenseUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(
        id: Long?,
        name: String,
        budgetAmount: Double,
        year: Int,
        month: Int,
    ): Resource<BudgetListState> = budgetRepository.saveExpenseGroup(
        id = id,
        name = name,
        budgetAmount = budgetAmount,
        year = year,
        month = month,
    )
}
