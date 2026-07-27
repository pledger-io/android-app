package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.Patch2FactorRequest
import com.pledgerio.app.data.remote.dto.SessionRequest
import com.pledgerio.app.data.remote.dto.SessionResponse
import com.pledgerio.app.data.remote.dto.UserProfileResponse
import com.pledgerio.app.domain.model.ApiSession
import com.pledgerio.app.domain.model.UserProfile
import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.formatApi
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject

class UserSessionRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val sessionManager: SessionManager,
) : UserSessionRepository {

    override suspend fun listSessions(): Resource<List<ApiSession>> {
        val username = requireUsername() ?: return missingUsername()
        return try {
            val response = apiService.listSessions(username)
            if (response.isSuccessful) {
                val sessions = response.body()
                    .orEmpty()
                    .mapNotNull { it.toDomainOrNull() }
                Resource.Success(sessions)
            } else {
                httpError(response.code(), "List sessions")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun createSession(
        description: String,
        expires: LocalDate,
    ): Resource<ApiSession> {
        val username = requireUsername() ?: return missingUsername()
        val trimmed = description.trim()
        if (trimmed.length < ApiSession.MIN_DESCRIPTION_LENGTH) {
            return Resource.Error(
                "Description must be at least ${ApiSession.MIN_DESCRIPTION_LENGTH} characters",
            )
        }
        return try {
            val response = apiService.createSession(
                user = username,
                request = SessionRequest(
                    description = trimmed,
                    expires = expires.formatApi(),
                ),
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Resource.Error("No response body")
                val session = body.toDomainOrNull()
                    ?: return Resource.Error("Invalid session response")
                Resource.Success(session)
            } else {
                httpError(response.code(), "Create session")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun revokeSession(id: Long): Resource<Unit> {
        val username = requireUsername() ?: return missingUsername()
        return try {
            val response = apiService.revokeSession(username, id)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                httpError(response.code(), "Revoke session")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getProfile(): Resource<UserProfile> {
        val username = requireUsername() ?: return missingUsername()
        return try {
            val response = apiService.getProfile(username)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Resource.Error("No response body")
                Resource.Success(body.toDomain())
            } else {
                httpError(response.code(), "Load profile")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun get2FactorQr(): Resource<ByteArray> {
        val username = requireUsername() ?: return missingUsername()
        return try {
            val response = apiService.get2FactorQr(username)
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                    ?: return Resource.Error("No QR image returned")
                if (bytes.isEmpty()) {
                    return Resource.Error("No QR image returned")
                }
                Resource.Success(bytes)
            } else {
                httpError(response.code(), "Load QR code")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun enableMfa(verificationCode: String): Resource<Unit> {
        val username = requireUsername() ?: return missingUsername()
        val code = verificationCode.trim()
        if (code.length !in 4..8 || !code.all { it.isDigit() }) {
            return Resource.Error("Enter the 6-digit code from your authenticator app")
        }
        return try {
            val response = apiService.patch2Factor(
                user = username,
                request = Patch2FactorRequest.enable(code),
            )
            if (response.isSuccessful || response.code() == 204) {
                Resource.Success(Unit)
            } else if (response.code() == 400 || response.code() == 403) {
                Resource.Error("Invalid verification code")
            } else {
                httpError(response.code(), "Enable two-factor")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun disableMfa(): Resource<Unit> {
        val username = requireUsername() ?: return missingUsername()
        return try {
            val response = apiService.patch2Factor(
                user = username,
                request = Patch2FactorRequest.disable(),
            )
            if (response.isSuccessful || response.code() == 204) {
                Resource.Success(Unit)
            } else {
                httpError(response.code(), "Disable two-factor")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    private fun requireUsername(): String? = sessionManager.getUsername()?.takeIf { it.isNotBlank() }

    private fun missingUsername(): Resource.Error =
        Resource.Error("Not signed in")

    private fun httpError(code: Int, action: String): Resource.Error {
        val message = when (code) {
            401 -> "Unauthorized — please sign in again"
            404 -> "Not found"
            else -> "$action failed: HTTP $code"
        }
        return Resource.Error(message)
    }

    private fun SessionResponse.toDomainOrNull(): ApiSession? {
        val sessionId = id ?: return null
        return ApiSession(
            id = sessionId,
            description = description,
            token = token.takeIf { it.isNotBlank() },
            validFrom = valid?.startDate?.let { parseDate(it) },
            validUntil = valid?.endDate?.let { parseDate(it) },
        )
    }

    private fun UserProfileResponse.toDomain(): UserProfile = UserProfile(mfa = mfa)

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

}
