package com.pledgerio.app.domain.repository

import com.pledgerio.app.util.Resource

interface AuthRepository {
    suspend fun login(username: String, password: String): Resource<String>
    suspend fun validateServer(baseUrl: String): Resource<Boolean>
    fun isLoggedIn(): Boolean
    suspend fun logout()
}
