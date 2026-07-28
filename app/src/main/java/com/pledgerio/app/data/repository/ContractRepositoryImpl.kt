package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.ContractDao
import com.pledgerio.app.data.local.entity.ContractEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.domain.model.Contract
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class ContractRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val contractDao: ContractDao,
    private val cacheRefresher: CacheRefresher,
) : ContractRepository {

    override fun observeMatching(query: String): Flow<List<Contract>> =
        contractDao.observeMatching(query.trim())
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .onStart { triggerStaleRefresh() }

    override suspend fun searchContracts(name: String?): Resource<List<Contract>> {
        triggerStaleRefresh()
        val query = name?.trim().orEmpty()
        val results = contractDao.searchOnce(query, limit = 20).map { it.toDomain() }
        if (results.isNotEmpty()) {
            return Resource.Success(results)
        }
        // Empty cache or first-run: do a synchronous refresh so the UI gets data.
        return when (val refreshed = refreshContracts()) {
            is Resource.Success -> Resource.Success(
                refreshed.data.filter {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                },
            )
            else -> refreshed
        }
    }

    override suspend fun refreshContracts(): Resource<List<Contract>> {
        return cacheRefresher.refreshNow(SyncKeys.CONTRACTS) {
            refreshContractsUnlocked()
        }
    }

    private suspend fun refreshContractsUnlocked(): Resource<List<Contract>> {
        return try {
            val response = apiService.getContracts()
            if (response.isSuccessful) {
                val contracts = response.body()?.map { dto ->
                    Contract(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description ?: "",
                    )
                } ?: emptyList()
                contractDao.replaceAll(contracts.map { ContractEntity.fromDomain(it) })
                Resource.Success(contracts)
            } else {
                Resource.Error("Failed to fetch contracts: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    private fun triggerStaleRefresh() {
        cacheRefresher.launchIfStale(
            key = SyncKeys.CONTRACTS,
            ttlMs = CachePolicy.CONTRACTS_TTL_MS,
        ) { refreshContractsUnlocked() }
    }
}
