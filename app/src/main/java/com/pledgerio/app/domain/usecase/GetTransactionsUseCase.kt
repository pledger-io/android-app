package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionFilters
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.PagedResult
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.domain.common.Resource
import java.time.LocalDate
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        startDate: LocalDate,
        endDate: LocalDate,
        accountId: Long? = null,
        type: TransactionType? = null,
        filters: TransactionFilters = TransactionFilters(),
        page: Int = 0,
        pageSize: Int = 25,
    ): Resource<PagedResult<Transaction>> {
        return transactionRepository.getTransactionsPage(
            startDate = startDate,
            endDate = endDate,
            accountId = accountId,
            type = type,
            filters = filters,
            page = page,
            pageSize = pageSize,
        )
    }
}
