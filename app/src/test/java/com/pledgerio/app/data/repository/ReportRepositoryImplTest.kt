package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BalanceDatedDto
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.YearMonth

class ReportRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true)
    private lateinit var repository: ReportRepositoryImpl

    @Before
    fun setUp() {
        repository = ReportRepositoryImpl(apiService, budgetRepository)
    }

    @Test
    fun `getNetWorthTrend uses daily balance grouping for selected month`() = runTest {
        val month = YearMonth.of(2026, 5)
        val typeSlot = slot<String>()
        coEvery {
            apiService.getDatedBalance(capture(typeSlot), any())
        } returns Response.success(
            listOf(
                BalanceDatedDto(date = "2026-05-01", balance = 1000.0),
                BalanceDatedDto(date = "2026-05-02", balance = 1100.0),
            ),
        )

        val result = repository.getNetWorthTrend(month)

        assertEquals("daily", typeSlot.captured)
        coVerify { apiService.getDatedBalance("daily", any()) }
        assertTrue(result is Resource.Success)
        val points = (result as Resource.Success).data
        assertEquals(2, points.size)
        assertEquals("2026-05-01", points[0].date)
        assertEquals(1000.0, points[0].amount, 0.01)
    }
}
