package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.TransactionEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.TransactionClassificationSuggestionDto
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class TransactionRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val mutationInvalidator = mockk<TransactionMutationInvalidator>(relaxed = true)
    private val repository = TransactionRepositoryImpl(
        apiService,
        transactionDao,
        mutationInvalidator,
    )

    @Test
    fun `suggestClassifications maps dto to domain model`() = runTest {
        coEvery {
            apiService.suggestClassifications(
                amount = 35.5,
                description = "Lunch",
                source = "Checking",
                destination = "Cafe",
            )
        } returns Response.success(
            TransactionClassificationSuggestionDto(
                budget = "Food",
                category = "Dining out",
                tags = listOf("weekday"),
            ),
        )

        val result = repository.suggestClassifications(
            amount = 35.5,
            description = "Lunch",
            source = "Checking",
            destination = "Cafe",
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals("Food", data.budget)
        assertEquals("Dining out", data.category)
        assertEquals(listOf("weekday"), data.tags)
    }

    @Test
    fun `suggestClassifications returns error for non-success response`() = runTest {
        coEvery {
            apiService.suggestClassifications(any(), any(), any(), any())
        } returns Response.error(500, "".toResponseBody(null))

        val result = repository.suggestClassifications(
            amount = 10.0,
            description = "Coffee",
            source = null,
            destination = null,
        )

        assertTrue(result is Resource.Error)
        assertTrue((result as Resource.Error).message?.contains("Failed to classify") == true)
    }

    @Test
    fun `deleteTransaction succeeds on HTTP 204 and deletes cache by id`() = runTest {
        val date = LocalDate.of(2026, 7, 1)
        coEvery { apiService.deleteTransaction(42) } returns Response.success(Unit)
        coEvery { transactionDao.getById(42) } returns cachedTransaction(42, date)

        val result = repository.deleteTransaction(42, date)

        assertTrue(result is Resource.Success)
        coVerifyOrder {
            transactionDao.getById(42)
            apiService.deleteTransaction(42)
            transactionDao.deleteById(42)
            mutationInvalidator.invalidate(date)
        }
    }

    @Test
    fun `deleteTransaction treats HTTP 404 as success`() = runTest {
        val date = LocalDate.of(2026, 6, 15)
        coEvery { apiService.deleteTransaction(7) } returns Response.error(
            404,
            "".toResponseBody(null),
        )
        coEvery { transactionDao.getById(7) } returns cachedTransaction(7, date)

        val result = repository.deleteTransaction(7, transactionDate = null)

        assertTrue(result is Resource.Success)
        coVerify { transactionDao.deleteById(7) }
        coVerify { mutationInvalidator.invalidate(date) }
    }

    @Test
    fun `deleteTransaction returns error for non-404 failures`() = runTest {
        coEvery { apiService.deleteTransaction(9) } returns Response.error(
            500,
            "".toResponseBody(null),
        )
        coEvery { transactionDao.getById(9) } returns null

        val result = repository.deleteTransaction(9)

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionDao.deleteById(any()) }
        coVerify(exactly = 0) { mutationInvalidator.invalidate(any()) }
    }

    private fun cachedTransaction(id: Long, date: LocalDate) = TransactionEntity(
        id = id,
        description = "Coffee",
        amount = 4.5,
        type = TransactionType.CREDIT.name,
        date = date,
    )
}
