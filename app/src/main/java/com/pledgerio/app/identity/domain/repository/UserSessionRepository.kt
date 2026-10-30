package com.pledgerio.app.identity.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.identity.domain.model.ApiSession
import com.pledgerio.app.identity.domain.model.UserProfile
import java.time.LocalDate

interface UserSessionRepository {
    suspend fun listSessions(): Resource<List<ApiSession>>
    suspend fun createSession(description: String, expires: LocalDate): Resource<ApiSession>
    suspend fun revokeSession(id: Long): Resource<Unit>
    suspend fun getProfile(): Resource<UserProfile>
    suspend fun get2FactorQr(): Resource<ByteArray>
    suspend fun enableMfa(verificationCode: String): Resource<Unit>
    suspend fun disableMfa(): Resource<Unit>
}
