package com.pledgerio.app.identity.domain.usecase

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.identity.domain.model.LoginResult
import com.pledgerio.app.identity.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(username: String, password: String): Resource<LoginResult> {
        if (username.isBlank()) return Resource.Error("Username cannot be empty")
        if (password.isBlank()) return Resource.Error("Password cannot be empty")
        return authRepository.login(username, password)
    }
}
