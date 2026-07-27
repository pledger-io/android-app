package com.pledgerio.app.ui.settings

import com.pledgerio.app.domain.model.ApiSession
import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ApiSessionsViewModelTest {

    private val repository = mockk<UserSessionRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.listSessions() } returns Resource.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createSession rejects short description without calling repository`() = runTest {
        val viewModel = ApiSessionsViewModel(repository)
        advanceUntilIdle()
        viewModel.openCreateSheet()
        viewModel.onDescriptionChanged("short")

        viewModel.createSession()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.createForm?.descriptionError == true)
        coVerify(exactly = 0) { repository.createSession(any(), any()) }
    }

    @Test
    fun `createSession shows one-time token and refreshes list`() = runTest {
        val created = ApiSession(
            id = 7L,
            description = "Deploy script",
            token = "secret-once",
            validFrom = LocalDate.of(2026, 1, 1),
            validUntil = LocalDate.of(2027, 1, 1),
        )
        val listed = created.copy(token = "secret-once")
        coEvery {
            repository.createSession("Deploy script", any())
        } returns Resource.Success(created)
        coEvery { repository.listSessions() } returnsMany listOf(
            Resource.Success(emptyList()),
            Resource.Success(listOf(listed)),
        )

        val viewModel = ApiSessionsViewModel(repository)
        advanceUntilIdle()
        viewModel.openCreateSheet()
        viewModel.onDescriptionChanged("Deploy script")
        viewModel.createSession()
        advanceUntilIdle()

        assertEquals("secret-once", viewModel.uiState.value.createdToken)
        assertNull(viewModel.uiState.value.createForm)
        assertEquals(1, viewModel.uiState.value.sessions.size)
        coVerify(exactly = 1) { repository.createSession("Deploy script", any()) }
    }

    @Test
    fun `confirmRevoke refreshes list on success`() = runTest {
        val session = ApiSession(
            id = 3L,
            description = "Old token xx",
            token = null,
            validFrom = null,
            validUntil = LocalDate.of(2027, 1, 1),
        )
        coEvery { repository.listSessions() } returnsMany listOf(
            Resource.Success(listOf(session)),
            Resource.Success(emptyList()),
        )
        coEvery { repository.revokeSession(3L) } returns Resource.Success(Unit)

        val viewModel = ApiSessionsViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sessions.size)

        viewModel.requestRevoke(session)
        viewModel.confirmRevoke()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingRevoke)
        assertTrue(viewModel.uiState.value.sessions.isEmpty())
        coVerify(exactly = 1) { repository.revokeSession(3L) }
    }

    @Test
    fun `confirmRevoke exposes snackbar on failure`() = runTest {
        val session = ApiSession(
            id = 9L,
            description = "Keep me pls",
            token = null,
            validFrom = null,
            validUntil = null,
        )
        coEvery { repository.listSessions() } returns Resource.Success(listOf(session))
        coEvery { repository.revokeSession(9L) } returns Resource.Error("Not found")

        val viewModel = ApiSessionsViewModel(repository)
        advanceUntilIdle()
        viewModel.requestRevoke(session)
        viewModel.confirmRevoke()
        advanceUntilIdle()

        assertEquals("Not found", viewModel.uiState.value.snackbarMessage)
        assertNull(viewModel.uiState.value.pendingRevoke)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.sessions.singleOrNull())
    }
}
