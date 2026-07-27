package com.pledgerio.app.util

import com.pledgerio.app.data.local.LocalDataCleaner
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDataBarrierConcurrencyTest {

    @Test
    fun `server transition waits for active write then clears and blocks every later step`() =
        runTest {
            val barrier = SessionDataBarrier()
            val sessionIsCurrent = AtomicBoolean(true)
            val stepStarted = CompletableDeferred<Unit>()
            val releaseStep = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val sessionManager = mockk<SessionManager>(relaxed = true)
            val localDataCleaner = mockk<LocalDataCleaner>(relaxed = true)
            val scheduler = mockk<SyncWorkScheduler>(relaxed = true)
            val accountRepository = mockk<AccountRepository>(relaxed = true)
            val currencyRepository = mockk<CurrencyRepository>()
            val sessionGuard = mockk<SyncSessionGuard>()
            every { sessionGuard.isCurrent("generation-a") } answers {
                sessionIsCurrent.get()
            }
            coEvery { currencyRepository.sync() } coAnswers {
                events += "step-start"
                stepStarted.complete(Unit)
                releaseStep.await()
                events += "step-write"
                true
            }
            every { sessionManager.clearAuthTokens() } answers {
                events += "invalidate"
                sessionIsCurrent.set(false)
            }
            coEvery { localDataCleaner.clearAllUserData() } coAnswers {
                events += "clean"
            }
            every {
                sessionManager.clearAuthTokensAndSaveBaseUrl("https://new.example")
            } answers {
                events += "activate-server"
            }
            val runner = runner(
                accountRepository = accountRepository,
                currencyRepository = currencyRepository,
                sessionGuard = sessionGuard,
                barrier = barrier,
            )
            val coordinator = coordinator(
                sessionManager = sessionManager,
                localDataCleaner = localDataCleaner,
                scheduler = scheduler,
                barrier = barrier,
            )
            var outcome: SyncRunOutcome? = null

            val worker = launch {
                outcome = runner.run("generation-a")
            }
            stepStarted.await()
            val transition = launch {
                coordinator.switchServer("https://new.example")
            }
            runCurrent()

            assertFalse(transition.isCompleted)
            assertEquals(listOf("step-start"), events)
            coVerify(exactly = 0) { localDataCleaner.clearAllUserData() }

            releaseStep.complete(Unit)
            transition.join()
            worker.join()

            assertEquals(
                listOf("step-start", "step-write", "invalidate", "clean", "activate-server"),
                events,
            )
            assertEquals(SyncRunOutcome.StaleSession, outcome)
            coVerify(exactly = 0) { accountRepository.refreshAccountTypes() }
        }

    @Test
    fun `terminal failure inside guarded call invalidates synchronously without deadlock`() =
        runTest {
            val barrier = SessionDataBarrier()
            val sessionIsCurrent = AtomicBoolean(true)
            val sessionManager = mockk<SessionManager>(relaxed = true)
            val localDataCleaner = mockk<LocalDataCleaner>(relaxed = true)
            val scheduler = mockk<SyncWorkScheduler>(relaxed = true)
            val currencyRepository = mockk<CurrencyRepository>()
            val scope = AuthenticatedSessionScope(
                accessToken = "failed-token",
                baseUrl = "https://example.com",
                syncGeneration = "generation-a",
            )
            every { sessionManager.clearAuthTokensIfCurrent(scope) } answers {
                sessionIsCurrent.set(false)
                true
            }
            every { sessionManager.isLoggedIn() } returns false
            every { sessionManager.getSyncGeneration() } returns null
            val sessionGuard = mockk<SyncSessionGuard>()
            every { sessionGuard.isCurrent("generation-a") } answers {
                sessionIsCurrent.get()
            }
            val coordinator = coordinator(
                sessionManager = sessionManager,
                localDataCleaner = localDataCleaner,
                scheduler = scheduler,
                barrier = barrier,
            )
            coEvery { currencyRepository.sync() } coAnswers {
                coordinator.terminateSessionAsync(scope)
                true
            }
            val runner = runner(
                accountRepository = mockk(relaxed = true),
                currencyRepository = currencyRepository,
                sessionGuard = sessionGuard,
                barrier = barrier,
            )

            val outcome = runner.run("generation-a")
            advanceUntilIdle()

            assertEquals(SyncRunOutcome.StaleSession, outcome)
            assertFalse(sessionIsCurrent.get())
            coVerify(exactly = 1) { scheduler.cancelAndAwait() }
            coVerify(exactly = 1) { localDataCleaner.clearAllUserData() }
        }

    private fun runner(
        accountRepository: AccountRepository,
        currencyRepository: CurrencyRepository,
        sessionGuard: SyncSessionGuard,
        barrier: SessionDataBarrier,
    ) = SyncWorkRunner(
        accountRepository = accountRepository,
        budgetRepository = mockk<BudgetRepository>(relaxed = true),
        categoryRepository = mockk<CategoryRepository>(relaxed = true),
        contractRepository = mockk<ContractRepository>(relaxed = true),
        currencyRepository = currencyRepository,
        tagRepository = mockk<TagRepository>(relaxed = true),
        outboxRepository = mockk(relaxed = true),
        sessionGuard = sessionGuard,
        sessionDataBarrier = barrier,
    )

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        sessionManager: SessionManager,
        localDataCleaner: LocalDataCleaner,
        scheduler: SyncWorkScheduler,
        barrier: SessionDataBarrier,
    ) = AuthenticatedSessionCoordinator(
        sessionManager = sessionManager,
        localDataCleaner = localDataCleaner,
        syncWorkScheduler = scheduler,
        sessionDataBarrier = barrier,
        appLog = mockk(relaxed = true),
        applicationScope = this,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )
}
