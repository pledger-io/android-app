package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.domain.model.Contract
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.util.Resource
import javax.inject.Inject

class ContractRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
) : ContractRepository {

    override suspend fun searchContracts(name: String?): Resource<List<Contract>> {
        return try {
            val response = apiService.getContracts(name = name?.takeIf { it.isNotBlank() })
            if (response.isSuccessful) {
                val contracts = response.body()?.map { dto ->
                    Contract(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description ?: "",
                    )
                } ?: emptyList()
                Resource.Success(contracts)
            } else {
                Resource.Error("Failed to search contracts: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }
}
