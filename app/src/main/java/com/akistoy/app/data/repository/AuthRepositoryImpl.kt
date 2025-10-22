package com.akistoy.app.data.repository

import com.akistoy.app.BuildConfig
import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.data.remote.dto.LoginRequest
import com.akistoy.app.domain.model.User
import com.akistoy.app.domain.repo.AuthRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AkistoyApi,
    private val preferences: UserPreferencesDataSource
) : AuthRepository {

    override suspend fun login(email: String, deviceId: String): Result<User> {
        return runCatching {
            val actualDeviceId = deviceId.ifBlank { UUID.randomUUID().toString() }
            val response = if (BuildConfig.FAKE_LOGIN) {
                User(
                    id = UUID.randomUUID().toString(),
                    email = email,
                    token = UUID.randomUUID().toString(),
                    deviceId = actualDeviceId
                )
            } else {
                val loginResponse = api.login(LoginRequest(email = email, deviceId = actualDeviceId))
                User(
                    id = loginResponse.userId,
                    email = loginResponse.email,
                    token = loginResponse.token,
                    deviceId = actualDeviceId
                )
            }
            preferences.setUser(response)
            response
        }
    }

    override suspend fun getLoggedInUser(): User? = preferences.userFlow.firstOrNull()

    override suspend fun logout() {
        preferences.clearUser()
    }
}
