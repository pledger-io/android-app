package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.entity.CategoryEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CategoryDto
import com.pledgerio.app.data.remote.dto.CategoryPagedResponse
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class CategoryRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: CategoryRepositoryImpl

    @Before
    fun setUp() {
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = CategoryRepositoryImpl(apiService, categoryDao, cacheRefresher)
    }

    @Test
    fun `searchCategories returns cached results when present`() = runTest {
        val cached = listOf(CategoryEntity(id = 1, name = "Groceries"))
        coEvery { categoryDao.searchOnce("Gro", 20) } returns cached

        val result = repository.searchCategories("Gro")

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.size)
        assertEquals("Groceries", result.data.first().name)
    }

    @Test
    fun `refreshCategories writes through to dao and marks metadata fresh`() = runTest {
        val dto = CategoryDto(id = 7, name = "Fuel")
        coEvery { apiService.getCategories(any(), any(), any()) } returns
            Response.success(CategoryPagedResponse(content = listOf(dto)))
        coEvery { categoryDao.replaceAll(any()) } just Runs

        val result = repository.refreshCategories()

        assertTrue(result is Resource.Success)
        coVerify { categoryDao.replaceAll(match { it.size == 1 && it.first().name == "Fuel" }) }
        assertNotNull(syncMetadataDao.getLastSyncedAt(SyncKeys.CATEGORIES))
    }

    @Test
    fun `getCategories emits cached then refreshed on stale cache`() = runTest {
        val cachedRow = CategoryEntity(id = 1, name = "Cached")
        coEvery { categoryDao.getAll() } returns flowOf(listOf(cachedRow))
        coEvery { apiService.getCategories(any(), any(), any()) } returns
            Response.success(
                CategoryPagedResponse(content = listOf(CategoryDto(id = 2, name = "Fresh"))),
            )
        coEvery { categoryDao.replaceAll(any()) } just Runs

        val emissions = mutableListOf<Resource<*>>()
        repository.getCategories().collect { emissions.add(it) }

        assertTrue(emissions.first() is Resource.Loading)
        val success = emissions.filterIsInstance<Resource.Success<*>>()
        assertTrue("expected both cached and refreshed success", success.size >= 2)
    }

    @Test
    fun `getCategories skips refresh when cache is fresh`() = runTest {
        val cachedRow = CategoryEntity(id = 1, name = "Cached")
        coEvery { categoryDao.getAll() } returns flowOf(listOf(cachedRow))
        syncMetadataDao.seed(SyncKeys.CATEGORIES, System.currentTimeMillis())

        val emissions = mutableListOf<Resource<*>>()
        repository.getCategories().collect { emissions.add(it) }

        assertTrue(emissions.first() is Resource.Loading)
        assertEquals(1, emissions.filterIsInstance<Resource.Success<*>>().size)
        coVerify(exactly = 0) { apiService.getCategories(any(), any(), any()) }
    }

    @Test
    fun `cache policy treats null timestamp as stale`() {
        assertTrue(CachePolicy.isStale(null, CachePolicy.CATEGORIES_TTL_MS))
    }
}
