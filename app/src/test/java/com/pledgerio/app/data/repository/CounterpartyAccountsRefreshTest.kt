package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountDto
import com.pledgerio.app.data.remote.dto.AccountPagedResponse
import com.pledgerio.app.data.remote.dto.BalancePartitionedDto
import com.pledgerio.app.data.remote.dto.PageInfo
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class CounterpartyAccountsRefreshTest {

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
    }

    @Test
    fun `refreshCounterpartyAccounts fetches subsequent pages using received count as offset`() = runTest {
        val page1 = (1L..50L).map { id ->
            AccountDto(id = id, name = "Party $id", type = "creditor")
        }
        val page2 = (51L..80L).map { id ->
            AccountDto(id = id, name = "Party $id", type = "debtor")
        }
        coEvery {
            apiService.getAccounts(
                type = counterpartyTypes,
                accountName = null,
                offset = 0,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = page1,
                info = PageInfo(records = 80, pages = 2, pageSize = 50),
            ),
        )
        coEvery {
            apiService.getAccounts(
                type = counterpartyTypes,
                accountName = null,
                offset = 50,
                numberOfResults = 200,
            )
        } returns Response.success(
            AccountPagedResponse(
                content = page2,
                info = PageInfo(records = 80, pages = 2, pageSize = 30),
            ),
        )
        coEvery { accountDao.replaceByTypes(any(), any()) } just Runs
        coEvery {
            apiService.getPartitionedBalance("account", any())
        } returns Response.success(
            listOf(
                BalancePartitionedDto(balance = 42.0, partition = "Party 1"),
                BalancePartitionedDto(balance = 7.5, partition = "Party 51"),
            ),
        )

        val result = repository.refreshCounterpartyAccounts()

        assertTrue(result is Resource.Success)
        val accounts = (result as Resource.Success).data
        assertEquals(80, accounts.size)
        assertEquals(42.0, accounts.first { it.name == "Party 1" }.balance, 0.0)
        assertEquals(7.5, accounts.first { it.name == "Party 51" }.balance, 0.0)
        coVerify {
            apiService.getAccounts(
                type = counterpartyTypes,
                accountName = null,
                offset = 50,
                numberOfResults = 200,
            )
        }
    }
}
