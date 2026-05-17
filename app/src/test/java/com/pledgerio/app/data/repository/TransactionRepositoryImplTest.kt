package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.TransactionClassificationSuggestionDto
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class TransactionRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val repository = TransactionRepositoryImpl(apiService, transactionDao)

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
}
