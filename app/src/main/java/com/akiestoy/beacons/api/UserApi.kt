package com.akiestoy.beacons.api

import com.akiestoy.beacons.model.user.UserResponse
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * API para registro y gestión de usuarios
 */
interface UserApi {
    @POST("create/user")
    suspend fun registerUser(
        @Header("rut") rut: String
    ): UserResponse
}
