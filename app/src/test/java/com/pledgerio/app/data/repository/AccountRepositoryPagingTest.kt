package com.pledgerio.app.data.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.shared.data.cache.CacheRefresher
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.catalog.data.dao.AccountTypeDao
import com.pledgerio.app.data.local.entity.AccountEntity
import com.pledgerio.app.shared.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountDto
import com.pledgerio.app.data.remote.dto.AccountPagedResponse
import com.pledgerio.app.data.remote.dto.BalanceDto
import com.pledgerio.app.data.remote.dto.BalancePartitionedDto
import com.pledgerio.app.data.remote.dto.BalanceRequest
import com.pledgerio.app.data.remote.dto.PageInfo
import com.pledgerio.app.catalog.domain.model.AccountTypeCodes
import com.pledgerio.app.util.FakeSyncMetadataDao
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
import okhttp3.ResponseBody.Companion.toResponseBody
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
        coEvery { apiService.getBalance(any()) } returns Response.success(BalanceDto(balance = 0.0))
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
                type = listOf("default", "savings") + AccountTypeCodes.liabilityTypeCodes,
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
                type = listOf("default", "savings") + AccountTypeCodes.liabilityTypeCodes,
                offset = 200,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = page2,
                info = PageInfo(records = 215, pages = 2, pageSize = 15),
            ),
        )
        coEvery { apiService.getBalance(any()) } returns Response.success(BalanceDto(balance = 0.0))
        coEvery { accountDao.replaceByTypes(any(), any()) } returns Unit

        val result = repository.refreshOwnedAccounts()

        assertTrue(result is Resource.Success)
        assertEquals(215, (result as Resource.Success).data.size)
        coVerify {
            apiService.getAccounts(
                type = listOf("default", "savings") + AccountTypeCodes.liabilityTypeCodes,
                offset = 200,
                numberOfResults = 200,
            )
        }
    }

    @Test
    fun `refreshOwnedAccounts loads each owned balance by account id`() = runTest {
        coEvery { accountTypeDao.getAllCodes() } returns listOf("default", "savings")
        coEvery {
            apiService.getAccounts(
                type = listOf("default", "savings") + AccountTypeCodes.liabilityTypeCodes,
                offset = 0,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = listOf(
                    AccountDto(id = 1, name = "Checking", type = "default"),
                    AccountDto(id = 2, name = "Savings", type = "savings"),
                    AccountDto(id = 3, name = "Savings", type = "savings"),
                ),
                info = PageInfo(records = 3, pages = 1, pageSize = 200),
            ),
        )
        coEvery {
            apiService.getBalance(match<BalanceRequest> { it.accounts == listOf(1L) })
        } returns Response.success(BalanceDto(balance = 1250.5))
        coEvery {
            apiService.getBalance(match<BalanceRequest> { it.accounts == listOf(2L) })
        } returns Response.success(BalanceDto(balance = 300.0))
        coEvery {
            apiService.getBalance(match<BalanceRequest> { it.accounts == listOf(3L) })
        } returns Response.error(500, "".toResponseBody())
        coEvery { accountDao.getById(3) } returns AccountEntity(id = 3, name = "Savings", type = "savings", balance = 42.0)
        coEvery { accountDao.replaceByTypes(any(), any()) } returns Unit

        val result = repository.refreshOwnedAccounts()

        assertTrue(result is Resource.Success)
        val balances = (result as Resource.Success).data.associate { it.id to it.balance }
        assertEquals(1250.5, balances.getValue(1), 0.001)
        assertEquals(300.0, balances.getValue(2), 0.001)
        assertEquals(42.0, balances.getValue(3), 0.001)
        coVerify(exactly = 0) { apiService.getPartitionedBalance(any(), any()) }
    }
}
