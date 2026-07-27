package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.data.remote.dto.SessionRequest
import com.pledgerio.app.data.remote.dto.SessionResponse
import com.pledgerio.app.data.remote.dto.UserProfileResponse
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate

class UserSessionRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val sessionManager = mockk<SessionManager>()
    private lateinit var repository: UserSessionRepositoryImpl

    @Before
    fun setUp() {
        every { sessionManager.getUsername() } returns "alice@example.com"
        repository = UserSessionRepositoryImpl(apiService, sessionManager)
    }

    @Test
    fun `listSessions maps DateRangeDto and succeeds`() = runTest {
        coEvery { apiService.listSessions("alice@example.com") } returns Response.success(
            listOf(
                SessionResponse(
                    id = 11L,
                    description = "CI bot token",
                    token = "super-secret-token-value",
                    valid = DateRangeDto(startDate = "2026-01-01", endDate = "2027-01-01"),
                ),
            ),
        )

        val result = repository.listSessions()

        assertTrue(result is Resource.Success)
        val session = (result as Resource.Success).data.single()
        assertEquals(11L, session.id)
        assertEquals("CI bot token", session.description)
        assertEquals("super-secret-token-value", session.token)
        assertEquals(LocalDate.of(2026, 1, 1), session.validFrom)
        assertEquals(LocalDate.of(2027, 1, 1), session.validUntil)
        assertEquals("••••alue", session.maskedToken())
    }

    @Test
    fun `listSessions maps 401`() = runTest {
        coEvery { apiService.listSessions(any()) } returns Response.error(
            401,
            "".toResponseBody(null),
        )

        val result = repository.listSessions()

        assertTrue(result is Resource.Error)
        assertTrue((result as Resource.Error).message.contains("Unauthorized"))
    }

    @Test
    fun `listSessions fails when username missing`() = runTest {
        every { sessionManager.getUsername() } returns null

        val result = repository.listSessions()

        assertTrue(result is Resource.Error)
        assertEquals("Not signed in", (result as Resource.Error).message)
        coVerify(exactly = 0) { apiService.listSessions(any()) }
    }

    @Test
    fun `createSession posts description and expires date`() = runTest {
        coEvery {
            apiService.createSession(
                user = "alice@example.com",
                request = SessionRequest(
                    description = "Deploy script",
                    expires = "2027-07-27",
                ),
            )
        } returns Response.success(
            SessionResponse(
                id = 42L,
                description = "Deploy script",
                token = "once-only-token",
                valid = DateRangeDto(startDate = "2026-07-27", endDate = "2027-07-27"),
            ),
        )

        val result = repository.createSession("Deploy script", LocalDate.of(2027, 7, 27))

        assertTrue(result is Resource.Success)
        assertEquals("once-only-token", (result as Resource.Success).data.token)
        assertEquals(42L, result.data.id)
    }

    @Test
    fun `createSession rejects short description without calling API`() = runTest {
        val result = repository.createSession("short", LocalDate.now().plusYears(1))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { apiService.createSession(any(), any()) }
    }

    @Test
    fun `revokeSession maps 404`() = runTest {
        coEvery {
            apiService.revokeSession("alice@example.com", 99L)
        } returns Response.error(404, "".toResponseBody(null))

        val result = repository.revokeSession(99L)

        assertTrue(result is Resource.Error)
        assertEquals("Not found", (result as Resource.Error).message)
    }

    @Test
    fun `revokeSession succeeds`() = runTest {
        coEvery {
            apiService.revokeSession("alice@example.com", 5L)
        } returns Response.success(Unit)

        val result = repository.revokeSession(5L)

        assertTrue(result is Resource.Success)
    }

    @Test
    fun `getProfile maps mfa flag`() = runTest {
        coEvery { apiService.getProfile("alice@example.com") } returns Response.success(
            UserProfileResponse(mfa = true),
        )

        val result = repository.getProfile()

        assertTrue(result is Resource.Success)
        assertEquals(true, (result as Resource.Success).data.mfa)
    }

    @Test
    fun `getProfile maps 401`() = runTest {
        coEvery { apiService.getProfile(any()) } returns Response.error(
            401,
            "".toResponseBody(null),
        )

        val result = repository.getProfile()

        assertTrue(result is Resource.Error)
        assertTrue((result as Resource.Error).message.contains("Unauthorized"))
    }

    @Test
    fun `listSessions drops responses without id`() = runTest {
        coEvery { apiService.listSessions(any()) } returns Response.success(
            listOf(SessionResponse(id = null, description = "broken")),
        )

        val result = repository.listSessions()

        assertTrue(result is Resource.Success)
        assertTrue((result as Resource.Success).data.isEmpty())
        assertNull(result.data.firstOrNull())
    }
}
