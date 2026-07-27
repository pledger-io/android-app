package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.dao.TransactionOutboxDao
import com.pledgerio.app.data.local.entity.TransactionOutboxEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.TransactionDto
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.model.OutboxStatus
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SyncSessionGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class TransactionOutboxRepositoryImplTest {

    private val outboxDao = mockk<TransactionOutboxDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val apiService = mockk<PledgerApiService>()
    private val mutationInvalidator = mockk<TransactionMutationInvalidator>(relaxed = true)
    private val sessionGuard = mockk<SyncSessionGuard>()

    private val repository = TransactionOutboxRepositoryImpl(
        outboxDao = outboxDao,
        transactionDao = transactionDao,
        apiService = apiService,
        mutationInvalidator = mutationInvalidator,
        sessionGuard = sessionGuard,
    )

    @Test
    fun `enqueueCreate persists row and observePending emits it`() = runTest {
        val inserted = slot<TransactionOutboxEntity>()
        coEvery { outboxDao.insert(capture(inserted)) } returns Unit
        every { outboxDao.observeAll() } answers {
            flowOf(listOf(inserted.captured))
        }

        val result = repository.enqueueCreate(sampleTransaction())

        assertTrue(result is Resource.Success)
        val pending = (result as Resource.Success).data
        assertEquals("Coffee", pending.description)
        assertEquals(OutboxStatus.PENDING, pending.status)
        coVerify { outboxDao.insert(any()) }

        val observed = repository.observePending().first()
        assertEquals(1, observed.size)
        assertEquals(pending.localId, observed.first().localId)
    }

    @Test
    fun `flushPending success removes row inserts transaction and invalidates`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns listOf(sampleEntity())
        coEvery { apiService.createTransaction(any()) } returns Response.success(
            TransactionDto(
                id = 99,
                description = "Coffee",
                amount = 4.5,
                currency = "EUR",
                type = "CREDIT",
            ),
        )

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify { transactionDao.insert(match { it.id == 99L }) }
        coVerify { mutationInvalidator.invalidate(any()) }
        coVerify { outboxDao.deleteByLocalId("local-1") }
    }

    @Test
    fun `flushPending IOException leaves pending and stops`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns listOf(sampleEntity())
        coEvery { apiService.createTransaction(any()) } throws IOException("offline")

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.StoppedOnNetworkError, result)
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
        coVerify(exactly = 0) { outboxDao.updateStatus(any(), OutboxStatus.FAILED.name, any(), any()) }
    }

    @Test
    fun `flushPending HTTP 400 marks FAILED`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns listOf(sampleEntity())
        coEvery { apiService.createTransaction(any()) } returns Response.error(
            400,
            "".toResponseBody(null),
        )

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify {
            outboxDao.updateStatus(
                localId = "local-1",
                status = OutboxStatus.FAILED.name,
                lastError = match { it.contains("400") },
                attemptCount = 1,
            )
        }
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
    }

    @Test
    fun `flushPending stale generation does not call API`() = runTest {
        every { sessionGuard.isCurrent("stale") } returns false

        val result = repository.flushPending("stale")

        assertEquals(FlushResult.AbortedStaleSession, result)
        coVerify(exactly = 0) { outboxDao.getByStatus(any()) }
        coVerify(exactly = 0) { apiService.createTransaction(any()) }
    }

    @Test
    fun `discard deletes outbox row`() = runTest {
        coEvery { outboxDao.deleteByLocalId("local-1") } returns Unit

        val result = repository.discard("local-1")

        assertTrue(result is Resource.Success)
        coVerify { outboxDao.deleteByLocalId("local-1") }
    }

    private fun sampleTransaction() = Transaction(
        id = 0,
        description = "Coffee",
        amount = 4.5,
        currency = "EUR",
        type = TransactionType.CREDIT,
        date = LocalDate.of(2026, 7, 27),
        sourceAccountId = 1,
        sourceAccountName = "Checking",
        destinationAccountId = 2,
        destinationAccountName = "Cafe",
        categoryName = "Food",
        tags = listOf("cafe"),
    )

    private fun sampleEntity() = TransactionOutboxEntity(
        localId = "local-1",
        createdAtMillis = 1L,
        status = OutboxStatus.PENDING.name,
        date = "2026-07-27",
        currency = "EUR",
        description = "Coffee",
        amount = 4.5,
        sourceAccountId = 1,
        destinationAccountId = 2,
        tagsJson = """["cafe"]""",
        type = TransactionType.CREDIT.name,
    )
}
