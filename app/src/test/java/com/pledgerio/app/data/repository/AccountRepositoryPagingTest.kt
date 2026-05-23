package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
import com.pledgerio.app.data.local.entity.AccountEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountDto
import com.pledgerio.app.data.remote.dto.AccountPagedResponse
import com.pledgerio.app.data.remote.dto.BalancePartitionedDto
import com.pledgerio.app.data.remote.dto.PageInfo
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AccountRepositoryPagingTest {

    private val apiService = mockk<PledgerApiService>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val accountTypeDao = mockk<AccountTypeDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: AccountRepositoryImpl

    private val counterpartyTypes = AccountTypeCodes.counterpartyTypeCodes.toList()

    @Before
    fun setUp() {
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = AccountRepositoryImpl(apiService, accountDao, accountTypeDao, cacheRefresher)
        coEvery { accountDao.getAll() } returns flowOf(emptyList())
    }

    @Test
    fun `getCounterpartyAccountsPage fetches from API when cache only has a partial page`() = runTest {
        val staleProbe = AccountEntity(id = 1, name = "Shop", type = "creditor")
        coEvery {
            accountDao.countByTypes(counterpartyTypes, "")
        } returns 642L
        coEvery {
            accountDao.searchByTypes(counterpartyTypes, "", offset = 0, limit = 50)
        } returns listOf(staleProbe)

        val apiPage = (1L..50L).map { id ->
            AccountDto(id = id, name = "Party $id", type = "creditor")
        }
        coEvery {
            apiService.getAccounts(
                type = counterpartyTypes,
                accountName = null,
                offset = 0,
                numberOfResults = 50,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = apiPage,
                info = PageInfo(records = 642, pages = 13, pageSize = 50),
            ),
        )
        coEvery {
            apiService.getPartitionedBalance("account", any())
        } returns Response.success(
            listOf(BalancePartitionedDto(balance = 1.0, partition = "Party 1")),
        )

        val result = repository.getCounterpartyAccountsPage(offset = 0, pageSize = 50, nameQuery = "")

        assertTrue(result is Resource.Success)
        assertEquals(50, (result as Resource.Success).data.items.size)
        assertEquals(642L, result.data.totalRecords)
        coVerify {
            apiService.getAccounts(
                type = counterpartyTypes,
                accountName = null,
                offset = 0,
                numberOfResults = 50,
            )
        }
    }

    @Test
    fun `refreshOwnedAccounts queries types from cache when account-types omits them`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default")
        coEvery { accountDao.getAll() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                AccountEntity(id = 1, name = "Wallet", type = "cash"),
                AccountEntity(id = 2, name = "Shop", type = "creditor"),
            ),
        )
        coEvery {
            apiService.getAccounts(
                type = match { it != null && "default" in it && "cash" in it && "creditor" !in it },
                offset = 0,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = listOf(
                    AccountDto(id = 1, name = "Wallet", type = "cash"),
                    AccountDto(id = 3, name = "Bank", type = "default"),
                ),
                info = PageInfo(records = 2, pages = 1, pageSize = 200),
            ),
        )
        coEvery {
            apiService.getPartitionedBalance("account", any())
        } returns Response.success(emptyList())
        coEvery { accountDao.replaceByTypes(any(), any()) } returns Unit

        val result = repository.refreshOwnedAccounts()

        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.size)
        coVerify {
            apiService.getAccounts(
                type = match { it != null && "cash" in it },
                offset = 0,
                numberOfResults = 200,
            )
        }
    }

    @Test
    fun `refreshOwnedAccounts paginates owned types until all records are loaded`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default", "savings")
        val page1 = (1L..200L).map { id ->
            AccountDto(id = id, name = "Bank $id", type = "default")
        }
        val page2 = (201L..215L).map { id ->
            AccountDto(id = id, name = "Bank $id", type = "default")
        }
        coEvery {
            apiService.getAccounts(
                type = listOf("default", "savings"),
                offset = 0,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = page1,
                info = PageInfo(records = 215, pages = 2, pageSize = 200),
            ),
        )
        coEvery {
            apiService.getAccounts(
                type = listOf("default", "savings"),
                offset = 200,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = page2,
                info = PageInfo(records = 215, pages = 2, pageSize = 15),
            ),
        )
        coEvery {
            apiService.getPartitionedBalance("account", any())
        } returns Response.success(emptyList())
        coEvery { accountDao.replaceByTypes(any(), any()) } returns Unit

        val result = repository.refreshOwnedAccounts()

        assertTrue(result is Resource.Success)
        assertEquals(215, (result as Resource.Success).data.size)
        coVerify {
            apiService.getAccounts(
                type = listOf("default", "savings"),
                offset = 200,
                numberOfResults = 200,
            )
        }
    }
}
