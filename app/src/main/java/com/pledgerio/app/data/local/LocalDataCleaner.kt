package com.pledgerio.app.data.local

import android.content.Context
import androidx.room.withTransaction
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.pledgerio.app.data.cache.ReportsOverviewCache
import com.pledgerio.app.util.CurrencyProvider
import com.pledgerio.app.util.TransactionTemplateStore
import com.pledgerio.app.util.UserPreferences
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes all user-specific offline data so a subsequent login (possibly as another user)
 * never reads the previous session's Room cache, sync timestamps, or form templates.
 */
@Singleton
class LocalDataCleaner @Inject constructor(
    private val database: PledgerDatabase,
    @ApplicationContext private val context: Context,
    private val currencyProvider: Lazy<CurrencyProvider>,
    private val userPreferences: Lazy<UserPreferences>,
    private val transactionTemplateStore: Lazy<TransactionTemplateStore>,
    private val reportsOverviewCache: ReportsOverviewCache,
) {

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllUserData() {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.clearAllTables()
            }
            currencyProvider.get().clearCache()
            userPreferences.get().clearSessionData()
            transactionTemplateStore.get().clearAll()
            reportsOverviewCache.clearAll()
            runCatching {
                val loader = Coil.imageLoader(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
    }

}
