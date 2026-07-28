package com.pledgerio.app.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.YearMonth

class SyncWorkerTest {

    @Test
    fun `completed sync maps to worker success`() {
        val result = SyncWorker.resultFor(
            SyncRunOutcome.Completed(
                budgetsForAlerts = emptyList(),
                yearMonth = YearMonth.of(2026, 7),
            ),
        )

        assertEquals("Success", result.javaClass.simpleName)
    }

    @Test
    fun `retryable sync outcome maps to worker retry`() {
        val result = SyncWorker.resultFor(
            SyncRunOutcome.RetryableFailure(
                retryableFailureCount = 1,
                permanentFailureCount = 0,
            ),
        )

        assertEquals("Retry", result.javaClass.simpleName)
    }

    @Test
    fun `permanent sync outcome maps to worker failure`() {
        val result = SyncWorker.resultFor(SyncRunOutcome.PermanentFailure(failureCount = 1))

        assertEquals("Failure", result.javaClass.simpleName)
    }

    @Test
    fun `classifyFailure retries on io exceptions`() {
        val result = SyncWorker.classifyFailure(IOException("network"))
        assertEquals("Retry", result.javaClass.simpleName)
    }

    @Test
    fun `classifyFailure fails on unauthorized`() {
        val result = SyncWorker.classifyFailure(httpException(401))
        assertEquals("Failure", result.javaClass.simpleName)
    }

    @Test
    fun `classifyFailure fails on non-retryable client errors`() {
        val result = SyncWorker.classifyFailure(httpException(400))
        assertEquals("Failure", result.javaClass.simpleName)
    }

    @Test
    fun `classifyFailure retries on server http errors`() {
        val result = SyncWorker.classifyFailure(httpException(500))
        assertEquals("Retry", result.javaClass.simpleName)
    }

    @Test
    fun `classifyFailure fails on unknown exception`() {
        val result = SyncWorker.classifyFailure(IllegalStateException("boom"))
        assertEquals("Failure", result.javaClass.simpleName)
    }

    private fun httpException(code: Int): HttpException {
        val response = Response.error<Any>(
            code,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        return HttpException(response)
    }
}
