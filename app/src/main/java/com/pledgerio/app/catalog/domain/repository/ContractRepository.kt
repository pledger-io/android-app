package com.pledgerio.app.catalog.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.catalog.domain.model.Contract
import kotlinx.coroutines.flow.Flow

interface ContractRepository {
    /** Cache-backed contracts that match [query]. */
    fun observeMatching(query: String): Flow<List<Contract>>

    suspend fun searchContracts(name: String? = null): Resource<List<Contract>>

    /** Force a network refresh of all contracts. */
    suspend fun refreshContracts(): Resource<List<Contract>>
}
