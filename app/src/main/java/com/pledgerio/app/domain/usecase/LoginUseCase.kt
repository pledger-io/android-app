package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.LoginResult
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.Resource
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
