package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.dao.TransactionOutboxDao
import com.pledgerio.app.data.local.entity.TransactionOutboxEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountLinkDto
import com.pledgerio.app.data.remote.dto.PageInfo
import com.pledgerio.app.data.remote.dto.TransactionDatesDto
import com.pledgerio.app.data.remote.dto.TransactionDto
import com.pledgerio.app.data.remote.dto.TransactionPagedResponse
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.model.OutboxStatus
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionDataBarrier
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.SyncSessionGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
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
    private val sessionManager = mockk<SessionManager>()
    private val sessionDataBarrier = SessionDataBarrier()

    private val repository = TransactionOutboxRepositoryImpl(
        outboxDao = outboxDao,
        transactionDao = transactionDao,
        apiService = apiService,
        mutationInvalidator = mutationInvalidator,
        sessionGuard = sessionGuard,
        sessionManager = sessionManager,
        sessionDataBarrier = sessionDataBarrier,
    )

    @Test
    fun `enqueueCreate persists row and observePending emits it`() = runTest {
        every { sessionManager.getSyncGeneration() } returns "gen"
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
    fun `enqueueCreate refuses when session generation missing`() = runTest {
        every { sessionManager.getSyncGeneration() } returns null

        val result = repository.enqueueCreate(sampleTransaction())

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { outboxDao.insert(any()) }
    }

    @Test
    fun `flushPending success removes row inserts transaction and invalidates`() = runTest {
        givenSinglePendingRow()
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
    fun `flushPending IOException leaves the claimed row in flight and stops`() = runTest {
        givenSinglePendingRow()
        coEvery { apiService.createTransaction(any()) } throws IOException("offline")

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.StoppedOnNetworkError, result)
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
        coVerify(exactly = 0) { outboxDao.updateStatus(any(), OutboxStatus.FAILED.name, any(), any()) }
        coVerify(exactly = 0) { outboxDao.updateStatus(any(), OutboxStatus.PENDING.name, any(), any()) }
    }

    @Test
    fun `flushPending skips a row claimed by a concurrent flush`() = runTest {
        givenSinglePendingRow()
        coEvery { outboxDao.claimForSend(any(), any(), any()) } returns 0

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify(exactly = 0) { apiService.createTransaction(any()) }
    }

    @Test
    fun `flushPending drops an in-flight row that reached the server`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery {
            outboxDao.getByStatus(OutboxStatus.IN_FLIGHT.name)
        } returns listOf(sampleEntity().copy(status = OutboxStatus.IN_FLIGHT.name, attemptCount = 1))
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns emptyList()
        coEvery {
            apiService.getTransactions(
                startDate = any(),
                endDate = any(),
                accounts = any(),
                numberOfResults = any(),
            )
        } returns Response.success(serverPage(sampleServerTransaction()))

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify { outboxDao.deleteByLocalId("local-1") }
        coVerify(exactly = 0) { apiService.createTransaction(any()) }
    }

    @Test
    fun `flushPending requeues an in-flight row the server never received`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery {
            outboxDao.getByStatus(OutboxStatus.IN_FLIGHT.name)
        } returns listOf(sampleEntity().copy(status = OutboxStatus.IN_FLIGHT.name, attemptCount = 1))
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns emptyList()
        coEvery {
            apiService.getTransactions(
                startDate = any(),
                endDate = any(),
                accounts = any(),
                numberOfResults = any(),
            )
        } returns Response.success(serverPage())

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify {
            outboxDao.updateStatus(
                localId = "local-1",
                status = OutboxStatus.PENDING.name,
                lastError = null,
                attemptCount = 2,
            )
        }
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
    }

    @Test
    fun `flushPending fails a row that exhausted its send attempts`() = runTest {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery {
            outboxDao.getByStatus(OutboxStatus.IN_FLIGHT.name)
        } returns listOf(sampleEntity().copy(status = OutboxStatus.IN_FLIGHT.name, attemptCount = 4))
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns emptyList()
        coEvery {
            apiService.getTransactions(
                startDate = any(),
                endDate = any(),
                accounts = any(),
                numberOfResults = any(),
            )
        } returns Response.success(serverPage())

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify {
            outboxDao.updateStatus(
                localId = "local-1",
                status = OutboxStatus.FAILED.name,
                lastError = any(),
                attemptCount = 5,
            )
        }
    }

    @Test
    fun `flushPending leaves the row in flight when the send fails unexpectedly`() = runTest {
        givenSinglePendingRow()
        coEvery { apiService.createTransaction(any()) } throws IllegalStateException("malformed")

        val result = repository.flushPending("gen")

        assertEquals(FlushResult.Completed, result)
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
        coVerify(exactly = 0) { outboxDao.updateStatus(any(), any(), any(), any()) }
    }

    @Test
    fun `flushPending HTTP 400 marks FAILED`() = runTest {
        givenSinglePendingRow()
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
    fun `flushPending CancellationException leaves row pending`() = runTest {
        givenSinglePendingRow()
        coEvery { apiService.createTransaction(any()) } throws CancellationException("replaced")

        try {
            repository.flushPending("gen")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        coVerify(exactly = 0) { outboxDao.updateStatus(any(), OutboxStatus.FAILED.name, any(), any()) }
        coVerify(exactly = 0) { outboxDao.deleteByLocalId(any()) }
    }

    @Test
    fun `discard deletes outbox row`() = runTest {
        coEvery { outboxDao.deleteByLocalId("local-1") } returns Unit

        val result = repository.discard("local-1")

        assertTrue(result is Resource.Success)
        coVerify { outboxDao.deleteByLocalId("local-1") }
    }

    private fun givenSinglePendingRow() {
        every { sessionGuard.isCurrent("gen") } returns true
        coEvery { outboxDao.getByStatus(OutboxStatus.IN_FLIGHT.name) } returns emptyList()
        coEvery { outboxDao.getByStatus(OutboxStatus.PENDING.name) } returns listOf(sampleEntity())
        coEvery {
            outboxDao.claimForSend(
                localId = "local-1",
                pendingStatus = OutboxStatus.PENDING.name,
                inFlightStatus = OutboxStatus.IN_FLIGHT.name,
            )
        } returns 1
    }

    private fun sampleServerTransaction() = TransactionDto(
        id = 99,
        description = "Coffee",
        amount = 4.5,
        currency = "EUR",
        type = "CREDIT",
        dates = TransactionDatesDto(transaction = "2026-07-27"),
        source = AccountLinkDto(id = 1, name = "Checking"),
        destination = AccountLinkDto(id = 2, name = "Cafe"),
    )

    private fun serverPage(vararg items: TransactionDto) = TransactionPagedResponse(
        content = items.toList(),
        info = PageInfo(records = items.size.toLong(), pageSize = items.size, pages = 1),
    )

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
