package com.akistoy.app.domain.usecase

import com.akistoy.app.domain.model.User
import com.akistoy.app.domain.repo.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(name: String, deviceId: String): Result<User> =
        authRepository.login(name, deviceId)
}
