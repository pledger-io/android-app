package com.pledgerio.app.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SyncWorkerTest {

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
