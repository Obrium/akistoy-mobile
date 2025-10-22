package com.akistoy.app.domain.repo

import com.akistoy.app.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, deviceId: String): Result<User>
    suspend fun getLoggedInUser(): User?
    suspend fun logout()
}
