package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.ContractDao
import com.pledgerio.app.data.local.entity.ContractEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.ContractDto
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ContractRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val contractDao = mockk<ContractDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: ContractRepositoryImpl

    @Before
    fun setUp() {
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = ContractRepositoryImpl(apiService, contractDao, cacheRefresher)
    }

    @Test
    fun `searchContracts returns cached when present and skips API`() = runTest {
        coEvery { contractDao.searchOnce("net", 20) } returns listOf(
            ContractEntity(id = 1, name = "Netflix"),
        )

        val result = repository.searchContracts("net")

        assertTrue(result is Resource.Success)
        assertEquals("Netflix", (result as Resource.Success).data.first().name)
    }

    @Test
    fun `searchContracts falls through to refresh on empty cache`() = runTest {
        coEvery { contractDao.searchOnce("any", 20) } returns emptyList()
        coEvery { apiService.getContracts(any(), any()) } returns Response.success(
            listOf(ContractDto(id = 2, name = "Power")),
        )
        coEvery { contractDao.replaceAll(any()) } just Runs

        val result = repository.searchContracts("any")

        assertTrue(result is Resource.Success)
        coVerify { apiService.getContracts(any(), any()) }
        coVerify { contractDao.replaceAll(match { it.size == 1 && it.first().name == "Power" }) }
        assertNotNull(syncMetadataDao.getLastSyncedAt(SyncKeys.CONTRACTS))
    }
}
