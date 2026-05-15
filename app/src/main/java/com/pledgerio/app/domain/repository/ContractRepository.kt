package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Contract
import com.pledgerio.app.util.Resource

interface ContractRepository {
    suspend fun searchContracts(name: String? = null): Resource<List<Contract>>
}
