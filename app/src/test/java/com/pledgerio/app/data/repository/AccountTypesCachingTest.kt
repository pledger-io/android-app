package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import com.pledgerio.app.data.remote.api.PledgerApiService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AccountTypesCachingTest {

    private val apiService = mockk<PledgerApiService>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val accountTypeDao = mockk<AccountTypeDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: AccountRepositoryImpl

    @Before
    fun setUp() {
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = AccountRepositoryImpl(apiService, accountDao, accountTypeDao, cacheRefresher)
    }

    @Test
    fun `getAccountTypes serves cached codes without hitting api`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default", "savings", "creditor")
        syncMetadataDao.seed(SyncKeys.ACCOUNT_TYPES, System.currentTimeMillis())

        val result = repository.getAccountTypes()

        assertTrue(result is Resource.Success)
        val codes = (result as Resource.Success).data.map { it.code }
        // The two static counterparty options are always appended by AccountTypeCatalog.
        assertTrue(codes.containsAll(listOf("default", "savings")))
        coVerify(exactly = 0) { apiService.getAccountTypes() }
    }

    @Test
    fun `getAccountTypes falls back to api when cache empty and writes through`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns emptyList()
        coEvery { apiService.getAccountTypes() } returns Response.success(
            listOf("default", "savings", "creditor", "debtor"),
        )
        coEvery { accountTypeDao.replaceAll(any()) } just Runs

        val result = repository.getAccountTypes()

        assertTrue(result is Resource.Success)
        coVerify { accountTypeDao.replaceAll(match { it.contains("default") && it.contains("savings") }) }
        assertNotNull(syncMetadataDao.getLastSyncedAt(SyncKeys.ACCOUNT_TYPES))
    }

    @Test
    fun `refreshAccountTypes lowercases and persists codes`() = runTest {
        coEvery { apiService.getAccountTypes() } returns Response.success(
            listOf("DEFAULT", "Savings", "CREDITOR"),
        )
        coEvery { accountTypeDao.replaceAll(any()) } just Runs

        val result = repository.refreshAccountTypes()

        assertTrue(result is Resource.Success)
        assertEquals(listOf("default", "savings", "creditor"), (result as Resource.Success).data)
        coVerify {
            accountTypeDao.replaceAll(
                match { it == listOf("default", "savings", "creditor") },
            )
        }
    }
}
