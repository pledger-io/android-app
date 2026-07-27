package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.LoginRequest
import com.pledgerio.app.data.remote.dto.Verify2FactorRequest
import com.pledgerio.app.domain.model.LoginResult
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.JwtPayload
import com.pledgerio.app.util.PendingMfaSession
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val sessionManager: SessionManager,
    private val authenticatedSessionCoordinator: AuthenticatedSessionCoordinator,
    private val pendingMfaSession: PendingMfaSession,
    private val okHttpClient: OkHttpClient,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Resource<LoginResult> {
        return try {
            val loginRequest = LoginRequest(username = username, password = password)
            val response = apiService.authenticate(loginRequest)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Resource.Error("No response body")
                val token = body.accessToken
                if (token.isBlank()) {
                    return Resource.Error("No access token in response")
                }
                if (JwtPayload.requiresMfaVerification(token)) {
                    pendingMfaSession.set(
                        accessToken = token,
                        refreshToken = body.refreshToken,
                        expiresInSeconds = body.expiresIn,
                        username = username,
                    )
                    Resource.Success(LoginResult.MfaRequired)
                } else {
                    pendingMfaSession.clear()
                    authenticatedSessionCoordinator.activateSession(
                        accessToken = token,
                        username = username,
                        refreshToken = body.refreshToken,
                        expiresInSeconds = body.expiresIn,
                    )
                    Resource.Success(LoginResult.FullyAuthenticated)
                }
            } else if (response.code() == 401) {
                pendingMfaSession.clear()
                Resource.Error("Invalid username or password")
            } else {
                pendingMfaSession.clear()
                Resource.Error("Login failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            pendingMfaSession.clear()
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun verifyTwoFactor(verificationCode: String): Resource<Unit> {
        val pending = pendingMfaSession.get()
            ?: return Resource.Error("No pending two-factor verification")
        val code = verificationCode.trim()
        if (code.length !in 4..8 || !code.all { it.isDigit() }) {
            return Resource.Error("Enter the 6-digit code from your authenticator app")
        }
        return try {
            val response = apiService.verify2Factor(
                authorization = pending.authorizationHeader(),
                request = Verify2FactorRequest(verificationCode = code),
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Resource.Error("No response body")
                val token = body.accessToken
                if (token.isBlank()) {
                    return Resource.Error("No access token in response")
                }
                if (JwtPayload.requiresMfaVerification(token)) {
                    return Resource.Error("Verification did not complete — try again")
                }
                authenticatedSessionCoordinator.activateSession(
                    accessToken = token,
                    username = pending.username,
                    refreshToken = body.refreshToken ?: pending.refreshToken,
                    expiresInSeconds = body.expiresIn.takeIf { it > 0 }
                        ?: pending.expiresInSeconds,
                )
                pendingMfaSession.clear()
                Resource.Success(Unit)
            } else if (response.code() == 403 || response.code() == 400) {
                Resource.Error("Invalid verification code")
            } else if (response.code() == 401) {
                pendingMfaSession.clear()
                Resource.Error("Session expired — sign in again")
            } else {
                Resource.Error("Verification failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override fun clearPendingMfa() {
        pendingMfaSession.clear()
    }

    override fun hasPendingMfa(): Boolean = pendingMfaSession.isPending()

    override suspend fun validateServer(baseUrl: String): Resource<Boolean> {
        return withContext(Dispatchers.IO) {
            when (val result = pingServer(baseUrl)) {
                is Resource.Success -> {
                    sessionManager.saveBaseUrl(result.data)
                    Resource.Success(true)
                }
                is Resource.Error -> result
                is Resource.Loading -> Resource.Loading
            }
        }
    }

    override suspend fun changeServerUrl(baseUrl: String): Resource<Boolean> {
        return withContext(Dispatchers.IO) {
            when (val result = pingServer(baseUrl)) {
                is Resource.Success -> {
                    val normalizedUrl = result.data
                    val previous = sessionManager.getBaseUrl()?.trimEnd('/')
                    if (previous != null && previous != normalizedUrl) {
                        authenticatedSessionCoordinator.switchServer(normalizedUrl)
                    } else {
                        sessionManager.saveBaseUrl(normalizedUrl)
                    }
                    pendingMfaSession.clear()
                    Resource.Success(true)
                }
                is Resource.Error -> result
                is Resource.Loading -> Resource.Loading
            }
        }
    }

    /** @return Success with normalized base URL (no trailing slash) */
    private fun pingServer(baseUrl: String): Resource<String> {
        return try {
            val normalizedUrl = baseUrl.trim().trimEnd('/')
            URL(normalizedUrl)

            val url = "$normalizedUrl/health"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Resource.Success(normalizedUrl)
                } else {
                    Resource.Error("Server returned HTTP ${resp.code} — is this a Pledger.io instance?")
                }
            }
        } catch (e: java.net.MalformedURLException) {
            Resource.Error("Invalid URL format. Example: https://my-pledger.example.com")
        } catch (e: java.net.ConnectException) {
            Resource.Error("Could not connect. If using an emulator, try 10.0.2.2 instead of localhost.")
        } catch (e: java.net.UnknownHostException) {
            Resource.Error("Server not found. Check the URL and your network connection.")
        } catch (e: java.io.IOException) {
            Resource.Error("Connection failed: ${e.message}")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not connect to server")
        }
    }

    override fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun logout() {
        pendingMfaSession.clear()
        authenticatedSessionCoordinator.logout { credential ->
            credential?.let {
                apiService.logout(it.authorizationHeader())
            }
        }
    }
}
