package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.common.Resource

interface AuthRepository {
    suspend fun login(username: String, password: String): Resource<String>
    suspend fun validateServer(baseUrl: String): Resource<Boolean>
    /** Validates URL, clears local data and auth if the host changed, then saves the new base URL. */
    suspend fun changeServerUrl(baseUrl: String): Resource<Boolean>
    fun isLoggedIn(): Boolean
    suspend fun logout()
}
