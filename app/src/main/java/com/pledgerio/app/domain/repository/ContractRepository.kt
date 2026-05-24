package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Contract
import com.pledgerio.app.domain.common.Resource
import kotlinx.coroutines.flow.Flow

interface ContractRepository {
    /** Cache-backed contracts that match [query]. */
    fun observeMatching(query: String): Flow<List<Contract>>

    suspend fun searchContracts(name: String? = null): Resource<List<Contract>>

    /** Force a network refresh of all contracts. */
    suspend fun refreshContracts(): Resource<List<Contract>>
}
