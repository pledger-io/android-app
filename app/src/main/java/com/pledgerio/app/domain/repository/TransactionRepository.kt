package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionFilters
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class PagedResult<T>(
    val items: List<T>,
    val totalRecords: Long,
    val totalPages: Int,
    val pageSize: Int,
) {
    val hasMore: Boolean get() = items.size.toLong() < totalRecords
}

interface TransactionRepository {
    suspend fun getTransactionsPage(
        startDate: LocalDate,
        endDate: LocalDate,
        accountId: Long? = null,
        type: TransactionType? = null,
        filters: TransactionFilters = TransactionFilters(),
        page: Int = 0,
        pageSize: Int = 25,
    ): Resource<PagedResult<Transaction>>

    suspend fun getTransaction(id: Long): Resource<Transaction>
    suspend fun createTransaction(transaction: Transaction): Resource<Transaction>
    suspend fun updateTransaction(transaction: Transaction): Resource<Transaction>
    suspend fun deleteTransaction(id: Long): Resource<Unit>
    fun getRecentTransactions(limit: Int = 5): Flow<Resource<List<Transaction>>>
}
