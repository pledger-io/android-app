package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.TransactionEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.AccountLinkDto
import com.pledgerio.app.data.remote.dto.TransactionClassificationSuggestionDto
import com.pledgerio.app.data.remote.dto.TransactionDatesDto
import com.pledgerio.app.data.remote.dto.TransactionDto
import com.pledgerio.app.data.remote.dto.TransactionPagedResponse
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
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
    private val outboxRepository = mockk<com.pledgerio.app.domain.repository.TransactionOutboxRepository>()
    private val networkMonitor = mockk<com.pledgerio.app.util.NetworkMonitor>()
    private val outboxFlushScheduler = mockk<com.pledgerio.app.util.OutboxFlushScheduler>(relaxed = true)
    private val sessionManager = mockk<com.pledgerio.app.util.SessionManager>(relaxed = true)
    private val repository = TransactionRepositoryImpl(
        apiService,
        transactionDao,
        mutationInvalidator,
        outboxRepository,
        networkMonitor,
        outboxFlushScheduler,
        sessionManager,
    )

    @Test
    fun `getTransactionsPage converts inclusive end date to exclusive API end`() = runTest {
        coEvery {
            apiService.getTransactions(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns Response.success(TransactionPagedResponse())

        val result = repository.getTransactionsPage(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 31),
        )

        assertTrue(result is Resource.Success)
        coVerify {
            apiService.getTransactions(
                startDate = "2026-07-01",
                endDate = "2026-08-01",
                accounts = null,
                type = null,
                description = null,
                currency = null,
                expenses = null,
                categories = null,
                contracts = null,
                offset = 0,
                numberOfResults = 25,
            )
        }
    }

    @Test
    fun `getTransactionsPage upserts month pages without globally wiping cache`() = runTest {
        coEvery {
            apiService.getTransactions(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returnsMany listOf(
            Response.success(transactionPage(transactionDto(1, LocalDate.of(2026, 6, 15)))),
            Response.success(transactionPage(transactionDto(2, LocalDate.of(2026, 7, 15)))),
        )

        repository.getTransactionsPage(
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 30),
        )
        repository.getTransactionsPage(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 31),
        )

        coVerify(exactly = 0) { transactionDao.deleteAll() }
        coVerify(exactly = 1) {
            transactionDao.insertAll(match { entities -> entities.single().id == 1L })
        }
        coVerify(exactly = 1) {
            transactionDao.insertAll(match { entities -> entities.single().id == 2L })
        }
    }

    @Test
    fun `getTransactionsPage offline fallback filters source and destination account`() = runTest {
        coEvery {
            apiService.getTransactions(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } throws java.io.IOException("offline")
        coEvery { transactionDao.getAllOnce() } returns listOf(
            cachedTransaction(
                id = 1,
                date = LocalDate.of(2026, 7, 10),
                sourceAccountId = 42,
            ),
            cachedTransaction(
                id = 2,
                date = LocalDate.of(2026, 7, 11),
                destinationAccountId = 42,
            ),
            cachedTransaction(
                id = 3,
                date = LocalDate.of(2026, 7, 12),
                sourceAccountId = 7,
                destinationAccountId = 8,
            ),
        )

        val result = repository.getTransactionsPage(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 31),
            accountId = 42,
        )

        assertTrue(result is Resource.Success)
        assertEquals(listOf(1L, 2L), (result as Resource.Success).data.items.map { it.id })
    }

    @Test
    fun `getTransactionsPage null successful body returns error without mutating cache`() = runTest {
        coEvery {
            apiService.getTransactions(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns Response.success<TransactionPagedResponse>(null)

        val result = repository.getTransactionsPage(
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 31),
        )

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
        coVerify(exactly = 0) { transactionDao.deleteAll() }
    }

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

    @Test
    fun `createTransactionOrEnqueue queues when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val pending = com.pledgerio.app.domain.model.PendingTransactionCreate(
            localId = "local-1",
            createdAtMillis = 1L,
            status = com.pledgerio.app.domain.model.OutboxStatus.PENDING,
            lastError = null,
            attemptCount = 0,
            date = LocalDate.of(2026, 7, 27),
            currency = "EUR",
            description = "Coffee",
            amount = 4.5,
            sourceAccountId = 1,
            destinationAccountId = 2,
        )
        coEvery { outboxRepository.enqueueCreate(any()) } returns Resource.Success(pending)

        val result = repository.createTransactionOrEnqueue(createSample())

        assertTrue(result is Resource.Success)
        assertTrue((result as Resource.Success).data is com.pledgerio.app.domain.model.CreateOutcome.Queued)
        coVerify(exactly = 0) { apiService.createTransaction(any()) }
    }

    @Test
    fun `createTransactionOrEnqueue syncs when online success`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.createTransaction(any()) } returns Response.success(
            com.pledgerio.app.data.remote.dto.TransactionDto(
                id = 10,
                description = "Coffee",
                amount = 4.5,
                currency = "EUR",
                type = "CREDIT",
            ),
        )

        val result = repository.createTransactionOrEnqueue(createSample())

        assertTrue(result is Resource.Success)
        assertTrue((result as Resource.Success).data is com.pledgerio.app.domain.model.CreateOutcome.Synced)
        coVerify(exactly = 0) { outboxRepository.enqueueCreate(any()) }
    }

    @Test
    fun `createTransactionOrEnqueue HTTP 400 returns error without enqueue`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.createTransaction(any()) } returns Response.error(
            400,
            "".toResponseBody(null),
        )

        val result = repository.createTransactionOrEnqueue(createSample())

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { outboxRepository.enqueueCreate(any()) }
    }

    @Test
    fun `createTransactionOrEnqueue IOException enqueues`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.createTransaction(any()) } throws java.io.IOException("timeout")
        every { sessionManager.getSyncGeneration() } returns "gen"
        val pending = com.pledgerio.app.domain.model.PendingTransactionCreate(
            localId = "local-2",
            createdAtMillis = 2L,
            status = com.pledgerio.app.domain.model.OutboxStatus.PENDING,
            lastError = null,
            attemptCount = 0,
            date = LocalDate.of(2026, 7, 27),
            currency = "EUR",
            description = "Coffee",
            amount = 4.5,
            sourceAccountId = 1,
            destinationAccountId = 2,
        )
        coEvery { outboxRepository.enqueueCreate(any()) } returns Resource.Success(pending)

        val result = repository.createTransactionOrEnqueue(createSample())

        assertTrue(result is Resource.Success)
        assertTrue((result as Resource.Success).data is com.pledgerio.app.domain.model.CreateOutcome.Queued)
        coVerify { outboxFlushScheduler.schedule("gen") }
    }

    private fun createSample() = com.pledgerio.app.domain.model.Transaction(
        id = 0,
        description = "Coffee",
        amount = 4.5,
        currency = "EUR",
        type = TransactionType.CREDIT,
        date = LocalDate.of(2026, 7, 27),
        sourceAccountId = 1,
        destinationAccountId = 2,
    )

    private fun transactionPage(transaction: TransactionDto) = TransactionPagedResponse(
        content = listOf(transaction),
    )

    private fun transactionDto(id: Long, date: LocalDate) = TransactionDto(
        id = id,
        dates = TransactionDatesDto(transaction = date.toString()),
        source = AccountLinkDto(id = 1),
    )

    private fun cachedTransaction(
        id: Long,
        date: LocalDate,
        sourceAccountId: Long? = null,
        destinationAccountId: Long? = null,
    ) = TransactionEntity(
        id = id,
        description = "Coffee",
        amount = 4.5,
        type = TransactionType.CREDIT.name,
        date = date,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
    )
}
