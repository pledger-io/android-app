package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.TagDao
import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.TagEntity
import com.pledgerio.app.data.local.entity.TransactionEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateTagRequest
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class TagRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setUp() {
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = TagRepositoryImpl(apiService, tagDao, transactionDao, cacheRefresher)
    }

    @Test
    fun `refreshTags writes names to dao and marks metadata fresh`() = runTest {
        coEvery { apiService.getTags(name = null) } returns Response.success(
            listOf("vacation", "business"),
        )
        coEvery { tagDao.replaceAll(any()) } just Runs

        val result = repository.refreshTags()

        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.size)
        coVerify {
            tagDao.replaceAll(match { names ->
                names.size == 2 && names.contains("vacation")
            })
        }
        assertNotNull(syncMetadataDao.getLastSyncedAt(SyncKeys.TAGS))
    }

    @Test
    fun `createTag inserts locally on 204`() = runTest {
        coEvery { apiService.createTag(CreateTagRequest("travel")) } returns Response.success(Unit)
        coEvery { tagDao.insert(TagEntity("travel")) } just Runs
        coEvery { apiService.getTags(name = null) } returns Response.success(emptyList())
        coEvery { tagDao.replaceAll(any()) } just Runs

        val result = repository.createTag("travel")

        assertTrue(result is Resource.Success)
        assertEquals("travel", (result as Resource.Success).data.name)
        coVerify { tagDao.insert(TagEntity("travel")) }
    }

    @Test
    fun `deleteTag removes from dao on 204`() = runTest {
        val tx = TransactionEntity(
            id = 1L,
            description = "Coffee",
            amount = 3.5,
            type = "CREDIT",
            date = java.time.LocalDate.now(),
            tags = listOf("old", "keep"),
        )
        coEvery { apiService.deleteTag("old") } returns Response.success(Unit)
        coEvery { tagDao.deleteByName("old") } just Runs
        coEvery { transactionDao.getAllOnce() } returns listOf(tx)
        coEvery { transactionDao.insert(any()) } just Runs
        coEvery { apiService.getTags(name = null) } returns Response.success(emptyList())
        coEvery { tagDao.replaceAll(any()) } just Runs

        val result = repository.deleteTag("old")

        assertTrue(result is Resource.Success)
        coVerify { tagDao.deleteByName("old") }
        coVerify {
            transactionDao.insert(
                match { entity -> entity.tags == listOf("keep") },
            )
        }
    }

    @Test
    fun `renameTag creates new tag then deletes old`() = runTest {
        val tx = TransactionEntity(
            id = 2L,
            description = "Groceries",
            amount = 50.0,
            type = "CREDIT",
            date = java.time.LocalDate.now(),
            tags = listOf("old-name"),
        )
        coEvery { apiService.createTag(CreateTagRequest("new-name")) } returns Response.success(Unit)
        coEvery { tagDao.insert(TagEntity("new-name")) } just Runs
        coEvery { apiService.deleteTag("old-name") } returns Response.success(Unit)
        coEvery { tagDao.deleteByName("old-name") } just Runs
        coEvery { transactionDao.getAllOnce() } returns listOf(tx)
        coEvery { transactionDao.insert(any()) } just Runs
        coEvery { apiService.getTags(name = null) } returns Response.success(emptyList())
        coEvery { tagDao.replaceAll(any()) } just Runs

        val result = repository.renameTag("old-name", "new-name")

        assertTrue(result is Resource.Success)
        assertEquals("new-name", (result as Resource.Success).data.name)
        coVerify { apiService.deleteTag("old-name") }
        coVerify {
            transactionDao.insert(
                match { entity -> entity.tags == listOf("new-name") },
            )
        }
    }

    @Test
    fun `refreshTags returns error on http failure`() = runTest {
        coEvery { apiService.getTags(name = null) } returns Response.error(
            500,
            "".toResponseBody(),
        )

        val result = repository.refreshTags()

        assertTrue(result is Resource.Error)
    }
}
