package com.pledgerio.app.catalog.domain.repository

import com.pledgerio.app.catalog.domain.model.Currency
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun getCurrencies(): Flow<List<Currency>>
    suspend fun getByCode(code: String): Currency?
    suspend fun sync(): Boolean
}
