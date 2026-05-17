package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Currency
import com.pledgerio.app.domain.repository.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyProvider @Inject constructor(
    private val currencyRepository: CurrencyRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Currency>()

    init {
        scope.launch {
            currencyRepository.getCurrencies().collectLatest { currencies ->
                cache.clear()
                currencies.forEach { cache[it.code] = it }
            }
        }
    }

    fun get(code: String): Currency? = cache[code]

    fun clearCache() {
        cache.clear()
    }

    fun formatAmount(amount: Double, currencyCode: String): String {
        val currency = cache[currencyCode]
        return if (currency != null) {
            val formatted = String.format("%,.${currency.decimalPlaces}f", amount)
            "${currency.symbol} $formatted"
        } else {
            val formatted = String.format("%,.2f", amount)
            "$currencyCode $formatted"
        }
    }

    companion object {
        @Volatile
        private var instance: CurrencyProvider? = null

        fun getInstance(): CurrencyProvider? = instance

        internal fun setInstance(provider: CurrencyProvider) {
            instance = provider
        }
    }
}
