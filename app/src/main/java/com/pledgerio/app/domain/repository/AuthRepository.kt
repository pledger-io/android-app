package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.common.Resource
import com.pledgerio.app.domain.model.LoginResult

interface AuthRepository {
    suspend fun login(username: String, password: String): Resource<LoginResult>
    suspend fun verifyTwoFactor(verificationCode: String): Resource<Unit>
    fun clearPendingMfa()
    fun hasPendingMfa(): Boolean
    suspend fun validateServer(baseUrl: String): Resource<Boolean>
    /** Validates URL, clears local data and auth if the host changed, then saves the new base URL. */
    suspend fun changeServerUrl(baseUrl: String): Resource<Boolean>
    fun isLoggedIn(): Boolean
    suspend fun logout()
}
