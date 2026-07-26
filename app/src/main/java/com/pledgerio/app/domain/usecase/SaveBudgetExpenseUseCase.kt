package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.common.Resource
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
