package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.SpendingInsightsCache
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.DetectedInsightDto
import com.pledgerio.app.data.remote.dto.DetectedPatternDto
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.model.InsightSeverity
import com.pledgerio.app.domain.model.InsightType
import com.pledgerio.app.domain.model.PatternType
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth

class SpendingInsightRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val cache = SpendingInsightsCache()
    private val repository = SpendingInsightRepositoryImpl(apiService, categoryRepository, cache)

    private val month = YearMonth.of(2026, 5)

    private fun stubCategories(vararg categories: Category) {
        every { categoryRepository.observeCategories() } returns flowOf(categories.toList())
    }

    @Test
    fun `maps insights and resolves category ids for drill-down`() = runTest {
        stubCategories(Category(id = 7, name = "Groceries"))
        coEvery { apiService.getDetectedInsights(2026, 5) } returns Response.success(
            listOf(
                DetectedInsightDto(
                    type = "SPENDING_SPIKE",
                    category = "Groceries",
                    severity = "ALERT",
                    score = 0.8,
                    detectedDate = "2026-05-14",
                    message = "Spending is up 40%",
                    transactionId = 42,
                ),
            ),
        )
        coEvery { apiService.getDetectedPatterns(2026, 5) } returns Response.success(
            listOf(
                DetectedPatternDto(
                    type = "RECURRING_MONTHLY",
                    category = "Groceries",
                    confidence = 0.9,
                    detectedDate = "2026-05-01",
                ),
            ),
        )

        val result = repository.getInsights(month)

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        val insight = data.insights.single()
        assertEquals(InsightType.SPENDING_SPIKE, insight.type)
        assertEquals(InsightSeverity.ALERT, insight.severity)
        assertEquals(LocalDate.of(2026, 5, 14), insight.detectedDate)
        assertEquals(42L, insight.transactionId)
        assertEquals(7L, insight.categoryId)
        val pattern = data.patterns.single()
        assertEquals(PatternType.RECURRING_MONTHLY, pattern.type)
        assertEquals(7L, pattern.categoryId)
    }

    @Test
    fun `unknown enum values degrade instead of dropping the insight`() = runTest {
        stubCategories()
        coEvery { apiService.getDetectedInsights(2026, 5) } returns Response.success(
            listOf(
                DetectedInsightDto(
                    type = "SOMETHING_NEW",
                    category = "",
                    severity = null,
                    message = "Heads up",
                ),
            ),
        )
        coEvery { apiService.getDetectedPatterns(2026, 5) } returns Response.success(emptyList())

        val insight = (repository.getInsights(month) as Resource.Success).data.insights.single()

        assertEquals(InsightType.UNKNOWN, insight.type)
        assertEquals(InsightSeverity.INFO, insight.severity)
        assertNull(insight.categoryId)
    }

    @Test
    fun `second call is served from cache`() = runTest {
        stubCategories()
        coEvery { apiService.getDetectedInsights(2026, 5) } returns Response.success(emptyList())
        coEvery { apiService.getDetectedPatterns(2026, 5) } returns Response.success(emptyList())

        repository.getInsights(month)
        repository.getInsights(month)

        coVerify(exactly = 1) { apiService.getDetectedInsights(2026, 5) }
    }

    @Test
    fun `forceRefresh bypasses the cache`() = runTest {
        stubCategories()
        coEvery { apiService.getDetectedInsights(2026, 5) } returns Response.success(emptyList())
        coEvery { apiService.getDetectedPatterns(2026, 5) } returns Response.success(emptyList())

        repository.getInsights(month)
        repository.getInsights(month, forceRefresh = true)

        coVerify(exactly = 2) { apiService.getDetectedInsights(2026, 5) }
    }

    @Test
    fun `partial failure is reported and nothing is cached`() = runTest {
        stubCategories()
        coEvery { apiService.getDetectedInsights(2026, 5) } returns Response.success(emptyList())
        coEvery { apiService.getDetectedPatterns(2026, 5) } returns Response.error(
            500,
            "boom".toResponseBody("text/plain".toMediaType()),
        )

        val result = repository.getInsights(month)

        assertTrue(result is Resource.Error)
        assertNull(cache.get(month))
    }
}
