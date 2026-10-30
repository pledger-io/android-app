package com.pledgerio.app.catalog.data

import com.pledgerio.app.catalog.data.dao.CurrencyDao
import com.pledgerio.app.catalog.data.entity.CurrencyEntity
import com.pledgerio.app.shared.data.remote.api.PledgerApiService
import com.pledgerio.app.catalog.domain.model.Currency
import com.pledgerio.app.catalog.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CurrencyRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val currencyDao: CurrencyDao,
) : CurrencyRepository {

    override fun getCurrencies(): Flow<List<Currency>> {
        return currencyDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getByCode(code: String): Currency? {
        return currencyDao.getByCode(code)?.toDomain()
    }

    override suspend fun sync(): Boolean {
        return try {
            val response = apiService.getCurrencies()
            if (response.isSuccessful) {
                val currencies = response.body() ?: emptyList()
                val entities = currencies.map { dto ->
                    CurrencyEntity(
                        code = dto.code,
                        name = dto.name,
                        symbol = dto.symbol,
                        decimalPlaces = dto.decimalPlaces,
                        enabled = dto.enabled,
                    )
                }
                currencyDao.deleteAll()
                currencyDao.insertAll(entities)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
