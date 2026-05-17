package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.LocalDataCleaner
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.LoginRequest
import com.pledgerio.app.domain.repository.AuthRepository
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
    private val localDataCleaner: LocalDataCleaner,
    private val okHttpClient: OkHttpClient,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Resource<String> {
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
                localDataCleaner.clearAllUserData()
                sessionManager.saveToken(token)
                sessionManager.saveUsername(username)
                body.refreshToken?.let { sessionManager.saveRefreshToken(it) }
                if (body.expiresIn > 0) {
                    sessionManager.saveTokenExpiry(body.expiresIn)
                }
                Resource.Success(token)
            } else if (response.code() == 401) {
                Resource.Error("Invalid username or password")
            } else {
                Resource.Error("Login failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun validateServer(baseUrl: String): Resource<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = baseUrl.trimEnd('/')
                URL(normalizedUrl)

                val url = "$normalizedUrl/health"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        sessionManager.saveBaseUrl(normalizedUrl)
                        Resource.Success(true)
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
    }

    override fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun logout() {
        try {
            if (sessionManager.isLoggedIn()) {
                apiService.logout()
            }
        } catch (_: Exception) {
            // Clear local session even when the server is unreachable
        }
        localDataCleaner.clearAllUserData()
        sessionManager.clearAuthTokens()
    }
}
