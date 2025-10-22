package com.akistoy.app.data.repository

import android.content.Context
import android.os.Build
import com.akistoy.app.BuildConfig
import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.data.remote.dto.LoginRequest
import com.akistoy.app.domain.model.User
import com.akistoy.app.domain.repo.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AkistoyApi,
    private val preferences: UserPreferencesDataSource,
    @ApplicationContext private val context: Context
) : AuthRepository {

    override suspend fun login(name: String, deviceId: String): Result<User> {
        return runCatching {
            val actualDeviceId = deviceId.ifBlank { UUID.randomUUID().toString() }
            // Si no se proporciona nombre, usar el nombre del dispositivo
            val actualName = name.ifBlank { getDeviceName() }

            val response = if (BuildConfig.FAKE_LOGIN) {
                User(
                    id = UUID.randomUUID().toString(),
                    name = actualName,
                    token = UUID.randomUUID().toString(),
                    deviceId = actualDeviceId
                )
            } else {
                val loginResponse = api.login(LoginRequest(name = actualName, deviceId = actualDeviceId))
                User(
                    id = loginResponse.userId,
                    name = loginResponse.name,
                    token = loginResponse.token,
                    deviceId = actualDeviceId
                )
            }
            preferences.setUser(response)
            response
        }
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
    }

    override suspend fun getLoggedInUser(): User? = preferences.userFlow.firstOrNull()

    override suspend fun logout() {
        preferences.clearUser()
    }
}
